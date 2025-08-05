(ns training-personal-data.resilience
  "Resilience patterns: retry with backoff, circuit breaker, and rate limiting"
  (:require [taoensso.timbre :as log]
            [clojure.string :as str])
  (:import [java.util.concurrent ConcurrentHashMap]
           [java.util.concurrent.atomic AtomicInteger AtomicLong]))

;; Circuit Breaker States
(def ^:private circuit-states #{:closed :open :half-open})

;; Circuit Breaker Configuration
(def ^:private default-circuit-config
  {:failure-threshold 5        ; Number of failures before opening
   :success-threshold 3        ; Number of successes to close from half-open
   :timeout-ms 60000          ; Time to wait before trying half-open
   :enabled true})

;; Retry Configuration
(def ^:private default-retry-config
  {:max-attempts 3
   :initial-delay-ms 1000
   :max-delay-ms 30000
   :backoff-multiplier 2.0
   :jitter true
   :enabled true})

;; Rate Limiting Configuration
(def ^:private default-rate-limit-config
  {:requests-per-minute 60
   :burst-size 10
   :enabled true})

;; Storage
(def ^:private circuit-breakers (ConcurrentHashMap.))
(def ^:private rate-limiters (ConcurrentHashMap.))
(def ^:private resilience-stats (atom {}))

;; Circuit Breaker Implementation
(defrecord CircuitBreaker [name state failure-count success-count last-failure-time config])

(defn- current-time-ms []
  (System/currentTimeMillis))

(defn- create-circuit-breaker [name config]
  (->CircuitBreaker name
                    (AtomicInteger. 0) ; :closed = 0, :open = 1, :half-open = 2
                    (AtomicInteger. 0)
                    (AtomicInteger. 0)
                    (AtomicLong. 0)
                    config))

(defn- state-from-int [state-int]
  (case state-int
    0 :closed
    1 :open
    2 :half-open))

(defn- int-from-state [state]
  (case state
    :closed 0
    :open 1
    :half-open 2))

(defn- should-attempt? [circuit-breaker]
  (let [current-state (state-from-int (.get (:state circuit-breaker)))
        config (:config circuit-breaker)
        current-time (current-time-ms)
        last-failure (.get (:last-failure-time circuit-breaker))]
    
    (case current-state
      :closed true
      :half-open true
      :open (> (- current-time last-failure) (:timeout-ms config)))))

(defn- record-success! [circuit-breaker]
  (let [current-state (state-from-int (.get (:state circuit-breaker)))
        success-count (.incrementAndGet (:success-count circuit-breaker))
        config (:config circuit-breaker)]
    
    (case current-state
      :closed (.set (:failure-count circuit-breaker) 0)
      :half-open (when (>= success-count (:success-threshold config))
                   (.set (:state circuit-breaker) (int-from-state :closed))
                   (.set (:failure-count circuit-breaker) 0)
                   (.set (:success-count circuit-breaker) 0)
                   (log/info {:event :circuit-breaker-closed :name (:name circuit-breaker)}))
      :open nil)))

(defn- record-failure! [circuit-breaker]
  (let [failure-count (.incrementAndGet (:failure-count circuit-breaker))
        config (:config circuit-breaker)
        current-state (state-from-int (.get (:state circuit-breaker)))]
    
    (.set (:last-failure-time circuit-breaker) (current-time-ms))
    
    (when (and (= current-state :closed)
               (>= failure-count (:failure-threshold config)))
      (.set (:state circuit-breaker) (int-from-state :open))
      (.set (:success-count circuit-breaker) 0)
      (log/warn {:event :circuit-breaker-opened 
                 :name (:name circuit-breaker)
                 :failure-count failure-count}))
    
    (when (= current-state :half-open)
      (.set (:state circuit-breaker) (int-from-state :open))
      (.set (:success-count circuit-breaker) 0)
      (log/warn {:event :circuit-breaker-reopened :name (:name circuit-breaker)}))))

(defn get-or-create-circuit-breaker
  "Gets or creates a circuit breaker with the given name"
  [name & {:keys [config] :or {config default-circuit-config}}]
  (.computeIfAbsent circuit-breakers name
                    (fn [_] (create-circuit-breaker name config))))

(defn get-circuit-breaker-state
  "Returns the current state of a circuit breaker"
  [name]
  (when-let [cb (.get circuit-breakers name)]
    (let [current-state (state-from-int (.get (:state cb)))]
      {:name name
       :state current-state
       :failure-count (.get (:failure-count cb))
       :success-count (.get (:success-count cb))
       :last-failure-time (.get (:last-failure-time cb))})))

;; Rate Limiter Implementation
(defrecord RateLimiter [name tokens last-refill-time config])

(defn- create-rate-limiter [name config]
  (->RateLimiter name
                 (AtomicInteger. (:burst-size config))
                 (AtomicLong. (current-time-ms))
                 config))

(defn- refill-tokens! [rate-limiter]
  (let [current-time (current-time-ms)
        last-refill (.get (:last-refill-time rate-limiter))
        time-passed (- current-time last-refill)
        config (:config rate-limiter)
        tokens-to-add (int (/ (* time-passed (:requests-per-minute config)) 60000))
        current-tokens (.get (:tokens rate-limiter))]
    
    (when (> tokens-to-add 0)
      (let [new-tokens (min (:burst-size config) (+ current-tokens tokens-to-add))]
        (.set (:tokens rate-limiter) new-tokens)
        (.set (:last-refill-time rate-limiter) current-time)))))

(defn- try-acquire-token! [rate-limiter]
  (refill-tokens! rate-limiter)
  (let [current-tokens (.get (:tokens rate-limiter))]
    (if (> current-tokens 0)
      (do
        (.decrementAndGet (:tokens rate-limiter))
        true)
      false)))

(defn get-or-create-rate-limiter
  "Gets or creates a rate limiter with the given name"
  [name & {:keys [config] :or {config default-rate-limit-config}}]
  (.computeIfAbsent rate-limiters name
                    (fn [_] (create-rate-limiter name config))))

;; Retry Implementation
(defn- calculate-delay [attempt config]
  (let [base-delay (:initial-delay-ms config)
        multiplier (:backoff-multiplier config)
        max-delay (:max-delay-ms config)
        exponential-delay (* base-delay (Math/pow multiplier (dec attempt)))
        capped-delay (min exponential-delay max-delay)
        jitter-factor (if (:jitter config) (+ 0.5 (* 0.5 (rand))) 1.0)]
    (long (* capped-delay jitter-factor))))

(defn- should-retry? [attempt exception config]
  (and (< attempt (:max-attempts config))
       (:enabled config)
       ;; Add specific exception types that should trigger retry
       (or (instance? java.net.SocketTimeoutException exception)
           (instance? java.net.ConnectException exception)
           (instance? java.io.IOException exception)
           (and (instance? clojure.lang.ExceptionInfo exception)
                (#{:api-error :timeout :rate-limited} (:type (ex-data exception)))))))

(defn with-retry
  "Executes a function with retry logic"
  [operation-name f & {:keys [config] :or {config default-retry-config}}]
  (loop [attempt 1]
    (let [result (try
                   (let [result (f)]
                     (when (> attempt 1)
                       (log/info {:event :retry-success 
                                  :operation operation-name 
                                  :attempt attempt}))
                     {:success true :result result})
                   (catch Exception e
                     {:success false :exception e}))]
      (if (:success result)
        (:result result)
        (let [e (:exception result)]
          (if (should-retry? attempt e config)
            (let [delay (calculate-delay attempt config)]
              (log/warn {:event :retry-attempt 
                         :operation operation-name
                         :attempt attempt
                         :max-attempts (:max-attempts config)
                         :delay-ms delay
                         :error (ex-message e)})
              (Thread/sleep delay)
              (recur (inc attempt)))
            (do
              (log/error {:event :retry-exhausted 
                          :operation operation-name
                          :final-attempt attempt
                          :error (ex-message e)})
              (throw e))))))))

(defn with-circuit-breaker
  "Executes a function with circuit breaker protection"
  [circuit-name f & {:keys [config] :or {config default-circuit-config}}]
  (if-not (:enabled config)
    (f)
    (let [circuit-breaker (get-or-create-circuit-breaker circuit-name :config config)]
      (if (should-attempt? circuit-breaker)
        (try
          (when (= (state-from-int (.get (:state circuit-breaker))) :open)
            (.set (:state circuit-breaker) (int-from-state :half-open))
            (log/info {:event :circuit-breaker-half-open :name circuit-name}))
          
          (let [result (f)]
            (record-success! circuit-breaker)
            result)
          (catch Exception e
            (record-failure! circuit-breaker)
            (throw e)))
        (throw (ex-info "Circuit breaker is open"
                        {:type :circuit-breaker-open
                         :circuit-name circuit-name
                         :state (get-circuit-breaker-state circuit-name)}))))))

(defn with-rate-limit
  "Executes a function with rate limiting"
  [limiter-name f & {:keys [config] :or {config default-rate-limit-config}}]
  (if-not (:enabled config)
    (f)
    (let [rate-limiter (get-or-create-rate-limiter limiter-name :config config)]
      (if (try-acquire-token! rate-limiter)
        (f)
        (throw (ex-info "Rate limit exceeded"
                        {:type :rate-limited
                         :limiter-name limiter-name
                         :available-tokens (.get (:tokens rate-limiter))}))))))

(defn with-resilience
  "Combines retry, circuit breaker, and rate limiting"
  [operation-name f & {:keys [retry-config circuit-config rate-limit-config circuit-name rate-limiter-name]
                       :or {retry-config default-retry-config
                            circuit-config default-circuit-config
                            rate-limit-config default-rate-limit-config
                            circuit-name operation-name
                            rate-limiter-name operation-name}}]
  (with-retry operation-name
    (fn []
      (with-circuit-breaker circuit-name
        (fn []
          (with-rate-limit rate-limiter-name f
                          :config rate-limit-config))
        :config circuit-config))
    :config retry-config))

;; Statistics and Monitoring
(defn get-resilience-stats
  "Returns comprehensive resilience statistics"
  []
  (let [circuit-stats (reduce-kv
                       (fn [acc name cb]
                         (assoc acc name (get-circuit-breaker-state name)))
                       {}
                       circuit-breakers)
        rate-limiter-stats (reduce-kv
                           (fn [acc name rl]
                             (assoc acc name
                                    {:name name
                                     :available-tokens (.get (:tokens rl))
                                     :max-tokens (get-in rl [:config :burst-size])
                                     :requests-per-minute (get-in rl [:config :requests-per-minute])}))
                           {}
                           rate-limiters)]
    {:circuit-breakers circuit-stats
     :rate-limiters rate-limiter-stats
     :timestamp (current-time-ms)}))

(defn reset-circuit-breaker!
  "Manually resets a circuit breaker to closed state"
  [circuit-name]
  (when-let [cb (.get circuit-breakers circuit-name)]
    (.set (:state cb) (int-from-state :closed))
    (.set (:failure-count cb) 0)
    (.set (:success-count cb) 0)
    (log/info {:event :circuit-breaker-manual-reset :name circuit-name})))
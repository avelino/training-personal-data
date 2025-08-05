(ns training-personal-data.cache
  "Intelligent caching system for frequently accessed data with TTL support"
  (:require [taoensso.timbre :as log]
            [clojure.string :as str])
  (:import [java.util.concurrent ConcurrentHashMap]
           [java.util.concurrent.atomic AtomicLong]))

;; Cache Configuration
(def ^:private default-config
  {:ttl-minutes 60
   :max-entries 1000
   :cleanup-interval-minutes 10
   :enabled true})

;; Cache Storage
(def ^:private cache-store (ConcurrentHashMap.))
(def ^:private cache-stats (atom {:hits 0 :misses 0 :evictions 0 :puts 0}))
(def ^:private cache-config (atom default-config))

;; Cache Entry Structure
(defrecord CacheEntry [value created-at expires-at access-count last-accessed])

(defn- current-time-ms []
  (System/currentTimeMillis))

(defn- minutes-to-ms [minutes]
  (* minutes 60 1000))

(defn- expired? [entry]
  (> (current-time-ms) (:expires-at entry)))

(defn- create-cache-key [& parts]
  "Creates a cache key from multiple parts"
  (str/join ":" (map str parts)))

(defn configure-cache!
  "Updates cache configuration"
  [config]
  (swap! cache-config merge config)
  (log/info {:event :cache-configured :config @cache-config}))

(defn get-cache-config
  "Returns current cache configuration"
  []
  @cache-config)

;; Forward declarations
(declare cleanup-expired!)
(declare evict-lru!)

(defn put!
  "Stores a value in the cache with optional TTL override"
  [key value & {:keys [ttl-minutes] :or {ttl-minutes nil}}]
  (when (:enabled @cache-config)
    (let [config @cache-config
          ttl (or ttl-minutes (:ttl-minutes config))
          current-time (current-time-ms)
          expires-at (+ current-time (minutes-to-ms ttl))
          entry (->CacheEntry value current-time expires-at (AtomicLong. 0) current-time)]
      
      ;; Check if cache is full and needs cleanup
      (when (>= (.size cache-store) (:max-entries config))
        (cleanup-expired!)
        (when (>= (.size cache-store) (:max-entries config))
          (evict-lru!)))
      
      (.put cache-store key entry)
      (swap! cache-stats update :puts inc)
      
      (log/debug {:event :cache-put :key key :ttl-minutes ttl :expires-at expires-at})
      value)))

(defn get
  "Retrieves a value from the cache, returns nil if not found or expired"
  [key]
  (when (:enabled @cache-config)
    (if-let [entry (.get cache-store key)]
      (if (expired? entry)
        (do
          (.remove cache-store key)
          (swap! cache-stats update :misses inc)
          (log/debug {:event :cache-miss :key key :reason :expired})
          nil)
        (do
          (.incrementAndGet (:access-count entry))
          (swap! cache-stats update :hits inc)
          (log/debug {:event :cache-hit :key key :access-count (.get (:access-count entry))})
          (:value entry)))
      (do
        (swap! cache-stats update :misses inc)
        (log/debug {:event :cache-miss :key key :reason :not-found})
        nil))))

(defn get-or-compute
  "Gets value from cache or computes it using the provided function"
  [key compute-fn & {:keys [ttl-minutes] :or {ttl-minutes nil}}]
  (if-let [cached-value (get key)]
    cached-value
    (let [computed-value (compute-fn)]
      (put! key computed-value :ttl-minutes ttl-minutes)
      computed-value)))

(defn invalidate!
  "Removes a specific key from the cache"
  [key]
  (when (.remove cache-store key)
    (log/debug {:event :cache-invalidate :key key})
    true))

(defn invalidate-pattern!
  "Removes all keys matching a pattern (simple prefix matching)"
  [pattern]
  (let [keys-to-remove (filter #(str/starts-with? % pattern) (.keySet cache-store))
        removed-count (count keys-to-remove)]
    (doseq [key keys-to-remove]
      (.remove cache-store key))
    (log/info {:event :cache-invalidate-pattern :pattern pattern :removed-count removed-count})
    removed-count))

(defn cleanup-expired!
  "Removes all expired entries from the cache"
  []
  (let [current-time (current-time-ms)
        expired-keys (filter (fn [key]
                              (when-let [entry (.get cache-store key)]
                                (expired? entry)))
                            (.keySet cache-store))
        removed-count (count expired-keys)]
    (doseq [key expired-keys]
      (.remove cache-store key))
    (when (> removed-count 0)
      (swap! cache-stats update :evictions + removed-count)
      (log/debug {:event :cache-cleanup :removed-count removed-count}))
    removed-count))

(defn evict-lru!
  "Evicts least recently used entries to make space"
  []
  (let [entries-with-keys (map (fn [key]
                                {:key key
                                 :entry (.get cache-store key)})
                              (.keySet cache-store))
        sorted-entries (sort-by #(get-in % [:entry :last-accessed]) entries-with-keys)
        to-evict (take (int (* 0.1 (.size cache-store))) sorted-entries)
        evicted-count (count to-evict)]
    
    (doseq [{:keys [key]} to-evict]
      (.remove cache-store key))
    
    (when (> evicted-count 0)
      (swap! cache-stats update :evictions + evicted-count)
      (log/info {:event :cache-lru-eviction :evicted-count evicted-count}))
    evicted-count))

(defn clear!
  "Clears all entries from the cache"
  []
  (let [size (.size cache-store)]
    (.clear cache-store)
    (log/info {:event :cache-cleared :previous-size size})
    size))

(defn get-stats
  "Returns cache statistics"
  []
  (let [stats @cache-stats
        current-size (.size cache-store)
        hit-rate (if (> (+ (:hits stats) (:misses stats)) 0)
                  (double (/ (:hits stats) (+ (:hits stats) (:misses stats))))
                  0.0)]
    (merge stats
           {:current-size current-size
            :max-size (:max-entries @cache-config)
            :hit-rate hit-rate
            :utilization (double (/ current-size (:max-entries @cache-config)))})))

(defn reset-stats!
  "Resets cache statistics"
  []
  (reset! cache-stats {:hits 0 :misses 0 :evictions 0 :puts 0})
  (log/info {:event :cache-stats-reset}))

;; Predefined cache keys for common data types
(defn oura-data-key [data-type date-range]
  (create-cache-key "oura" data-type date-range))

(defn insights-key [insight-type period]
  (create-cache-key "insights" insight-type period))

(defn aggregation-key [table metric period]
  (create-cache-key "agg" table metric period))

;; Automatic cleanup scheduler (would need a proper scheduler in production)
(defn start-cleanup-scheduler!
  "Starts automatic cleanup of expired entries"
  []
  (future
    (while true
      (try
        (Thread/sleep (minutes-to-ms (:cleanup-interval-minutes @cache-config)))
        (cleanup-expired!)
        (catch InterruptedException e
          (log/info {:event :cache-cleanup-scheduler-stopped})
          (.interrupt (Thread/currentThread)))
        (catch Exception e
          (log/error {:event :cache-cleanup-error :error (ex-message e)}))))))

;; Cache warming functions
(defn warm-cache!
  "Pre-loads cache with commonly accessed data"
  [warm-fn-map]
  (log/info {:event :cache-warming-start :functions (count warm-fn-map)})
  (doseq [[cache-key warm-fn] warm-fn-map]
    (try
      (let [value (warm-fn)]
        (put! cache-key value)
        (log/debug {:event :cache-warmed :key cache-key}))
      (catch Exception e
        (log/error {:event :cache-warm-error :key cache-key :error (ex-message e)}))))
  (log/info {:event :cache-warming-complete :final-size (.size cache-store)}))
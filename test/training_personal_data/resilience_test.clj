(ns training-personal-data.resilience-test
  (:require [clojure.test :refer [deftest is testing]]
            [training-personal-data.resilience :as resilience]))

(deftest test-retry-mechanism
  (testing "Retry mechanism with successful operation"
    (let [call-count (atom 0)
          operation (fn [] 
                     (swap! call-count inc)
                     "success")]
      
      (is (= "success" (resilience/with-retry "test-op" operation)))
      (is (= 1 @call-count))))
  
  (testing "Retry mechanism with eventual success"
    (let [call-count (atom 0)
          operation (fn [] 
                     (swap! call-count inc)
                     (if (< @call-count 3)
                       (throw (java.io.IOException. "Temporary failure"))
                       "success"))]
      
      (is (= "success" (resilience/with-retry "test-op" operation)))
      (is (= 3 @call-count))))
  
  (testing "Retry mechanism with permanent failure"
    (let [call-count (atom 0)
          operation (fn [] 
                     (swap! call-count inc)
                     (throw (java.io.IOException. "Permanent failure")))]
      
      (is (thrown? java.io.IOException
                   (resilience/with-retry "test-op-fail" operation 
                                         :config {:max-attempts 2})))
      (is (= 2 @call-count)))))

(deftest test-circuit-breaker
  (testing "Circuit breaker closed state"
    (let [call-count (atom 0)
          operation (fn [] 
                     (swap! call-count inc)
                     "success")]
      
      (is (= "success" (resilience/with-circuit-breaker "test-cb" operation)))
      (is (= 1 @call-count))
      
      (let [state (resilience/get-circuit-breaker-state "test-cb")]
        (is (= :closed (:state state))))))
  
  (testing "Circuit breaker opening on failures"
    (let [call-count (atom 0)
          operation (fn [] 
                     (swap! call-count inc)
                     (throw (RuntimeException. "Service failure")))]
      
      ;; Make enough failures to open the circuit
      (dotimes [_ 5]
        (try
          (resilience/with-circuit-breaker "failing-cb-test" operation
                                          :config {:failure-threshold 3})
          (catch Exception _)))
      
      (let [state (resilience/get-circuit-breaker-state "failing-cb-test")]
        (is (some? state))
        (when state
          (is (= :open (:state state)))
          (is (>= (:failure-count state) 3)))))))

(deftest test-rate-limiter
  (testing "Rate limiter allows requests within limit"
    (let [call-count (atom 0)
          operation (fn [] 
                     (swap! call-count inc)
                     "success")]
      
      ;; Should allow first few requests
      (dotimes [_ 5]
        (is (= "success" (resilience/with-rate-limit "test-rl" operation
                                                    :config {:burst-size 10}))))
      (is (= 5 @call-count))))
  
  (testing "Rate limiter blocks requests over limit"
    (let [call-count (atom 0)
          operation (fn [] 
                     (swap! call-count inc)
                     "success")]
      
      ;; Exhaust the rate limiter
      (dotimes [_ 5]
        (try
          (resilience/with-rate-limit "exhausted-rl-test" operation
                                     :config {:burst-size 3})
          (catch Exception _)))
      
      ;; Should be blocked now - simplified test
      (try
        (resilience/with-rate-limit "exhausted-rl-test" operation
                                   :config {:burst-size 3})
        (is false "Should have thrown rate limit exception")
        (catch Exception e
          (is (str/includes? (ex-message e) "Rate limit")))))))

(deftest test-combined-resilience
  (testing "Combined resilience patterns"
    (let [call-count (atom 0)
          operation (fn [] 
                     (swap! call-count inc)
                     (if (< @call-count 2)
                       (throw (java.io.IOException. "Temporary failure"))
                       "success"))]
      
      (is (= "success" 
             (resilience/with-resilience "combined-test-unique" operation
                                        :retry-config {:max-attempts 3}
                                        :circuit-config {:failure-threshold 5}
                                        :rate-limit-config {:burst-size 10})))
      (is (= 2 @call-count)))))

(deftest test-resilience-stats
  (testing "Resilience statistics collection"
    ;; Create some circuit breakers and rate limiters
    (resilience/get-or-create-circuit-breaker "stats-cb")
    (resilience/get-or-create-rate-limiter "stats-rl")
    
    (let [stats (resilience/get-resilience-stats)]
      (is (contains? (:circuit-breakers stats) "stats-cb"))
      (is (contains? (:rate-limiters stats) "stats-rl"))
      (is (number? (:timestamp stats))))))

(deftest test-circuit-breaker-reset
  (testing "Manual circuit breaker reset"
    ;; Create a circuit breaker and force it to open
    (let [operation (fn [] (throw (RuntimeException. "Failure")))]
      (dotimes [_ 5]
        (try
          (resilience/with-circuit-breaker "reset-cb-test" operation
                                          :config {:failure-threshold 3})
          (catch Exception _)))
      
      ;; Verify it's open
      (let [state (resilience/get-circuit-breaker-state "reset-cb-test")]
        (when state
          (is (= :open (:state state)))))
      
      ;; Reset it
      (resilience/reset-circuit-breaker! "reset-cb-test")
      
      ;; Verify it's closed
      (let [state (resilience/get-circuit-breaker-state "reset-cb-test")]
        (when state
          (is (= :closed (:state state)))
          (is (= 0 (:failure-count state))))))))
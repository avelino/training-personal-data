(ns training-personal-data.cache-test
  (:require [clojure.test :refer [deftest is testing]]
            [training-personal-data.cache :as cache]))

(deftest test-cache-basic-operations
  (testing "Basic cache put and get operations"
    ;; Clear cache before test
    (cache/clear!)
    
    ;; Test put and get
    (cache/put! "test-key" "test-value")
    (is (= "test-value" (cache/get "test-key")))
    
    ;; Test get non-existent key
    (is (nil? (cache/get "non-existent-key")))
    
    ;; Test cache stats
    (let [stats (cache/get-stats)]
      (is (> (:current-size stats) 0))
      (is (>= (:hits stats) 1))
      (is (>= (:puts stats) 1)))))

(deftest test-cache-ttl
  (testing "Cache TTL functionality"
    (cache/clear!)
    
    ;; Put with very short TTL
    (cache/put! "ttl-key" "ttl-value" :ttl-minutes 0.001) ; ~60ms
    (is (= "ttl-value" (cache/get "ttl-key")))
    
    ;; Wait for expiration
    (Thread/sleep 100)
    (is (nil? (cache/get "ttl-key")))))

(deftest test-cache-get-or-compute
  (testing "Cache get-or-compute functionality"
    (cache/clear!)
    
    (let [compute-count (atom 0)
          compute-fn (fn [] 
                      (swap! compute-count inc)
                      "computed-value")]
      
      ;; First call should compute
      (is (= "computed-value" (cache/get-or-compute "compute-key" compute-fn)))
      (is (= 1 @compute-count))
      
      ;; Second call should use cache
      (is (= "computed-value" (cache/get-or-compute "compute-key" compute-fn)))
      (is (= 1 @compute-count)))))

(deftest test-cache-invalidation
  (testing "Cache invalidation"
    (cache/clear!)
    
    ;; Add some test data
    (cache/put! "key1" "value1")
    (cache/put! "key2" "value2")
    (cache/put! "prefix:key1" "prefix-value1")
    (cache/put! "prefix:key2" "prefix-value2")
    
    ;; Test single key invalidation
    (is (cache/invalidate! "key1"))
    (is (nil? (cache/get "key1")))
    (is (= "value2" (cache/get "key2")))
    
    ;; Test pattern invalidation
    (let [removed-count (cache/invalidate-pattern! "prefix:")]
      (is (= 2 removed-count))
      (is (nil? (cache/get "prefix:key1")))
      (is (nil? (cache/get "prefix:key2")))
      (is (= "value2" (cache/get "key2"))))))

(deftest test-cache-statistics
  (testing "Cache statistics accuracy"
    (cache/clear!)
    (cache/reset-stats!)
    
    ;; Generate some cache activity
    (cache/put! "stats-key1" "value1")
    (cache/put! "stats-key2" "value2")
    (cache/get "stats-key1")  ; hit
    (cache/get "stats-key1")  ; hit
    (cache/get "non-existent") ; miss
    
    (let [stats (cache/get-stats)]
      (is (= 2 (:current-size stats)))
      (is (= 2 (:puts stats)))
      (is (= 2 (:hits stats)))
      (is (= 1 (:misses stats)))
      (is (> (:hit-rate stats) 0.5)))))

(deftest test-cache-predefined-keys
  (testing "Predefined cache key functions"
    (is (= "oura:activity:2024-01-01-2024-01-07" 
           (cache/oura-data-key "activity" "2024-01-01-2024-01-07")))
    
    (is (= "insights:weekly:2024-W01" 
           (cache/insights-key "weekly" "2024-W01")))
    
    (is (= "agg:sleep:avg_score:weekly" 
           (cache/aggregation-key "sleep" "avg_score" "weekly")))))
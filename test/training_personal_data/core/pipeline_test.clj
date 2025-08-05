(ns training-personal-data.core.pipeline-test
  (:require [clojure.test :refer [deftest is testing]]
            [training-personal-data.core.pipeline :as pipeline]))

(defn mock-fetch-success [token start-date end-date]
  {:success? true
   :data [{:id "test-1" :name "Test Record 1" :value 42}
          {:id "test-2" :name "Test Record 2" :value 84}]})

(defn mock-fetch-failure [token start-date end-date]
  {:success? false
   :error "API Error"})

(defn mock-normalize [record]
  (assoc record :normalized true))

(defn mock-extract-values [record]
  [(:id record) (:name record) (:value record) (:normalized record)])

(def test-config
  {:name "test-endpoint"
   :table-name "test_table"
   :columns ["id" "name" "value" "normalized"]
   :schema {:id "TEXT PRIMARY KEY"
            :name "TEXT"
            :value "INTEGER"
            :normalized "BOOLEAN"}
   :fetch-fn mock-fetch-success
   :normalize-fn mock-normalize
   :extract-values-fn mock-extract-values})

(deftest test-execute-pipeline-success
  (testing "Pipeline executes successfully with valid data"
    (with-redefs [training-personal-data.db/create-table (fn [& _] nil)
                  training-personal-data.db/save (fn [& args] (last args))]
      (let [result (pipeline/execute-pipeline test-config "token" "2024-01-01" "2024-01-07" :mock true)]
        (is (= 2 (count result)))
        (is (every? :normalized result))))))

(deftest test-execute-pipeline-fetch-failure
  (testing "Pipeline handles fetch failures gracefully"
    (let [failing-config (assoc test-config :fetch-fn mock-fetch-failure)]
      (with-redefs [training-personal-data.db/create-table (fn [& _] nil)
                    training-personal-data.db/save (fn [& args] (last args))]
        (is (nil? (pipeline/execute-pipeline failing-config "token" "2024-01-01" "2024-01-07" :mock true)))))))

(deftest test-execute-batch-pipeline
  (testing "Batch pipeline processes multiple date ranges"
    (let [date-ranges [{:start "2024-01-01" :end "2024-01-02"}
                       {:start "2024-01-02" :end "2024-01-03"}]]
      (with-redefs [training-personal-data.db/create-table (fn [& _] nil)
                    training-personal-data.db/save (fn [& args] (last args))]
        (let [result (pipeline/execute-batch-pipeline test-config "token" date-ranges)]
          (is (= 4 (count result)))  ; 2 records per date range
          (is (every? :normalized result)))))))

(deftest test-pipeline-configuration
  (testing "Pipeline configuration contains all required keys"
    (let [config test-config]
      (is (string? (:name config)))
      (is (string? (:table-name config)))
      (is (vector? (:columns config)))
      (is (map? (:schema config)))
      (is (ifn? (:fetch-fn config)))
      (is (ifn? (:normalize-fn config)))
      (is (ifn? (:extract-values-fn config))))))

(deftest test-integration-without-db
  (testing "Integration test without real database"
    ;; This test verifies the pipeline flow without actual database operations
    (let [test-data (atom [])
          mock-config (-> test-config
                          (assoc :fetch-fn (fn [_ _ _]
                                             {:success? true
                                              :data [{:id "integration-test" :value 999}]}))
                          (assoc :normalize-fn (fn [record]
                                                 (swap! test-data conj record)
                                                 (assoc record :processed true)))
                          (assoc :extract-values-fn (fn [record]
                                                      [(:id record) (:value record) (:processed record)])))]

      (with-redefs [training-personal-data.db/create-table (fn [& _] nil)
                    training-personal-data.db/save (fn [& args] (last args))]
        (pipeline/execute-pipeline mock-config "token" "2024-01-01" "2024-01-07" :mock true)

        ;; Verify data flowed through the pipeline
        (is (= 1 (count @test-data)))
        (is (= "integration-test" (:id (first @test-data))))
        (is (= 999 (:value (first @test-data))))))))

;; Performance test (basic)
(deftest test-pipeline-performance
  (testing "Pipeline performance with multiple records"
    (let [large-dataset (repeatedly 100 #(hash-map :id (str "perf-" (rand-int 10000))
                                                   :name "Performance Test"
                                                   :value (rand-int 1000)))
          perf-config (assoc test-config
                             :fetch-fn (fn [_ _ _] {:success? true :data large-dataset}))]

      (with-redefs [training-personal-data.db/create-table (fn [& _] nil)
                    training-personal-data.db/save (fn [& args] (last args))]
        (let [start-time (System/currentTimeMillis)]
          (pipeline/execute-pipeline perf-config "token" "2024-01-01" "2024-01-07" :mock true)
          (let [duration (- (System/currentTimeMillis) start-time)]
            ;; Should complete within reasonable time (adjust threshold as needed)
            (is (< duration 5000) "Pipeline should complete within 5 seconds for 100 records")))))))

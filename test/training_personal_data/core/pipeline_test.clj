(ns training-personal-data.core.pipeline-test
  (:require [clojure.test :refer [deftest is testing]]
            [training-personal-data.core.pipeline :as pipeline]))

(defn mock-fetch-success [token start-date end-date]
  {:success? true
   :data [{:id "test-1" :name "Test Record 1" :value 42}
          {:id "test-2" :name "Test Record 2" :value 24}]})

(defn mock-fetch-failure [token start-date end-date]
  {:success? false
   :error "API Error"})

(defn mock-normalize [record]
  (assoc record :normalized true :timestamp "2024-01-01"))

(defn mock-extract-values [record]
  [(:id record) (:name record) (:value record) (:timestamp record)])

(def mock-schema
  {:id [:text :primary-key]
   :name :text
   :value :integer
   :timestamp :timestamp})

(def mock-columns
  ["id" "name" "value" "timestamp"])

(def test-config
  (pipeline/create-endpoint-config
   "test-endpoint"
   "test_table"
   mock-columns
   mock-schema
   mock-fetch-success
   mock-normalize
   mock-extract-values))

(def failing-config
  (pipeline/create-endpoint-config
   "failing-endpoint"
   "test_table"
   mock-columns
   mock-schema
   mock-fetch-failure
   mock-normalize
   mock-extract-values))

;; Mock database spec and functions for testing
(def mock-db-spec {:mock true})

(def captured-operations (atom []))

;; Override the common-db functions for testing
(with-redefs [training-personal-data.db/create-table
              (fn [db-spec table-name schema]
                (swap! captured-operations conj {:op :create-table
                                                 :table table-name
                                                 :schema schema})
                nil)

              training-personal-data.db/save
              (fn [db-spec table-name columns record values]
                (swap! captured-operations conj {:op :save
                                                 :table table-name
                                                 :columns columns
                                                 :record record
                                                 :values values})
                record)]

  (deftest test-create-endpoint-config
    (testing "Creating endpoint configuration"
      (let [config (pipeline/create-endpoint-config
                    "test"
                    "test_table"
                    ["id" "name"]
                    {:id :text :name :text}
                    mock-fetch-success
                    mock-normalize
                    mock-extract-values)]
        (is (= "test" (:name config)))
        (is (= "test_table" (:table-name config)))
        (is (= ["id" "name"] (:columns config)))
        (is (= {:id :text :name :text} (:schema config)))
        (is (fn? (:fetch-fn config)))
        (is (fn? (:normalize-fn config)))
        (is (fn? (:extract-values-fn config))))))

  (deftest test-execute-pipeline-success
    (testing "Successful pipeline execution"
      (reset! captured-operations [])

      (pipeline/execute-pipeline test-config "fake-token" "2024-01-01" "2024-01-07" mock-db-spec)

      (let [ops @captured-operations]
        ;; Should create table once
        (is (= 1 (count (filter #(= (:op %) :create-table) ops))))

        ;; Should save two records
        (is (= 2 (count (filter #(= (:op %) :save) ops))))

        ;; Check table creation
        (let [create-op (first (filter #(= (:op %) :create-table) ops))]
          (is (= "test_table" (:table create-op)))
          (is (= mock-schema (:schema create-op))))

        ;; Check record saves
        (let [save-ops (filter #(= (:op %) :save) ops)]
          (is (every? #(= "test_table" (:table %)) save-ops))
          (is (every? #(= mock-columns (:columns %)) save-ops))
          (is (every? #(true? (get-in % [:record :normalized])) save-ops))))))

  (deftest test-execute-pipeline-failure
    (testing "Pipeline execution with fetch failure"
      (reset! captured-operations [])

      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Failed to fetch failing-endpoint data"
           (pipeline/execute-pipeline failing-config "fake-token" "2024-01-01" "2024-01-07" mock-db-spec)))

      ;; Should still create table but not save any records
      (let [ops @captured-operations]
        (is (= 1 (count (filter #(= (:op %) :create-table) ops))))
        (is (= 0 (count (filter #(= (:op %) :save) ops)))))))

  (deftest test-batch-execute-pipeline
    (testing "Batch pipeline execution"
      (reset! captured-operations [])

      (pipeline/batch-execute-pipeline test-config "fake-token" "2024-01-01" "2024-01-07" mock-db-spec
                                       :batch-size 1)

      (let [ops @captured-operations]
        ;; Should create table once
        (is (= 1 (count (filter #(= (:op %) :create-table) ops))))

        ;; Should save two records (same as regular pipeline)
        (is (= 2 (count (filter #(= (:op %) :save) ops)))))))

  (deftest test-data-transformation
    (testing "Data transformation pipeline"
      (let [raw-data [{:id "raw-1" :name "Raw Record" :value 100}]
            normalized (mock-normalize (first raw-data))
            values (mock-extract-values normalized)]

        ;; Check normalization
        (is (true? (:normalized normalized)))
        (is (= "2024-01-01" (:timestamp normalized)))
        (is (= "raw-1" (:id normalized)))

        ;; Check value extraction
        (is (= ["raw-1" "Raw Record" 100 "2024-01-01"] values)))))

  (deftest test-configuration-validation
    (testing "Configuration parameter validation"
      (let [config test-config]
        (is (string? (:name config)))
        (is (string? (:table-name config)))
        (is (vector? (:columns config)))
        (is (map? (:schema config)))
        (is (ifn? (:fetch-fn config)))
        (is (ifn? (:normalize-fn config)))
        (is (ifn? (:extract-values-fn config))))))

  (deftest test-error-handling
    (testing "Error handling in pipeline components"
      ;; Test invalid endpoint config
      (is (thrown? Exception
                   (pipeline/execute-pipeline nil "token" "2024-01-01" "2024-01-07" mock-db-spec)))

      ;; Test missing required fields
      (let [incomplete-config (dissoc test-config :fetch-fn)]
        (is (thrown? Exception
                     (pipeline/execute-pipeline incomplete-config "token" "2024-01-01" "2024-01-07" mock-db-spec)))))))

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
        (pipeline/execute-pipeline mock-config "token" "2024-01-01" "2024-01-07" {})

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
          (pipeline/execute-pipeline perf-config "token" "2024-01-01" "2024-01-07" {})
          (let [duration (- (System/currentTimeMillis) start-time)]
            ;; Should complete within reasonable time (adjust threshold as needed)
            (is (< duration 5000) "Pipeline should complete within 5 seconds for 100 records")))))))

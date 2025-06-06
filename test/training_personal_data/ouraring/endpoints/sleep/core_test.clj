(ns training-personal-data.ouraring.endpoints.sleep.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.sleep.core :as core]
            [training-personal-data.ouraring.endpoints.sleep.api :as api]
            [training-personal-data.ouraring.endpoints.sleep.db :as sleep-db]
            [training-personal-data.db :as db]))

(def sample-api-response
  {:success? true
   :data [{:id "123"
           :day "2024-01-07"
           :score 85
           :contributors {:deep_sleep 80
                          :efficiency 90
                          :latency 75
                          :rem_sleep 85
                          :restfulness 88
                          :timing 82
                          :total_sleep 480}}]})

(defn mock-fetch [token start-date end-date]
  sample-api-response)

(def saved-records (atom []))

(defn mock-save [_ table-name columns record values]
  (swap! saved-records conj record)
  {:success true})

(defn mock-create-table [_ table-name schema]
  {:success true})

(deftest test-fetch-and-save
  (testing "fetch and save sleep data using refactored pipeline"
    (reset! saved-records [])
    (with-redefs [training-personal-data.ouraring.endpoints.sleep.api/fetch mock-fetch
                  training-personal-data.db/save mock-save
                  training-personal-data.db/create-table mock-create-table]
      ;; Execute fetch-and-save with new pipeline
      (core/fetch-and-save "test-token" "2024-01-07" "2024-01-08" {})

      ;; Verify data was saved
      (let [saved-record (first @saved-records)]
        (is (some? saved-record))
        (is (= "123" (:id saved-record)))
        (is (= 85 (:score saved-record)))
        (is (= 80 (:deep_sleep saved-record)))
        (is (= 90 (:efficiency saved-record)))
        (is (some? (:contributors_json saved-record)))
        (is (some? (:raw_json saved-record))))))

  (deftest test-sleep-config
    (testing "sleep endpoint configuration"
      (let [config core/sleep-config]
        (is (= "sleep" (:name config)))
        (is (= sleep-db/table-name (:table-name config)))
        (is (= sleep-db/columns (:columns config)))
        (is (= sleep-db/schema (:schema config)))
        (is (fn? (:fetch-fn config)))
        (is (fn? (:normalize-fn config)))
        (is (fn? (:extract-values-fn config))))))

  (deftest test-fetch-and-save-batch
    (testing "batch processing for sleep data"
      (reset! saved-records [])
      (with-redefs [training-personal-data.ouraring.endpoints.sleep.api/fetch mock-fetch
                    training-personal-data.db/save mock-save
                    training-personal-data.db/create-table mock-create-table]
      ;; Execute batch fetch-and-save
        (core/fetch-and-save-batch "test-token" "2024-01-07" "2024-01-08" {} :batch-size 1)

      ;; Verify data was saved
        (let [saved-record (first @saved-records)]
          (is (some? saved-record))
          (is (= "123" (:id saved-record))))))))

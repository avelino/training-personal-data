(ns training-personal-data.ouraring.endpoints.readiness.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.readiness.core :as core]
            [training-personal-data.ouraring.endpoints.readiness.db :as readiness-db]
            [training-personal-data.ouraring.api :as oura-api]
            [training-personal-data.db :as db]))

(def sample-api-response
  {:success? true
   :data [{:id "123"
           :day "2024-01-07"
           :score 85
           :temperature_trend_deviation 0.5
           :temperature_deviation 0.3
           :contributors {:previous_day_activity 85
                          :sleep_balance 80
                          :previous_night 90}}]})

(defn mock-fetch [_ _ _ _]
  sample-api-response)

(def saved-records (atom []))

(defn mock-save [_ _ _ record _]
  (swap! saved-records conj record)
  {:success true})

(defn mock-create-table [_ _ _]
  {:success true})

(deftest test-fetch-and-save
  (testing "fetch and save readiness data using refactored pipeline"
    (reset! saved-records [])
    (with-redefs [oura-api/fetch-data mock-fetch
                  training-personal-data.db/save mock-save
                  training-personal-data.db/create-table mock-create-table]
      ;; Execute fetch-and-save with new pipeline
      (core/fetch-and-save "test-token" "2024-01-07" "2024-01-08" {})

      ;; Verify data was saved
      (let [saved-record (first @saved-records)]
        (is (some? saved-record))
        (is (= "123" (:id saved-record)))
        (is (= 85 (:score saved-record)))
        (is (= 0.5 (:temperature_trend saved-record)))
        (is (= 0.3 (:temperature_deviation saved-record)))
        (is (some? (:contributors_json saved-record)))
        (is (some? (:raw_json saved-record))))))

  (deftest test-readiness-config
    (testing "readiness endpoint configuration"
      (let [config core/readiness-config]
        (is (= "readiness" (:name config)))
        (is (= readiness-db/table-name (:table-name config)))
        (is (= readiness-db/columns (:columns config)))
        (is (= readiness-db/schema (:schema config)))
        (is (fn? (:fetch-fn config)))
        (is (fn? (:normalize-fn config)))
        (is (fn? (:extract-values-fn config))))))

  (deftest test-fetch-and-save-batch
    (testing "batch processing for readiness data"
      (reset! saved-records [])
      (with-redefs [oura-api/fetch-data mock-fetch
                    training-personal-data.db/save mock-save
                    training-personal-data.db/create-table mock-create-table]
        ;; Execute batch fetch-and-save
        (core/fetch-and-save-batch "test-token" "2024-01-07" "2024-01-08" {} :batch-size 1)

        ;; Verify data was saved
        (let [saved-record (first @saved-records)]
          (is (some? saved-record))
          (is (= "123" (:id saved-record))))))))

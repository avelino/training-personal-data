(ns training-personal-data.ouraring.endpoints.readiness.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.readiness.core :as core]
            [training-personal-data.ouraring.endpoints.readiness.db :as db]
            [training-personal-data.ouraring.db :as common-db]))

(def sample-api-response
  {:success? true
   :data [{:id "123"
           :day "2024-01-07"
           :score 85
           :temperature_trend_deviation 0.2
           :temperature_deviation 0.1
           :contributors {:activity_balance 85
                        :body_temperature 90
                        :hrv_balance 88
                        :previous_day_activity 82
                        :previous_night 85
                        :recovery_index 87
                        :resting_heart_rate 75
                        :sleep_balance 89}}]})

(defn mock-fetch [token start-date end-date]
  sample-api-response)

(def saved-records (atom []))

(defn mock-save [_ table-name columns record values]
  (swap! saved-records conj record)
  {:success true})

(defn mock-create-table [_ table-name schema]
  {:success true})

(deftest test-fetch-and-save
  (testing "fetch and save readiness data"
    (reset! saved-records [])
    (with-redefs [training-personal-data.ouraring.endpoints.readiness.api/fetch mock-fetch
                  training-personal-data.ouraring.db/save mock-save
                  training-personal-data.ouraring.db/create-table mock-create-table]
      ;; Execute fetch-and-save
      (core/fetch-and-save "test-token" "2024-01-07" "2024-01-08" {})
      
      ;; Verify data was saved
      (let [saved-record (first @saved-records)]
        (is (some? saved-record))
        (is (= "123" (:id saved-record)))
        (is (= 85 (:score saved-record)))
        (is (= 0.2 (:temperature_trend saved-record)))
        (is (= 0.1 (:temperature_deviation saved-record)))
        (is (some? (:contributors_json saved-record)))
        (is (some? (:raw_json saved-record))))))) 
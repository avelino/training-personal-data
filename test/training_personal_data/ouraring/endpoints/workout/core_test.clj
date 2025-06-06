(ns training-personal-data.ouraring.endpoints.workout.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.workout.core :as core]
            [training-personal-data.ouraring.endpoints.workout.api :as api]
            [training-personal-data.db :as db]))

(def sample-api-response
  {:success? true
   :data [{:id "123"
           :day "2024-01-07"
           :activity "running"
           :calories 500
           :day_id "2024-01-07"
           :distance 5000
           :end_datetime "2024-01-07T09:00:00+00:00"
           :intensity "moderate"
           :label "Morning Run"
           :source "manual"
           :start_datetime "2024-01-07T08:00:00+00:00"}]})

(defn mock-fetch [token endpoint start-date end-date]
  sample-api-response)

(def saved-records (atom []))

(defn mock-save [_ table-name columns record values]
  (swap! saved-records conj record)
  {:success true})

(defn mock-create-table [_ table-name schema]
  {:success true})

(deftest test-fetch-and-save
  (testing "fetch and save workout data"
    (reset! saved-records [])
    (with-redefs [training-personal-data.ouraring.api/fetch-data mock-fetch
                  training-personal-data.db/save mock-save
                  training-personal-data.db/create-table mock-create-table]
      ;; Execute fetch-and-save
      (core/fetch-and-save "test-token" "2024-01-07" "2024-01-08" {})

      ;; Verify data was saved
      (let [saved-record (first @saved-records)]
        (is (some? saved-record))
        (is (= "123" (:id saved-record)))
        (is (= "running" (:activity saved-record)))
        (is (= 500 (:calories saved-record)))
        (is (= 5000 (:distance saved-record)))
        (is (= "moderate" (:intensity saved-record)))
        (is (some? (:raw_json saved-record)))))))

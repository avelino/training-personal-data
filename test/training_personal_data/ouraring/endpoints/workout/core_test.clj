(ns training-personal-data.ouraring.endpoints.workout.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.workout.core :as core]
            [training-personal-data.ouraring.endpoints.workout.db :as db]
            [training-personal-data.ouraring.db :as common-db]
            [cheshire.core :as json]))

(def sample-api-response
  {:success? true
   :data [{:id "123"
           :day "2024-01-07"
           :timestamp "2024-01-07T08:00:00+00:00"
           :activity "running"
           :calories 500
           :distance 5000
           :intensity "moderate"
           :source "manual"
           :heart_rate {:avg 140
                       :max 160
                       :min 120}}]})

(defn mock-fetch [token start-date end-date]
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
    (with-redefs [training-personal-data.ouraring.endpoints.workout.api/fetch mock-fetch
                  training-personal-data.ouraring.db/save mock-save
                  training-personal-data.ouraring.db/create-table mock-create-table]
      ;; Execute fetch-and-save
      (core/fetch-and-save "test-token" "2024-01-07" "2024-01-08" {})
      
      ;; Verify data was saved
      (let [saved-record (first @saved-records)
            raw-json (json/parse-string (:raw_json saved-record) true)]
        (is (some? saved-record))
        (is (= "123" (:id saved-record)))
        (is (= "running" (:activity saved-record)))
        (is (= 500 (:calories saved-record)))
        (is (= 5000 (:distance saved-record)))
        (is (= "moderate" (:intensity saved-record)))
        (is (= "manual" (:source saved-record)))
        (is (= {:avg 140 :max 160 :min 120} (:heart_rate raw-json)))
        (is (some? (:raw_json saved-record))))))) 
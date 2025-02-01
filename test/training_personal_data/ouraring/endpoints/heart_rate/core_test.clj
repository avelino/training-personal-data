(ns training-personal-data.ouraring.endpoints.heart-rate.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.heart-rate.core :as core]
            [training-personal-data.ouraring.endpoints.heart-rate.api :as api]
            [training-personal-data.db :as db]))

(def sample-api-response
  {:success? true
   :data [{:timestamp "2024-01-07T08:00:00+00:00"
           :bpm 65
           :source "sensor"}]})

(defn mock-fetch [token start-date end-date]
  sample-api-response)

(def saved-records (atom []))

(defn mock-save [_ table-name columns record values]
  (swap! saved-records conj record)
  {:success true})

(defn mock-create-table [_ table-name schema]
  {:success true})

(deftest test-fetch-and-save
  (testing "fetch and save heart rate data"
    (reset! saved-records [])
    (with-redefs [training-personal-data.ouraring.endpoints.heart-rate.api/fetch mock-fetch
                  training-personal-data.db/save mock-save
                  training-personal-data.db/create-table mock-create-table]
      ;; Execute fetch-and-save
      (core/fetch-and-save "test-token" "2024-01-07" "2024-01-08" {})

      ;; Verify data was saved
      (let [saved-record (first @saved-records)]
        (is (some? saved-record))
        (is (= "2024-01-07T08:00:00+00:00-65-sensor" (:id saved-record)))
        (is (= 65 (:bpm saved-record)))
        (is (= "sensor" (:source saved-record)))
        (is (= "2024-01-07T08:00:00+00:00" (:timestamp saved-record)))
        (is (some? (:raw_json saved-record)))))))
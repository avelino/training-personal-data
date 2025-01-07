(ns training-personal-data.ouraring.endpoints.tags.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.tags.core :as core]
            [training-personal-data.ouraring.endpoints.tags.db :as db]
            [training-personal-data.ouraring.db :as common-db]))

(def sample-api-response
  {:success? true
   :data [{:id "123"
           :day "2024-01-07"
           :text "Test tag"
           :timestamp "2024-01-07T08:00:00+00:00"
           :tags ["test" "sample"]}]})

(defn mock-fetch [token start-date end-date]
  sample-api-response)

(def saved-records (atom []))

(defn mock-save [_ table-name columns record values]
  (swap! saved-records conj record)
  {:success true})

(defn mock-create-table [_ table-name schema]
  {:success true})

(deftest test-fetch-and-save
  (testing "fetch and save tags data"
    (reset! saved-records [])
    (with-redefs [training-personal-data.ouraring.endpoints.tags.api/fetch mock-fetch
                  training-personal-data.ouraring.db/save mock-save
                  training-personal-data.ouraring.db/create-table mock-create-table]
      ;; Execute fetch-and-save
      (core/fetch-and-save "test-token" "2024-01-07" "2024-01-08" {})
      
      ;; Verify data was saved
      (let [saved-record (first @saved-records)]
        (is (some? saved-record))
        (is (= "123" (:id saved-record)))
        (is (= "Test tag" (:text saved-record)))
        (is (= ["test" "sample"] (:tags saved-record)))
        (is (= "2024-01-07T08:00:00+00:00" (:timestamp saved-record)))
        (is (some? (:raw_json saved-record))))))) 
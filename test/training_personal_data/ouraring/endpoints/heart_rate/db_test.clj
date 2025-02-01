(ns training-personal-data.ouraring.endpoints.heart-rate.db-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.heart-rate.db :as db]
            [training-personal-data.db :as common-db]))

(def sample-heart-rate
  {:id "2024-01-07T08:00:00+00:00-65-sensor"
   :date "2024-01-07"
   :timestamp "2024-01-07T08:00:00+00:00"
   :bpm 65
   :source "sensor"
   :raw_json "{\"timestamp\":\"2024-01-07T08:00:00+00:00\",\"bpm\":65,\"source\":\"sensor\"}"})

(def saved-records (atom []))

(defn mock-save [_ table-name columns record values]
  (swap! saved-records conj {:record record :values values})
  {:success true})

(defn mock-create-table [_ table-name schema]
  {:success true})

(deftest test-extract-values
  (testing "extract values from heart rate record"
    (let [values (db/extract-values sample-heart-rate)]
      (is (= "2024-01-07T08:00:00+00:00-65-sensor" (first values)))
      (is (= "2024-01-07" (second values)))
      (is (= "2024-01-07T08:00:00+00:00" (nth values 2)))
      (is (= 65 (nth values 3)))
      (is (= "sensor" (nth values 4))))))

(deftest test-db-operations
  (testing "save heart rate record"
    (reset! saved-records [])
    (with-redefs [training-personal-data.db/save mock-save
                  training-personal-data.db/create-table mock-create-table]
      ;; Test save
      (let [values (db/extract-values sample-heart-rate)]
        (common-db/save {} db/table-name db/columns sample-heart-rate values)
        (let [saved (first @saved-records)]
          (is (= sample-heart-rate (:record saved)))
          (is (= values (:values saved))))))))
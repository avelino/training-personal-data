(ns training-personal-data.ouraring.endpoints.workout.db-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.workout.db :as db]
            [training-personal-data.ouraring.db :as common-db]))

(def sample-workout
  {:id "123"
   :date "2024-01-07"
   :activity "running"
   :calories 500
   :day_id "2024-01-07"
   :distance 5000
   :end_datetime "2024-01-07T09:00:00+00:00"
   :intensity "moderate"
   :label "Morning Run"
   :source "manual"
   :start_datetime "2024-01-07T08:00:00+00:00"
   :raw_json "{\"id\":\"123\",\"activity\":\"running\"}"})

(def saved-records (atom []))

(defn mock-save [_ table-name columns record values]
  (swap! saved-records conj {:record record :values values})
  {:success true})

(defn mock-create-table [_ table-name schema]
  {:success true})

(deftest test-extract-values
  (testing "extract values from workout record"
    (let [values (db/extract-values sample-workout)]
      (is (= "123" (first values)))
      (is (= "2024-01-07" (second values)))
      (is (= "running" (nth values 2)))
      (is (= 500 (nth values 3)))
      (is (= "2024-01-07" (nth values 4)))
      (is (= 5000 (nth values 5)))
      (is (= "2024-01-07T09:00:00+00:00" (nth values 6)))
      (is (= "moderate" (nth values 7)))
      (is (= "Morning Run" (nth values 8)))
      (is (= "manual" (nth values 9)))
      (is (= "2024-01-07T08:00:00+00:00" (nth values 10)))
      (is (= (:raw_json sample-workout) (nth values 11))))))

(deftest test-db-operations
  (testing "save workout record"
    (reset! saved-records [])
    (with-redefs [training-personal-data.ouraring.db/save mock-save
                  training-personal-data.ouraring.db/create-table mock-create-table]
      ;; Test save
      (let [values (db/extract-values sample-workout)]
        (common-db/save {} db/table-name db/columns sample-workout values)
        (let [saved (first @saved-records)]
          (is (= sample-workout (:record saved)))
          (is (= values (:values saved)))))))) 
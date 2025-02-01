(ns training-personal-data.ouraring.endpoints.activity.db-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.activity.db :as db]
            [training-personal-data.db :as common-db]))

(def sample-activity
  {:id "123"
   :date "2024-01-07"
   :score 85
   :class_5_min "[1,2,3]"
   :active_calories 500
   :average_met 1.5
   :daily_movement 1000
   :equivalent_walking_distance 5000
   :high_activity_met_minutes 30
   :high_activity_time 1800
   :inactivity_alerts 2
   :low_activity_met_minutes 120
   :low_activity_time 7200
   :medium_activity_met_minutes 60
   :medium_activity_time 3600
   :met "{\"interval\":[1.2,1.3,1.4]}"
   :meters_to_target 2000
   :non_wear_time 1800
   :resting_time 28800
   :sedentary_met_minutes 480
   :sedentary_time 28800
   :steps 10000
   :target_calories 600
   :target_meters 8000
   :total_calories 2400
   :day_summary "{\"id\":\"123\",\"score\":85}"
   :raw_json "{\"id\":\"123\",\"score\":85}"})

(def saved-records (atom []))

(defn mock-save [_ table-name columns record values]
  (swap! saved-records conj {:record record :values values})
  {:success true})

(defn mock-create-table [_ table-name schema]
  {:success true})

(deftest test-extract-values
  (testing "extract values from activity record"
    (let [values (db/extract-values sample-activity)]
      (is (= "123" (first values)))
      (is (= "2024-01-07" (second values)))
      (is (= "[1,2,3]" (nth values 2)))
      (is (= 85 (nth values 3)))
      (is (= 500 (nth values 4)))
      (is (= 1.5 (nth values 5))))))

(deftest test-db-operations
  (testing "save activity record"
    (reset! saved-records [])
    (with-redefs [training-personal-data.db/save mock-save
                  training-personal-data.db/create-table mock-create-table]
      ;; Test save
      (let [values (db/extract-values sample-activity)]
        (common-db/save {} db/table-name db/columns sample-activity values)
        (let [saved (first @saved-records)]
          (is (= sample-activity (:record saved)))
          (is (= values (:values saved))))))))
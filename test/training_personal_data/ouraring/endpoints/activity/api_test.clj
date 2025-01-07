(ns training-personal-data.ouraring.endpoints.activity.api-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.activity.api :as api]
            [cheshire.core :as json]))

(def sample-activity-data
  {:id "123"
   :day "2024-01-07"
   :score 85
   :timestamp "2024-01-07T08:00:00+00:00"
   :class_5_min [1 2 3]
   :met {:interval [1.2 1.3 1.4]
         :detailed [1.1 1.2 1.3]}
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
   :meters_to_target 2000
   :non_wear_time 1800
   :resting_time 28800
   :sedentary_met_minutes 480
   :sedentary_time 28800
   :steps 10000
   :target_calories 600
   :target_meters 8000
   :total_calories 2400})

(deftest test-normalize
  (testing "normalize activity data"
    (let [normalized (api/normalize sample-activity-data)]
      (is (= "123" (:id normalized)))
      (is (= "2024-01-07" (:date normalized)))
      (is (= 85 (:score normalized)))
      (is (= 500 (:active_calories normalized)))
      (is (= 1.5 (:average_met normalized)))
      (is (= 1000 (:daily_movement normalized)))
      (is (= (json/generate-string (:met sample-activity-data))
             (:met normalized)))
      (is (= (json/generate-string sample-activity-data)
             (:day_summary normalized)))
      (is (= (json/generate-string sample-activity-data)
             (:raw_json normalized)))
      (is (= (json/generate-string (:class_5_min sample-activity-data))
             (:class_5_min normalized)))))) 
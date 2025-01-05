(ns training-personal-data.ouraring.endpoints.activity.api
  (:require [training-personal-data.ouraring.api :as api]
            [cheshire.core :as json]))

(def ^:private endpoint "/daily_activity")

(defn fetch [token start-date end-date]
  (api/fetch-data token endpoint start-date end-date))

(defn normalize [activity]
  {:date (:day activity)
   :class_5_min (json/generate-string (:class_5_min activity))
   :score (:score activity)
   :active_calories (:active_calories activity)
   :average_met (:average_met activity)
   :daily_movement (:daily_movement activity)
   :equivalent_walking_distance (:equivalent_walking_distance activity)
   :high_activity_met_minutes (:high_activity_met_minutes activity)
   :high_activity_time (:high_activity_time activity)
   :inactivity_alerts (:inactivity_alerts activity)
   :low_activity_met_minutes (:low_activity_met_minutes activity)
   :low_activity_time (:low_activity_time activity)
   :medium_activity_met_minutes (:medium_activity_met_minutes activity)
   :medium_activity_time (:medium_activity_time activity)
   :met (json/generate-string (:met activity))
   :meters_to_target (:meters_to_target activity)
   :non_wear_time (:non_wear_time activity)
   :resting_time (:resting_time activity)
   :sedentary_met_minutes (:sedentary_met_minutes activity)
   :sedentary_time (:sedentary_time activity)
   :steps (:steps activity)
   :target_calories (:target_calories activity)
   :target_meters (:target_meters activity)
   :total_calories (:total_calories activity)
   :day_summary (json/generate-string activity)}) 
(ns training-personal-data.ouraring.endpoints.activity.db
  (:require [training-personal-data.db :as db]
            [pod.babashka.postgresql :as pg]))

(def table-name "ouraring_daily_activity")

(def columns
  ["id" "date" "class_5_min" "score" "active_calories" "average_met"
   "daily_movement" "equivalent_walking_distance" "high_activity_met_minutes"
   "high_activity_time" "inactivity_alerts" "low_activity_met_minutes"
   "low_activity_time" "medium_activity_met_minutes" "medium_activity_time"
   "met" "meters_to_target" "non_wear_time" "resting_time"
   "sedentary_met_minutes" "sedentary_time" "steps" "target_calories"
   "target_meters" "total_calories" "day_summary" "raw_json"])

(def schema
  {:id [:text :primary-key]
   :date [:date]
   :class_5_min :text
   :score :integer
   :active_calories :integer
   :average_met ["double precision"]
   :daily_movement :integer
   :equivalent_walking_distance :integer
   :high_activity_met_minutes :integer
   :high_activity_time :integer
   :inactivity_alerts :integer
   :low_activity_met_minutes :integer
   :low_activity_time :integer
   :medium_activity_met_minutes :integer
   :medium_activity_time :integer
   :met :jsonb
   :meters_to_target :integer
   :non_wear_time :integer
   :resting_time :integer
   :sedentary_met_minutes :integer
   :sedentary_time :integer
   :steps :integer
   :target_calories :integer
   :target_meters :integer
   :total_calories :integer
   :day_summary :jsonb
   :raw_json :jsonb
   :timestamp [:timestamp :default "CURRENT_TIMESTAMP"]})

(defn extract-values [activity]
  [(str (:id activity))
   (str (:date activity))
   (str (:class_5_min activity))
   (:score activity)
   (:active_calories activity)
   (:average_met activity)
   (:daily_movement activity)
   (:equivalent_walking_distance activity)
   (:high_activity_met_minutes activity)
   (:high_activity_time activity)
   (:inactivity_alerts activity)
   (:low_activity_met_minutes activity)
   (:low_activity_time activity)
   (:medium_activity_met_minutes activity)
   (:medium_activity_time activity)
   (str (:met activity))
   (:meters_to_target activity)
   (:non_wear_time activity)
   (:resting_time activity)
   (:sedentary_met_minutes activity)
   (:sedentary_time activity)
   (:steps activity)
   (:target_calories activity)
   (:target_meters activity)
   (:total_calories activity)
   (str (:day_summary activity))
   (str (:raw_json activity))])
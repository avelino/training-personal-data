(ns training-personal-data.ouraring.endpoints.workout.db
  (:require [training-personal-data.ouraring.db :as db]
            [pod.babashka.postgresql :as pg]))

(def table-name "ouraring_workout")

(def columns
  ["id" "date" "activity" "calories" "day_id" "distance" "end_datetime"
   "intensity" "label" "source" "start_datetime" "raw_json"])

(def schema
  {:id [:text :primary-key]
   :date [:date]
   :activity :text
   :calories :integer
   :day_id :text
   :distance :integer
   :end_datetime [:timestamp]
   :intensity :text
   :label :text
   :source :text
   :start_datetime [:timestamp]
   :raw_json :jsonb
   :timestamp [:timestamp :default "CURRENT_TIMESTAMP"]})

(defn extract-values [workout]
  [(:id workout)
   (:date workout)
   (:activity workout)
   (:calories workout)
   (:day_id workout)
   (:distance workout)
   (:end_datetime workout)
   (:intensity workout)
   (:label workout)
   (:source workout)
   (:start_datetime workout)
   (:raw_json workout)]) 
(ns training-personal-data.ouraring.endpoints.workout.db
  (:require [training-personal-data.ouraring.db :as db]
            [pod.babashka.postgresql :as pg]))

(def table-name "ouraring_workout")

(def columns
  ["activity" "calories" "day_id" "distance" "end_datetime"
   "intensity" "label" "source" "start_datetime" "raw_json"])

(def schema
  {:date [:date]
   :activity :text
   :calories :integer
   :day_id :text
   :distance :integer
   :end_datetime [:timestamp]
   :intensity :text
   :label :text
   :source :text
   :start_datetime [:timestamp]
   :raw_json :text
   :timestamp [:timestamp :default "CURRENT_TIMESTAMP"]
   :pk ["date" "start_datetime" "activity"]})

(defn record-exists? [db-spec date start-datetime activity]
  (-> (pg/execute! db-spec
                   [(str "SELECT EXISTS(SELECT 1 FROM " table-name 
                         " WHERE date = ?::date"
                         " AND start_datetime = ?::timestamp"
                         " AND activity = ?) AS exists") 
                    date start-datetime activity])
      first
      :exists))

(defn extract-values [workout]
  [(:activity workout)
   (:calories workout)
   (:day_id workout)
   (:distance workout)
   (:end_datetime workout)
   (:intensity workout)
   (:label workout)
   (:source workout)
   (:start_datetime workout)
   (:raw_json workout)]) 
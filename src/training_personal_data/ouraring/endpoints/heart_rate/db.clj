(ns training-personal-data.ouraring.endpoints.heart-rate.db
  (:require [training-personal-data.ouraring.db :as db]
            [pod.babashka.postgresql :as pg]))

(def table-name "ouraring_heart_rate")

(def columns
  ["timestamp" "bpm" "source" "raw_json"])

(def schema
  {:date [:date]
   :timestamp [:timestamp]
   :bpm :integer
   :source :text
   :raw_json :text
   :created_at [:timestamp :default "CURRENT_TIMESTAMP"]})

(defn record-exists? [db-spec date bpm source]
  (-> (pg/execute! db-spec
                   [(str "SELECT EXISTS(SELECT 1 FROM " table-name 
                         " WHERE date = ?::date AND bpm = ? AND source = ?) AS exists") 
                    date bpm source])
      first
      :exists))

(defn extract-values [heart-rate]
  [(:timestamp heart-rate)
   (:bpm heart-rate)
   (:source heart-rate)
   (:raw_json heart-rate)]) 
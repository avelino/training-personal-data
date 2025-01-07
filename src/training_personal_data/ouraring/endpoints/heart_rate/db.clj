(ns training-personal-data.ouraring.endpoints.heart-rate.db
  (:require [training-personal-data.ouraring.db :as db]
            [pod.babashka.postgresql :as pg]))

(def table-name "ouraring_heart_rate")

(def columns
  ["id" "date" "timestamp" "bpm" "source" "raw_json"])

(def schema
  {:id [:text :primary-key]
   :date [:date]
   :timestamp [:timestamp]
   :bpm :integer
   :source :text
   :raw_json :jsonb
   :created_at [:timestamp :default "CURRENT_TIMESTAMP"]})

(defn extract-values [heart-rate]
  [(:id heart-rate)
   (:date heart-rate)
   (:timestamp heart-rate)
   (:bpm heart-rate)
   (:source heart-rate)
   (:raw_json heart-rate)]) 
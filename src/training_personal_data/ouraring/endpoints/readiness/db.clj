(ns training-personal-data.ouraring.endpoints.readiness.db
  (:require [training-personal-data.ouraring.db :as db]
            [pod.babashka.postgresql :as pg]))

(def table-name "ouraring_daily_readiness")

(def columns
  ["id" "date" "score" "temperature_trend" "temperature_deviation"
   "contributors_json" "raw_json"])

(def schema
  {:id [:text :primary-key]
   :date [:date]
   :score :integer
   :temperature_trend ["double precision"]
   :temperature_deviation ["double precision"]
   :contributors_json :text
   :raw_json :jsonb
   :timestamp [:timestamp :default "CURRENT_TIMESTAMP"]})

(defn extract-values [readiness]
  [(:id readiness)
   (:date readiness)
   (:score readiness)
   (:temperature_trend readiness)
   (:temperature_deviation readiness)
   (:contributors_json readiness)
   (:raw_json readiness)]) 
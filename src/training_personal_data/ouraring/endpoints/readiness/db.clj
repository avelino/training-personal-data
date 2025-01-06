(ns training-personal-data.ouraring.endpoints.readiness.db
  (:require [training-personal-data.ouraring.db :as db]))

(def table-name "ouraring_daily_readiness")

(def columns
  ["score" "temperature_trend" "temperature_deviation"
   "contributors_json" "raw_json"])

(def schema
  {:date [:date :primary-key]
   :score :integer
   :temperature_trend ["double precision"]
   :temperature_deviation ["double precision"]
   :contributors_json :text
   :raw_json :text
   :timestamp [:timestamp :default "CURRENT_TIMESTAMP"]})

(defn extract-values [readiness]
  [(:score readiness)
   (:temperature_trend readiness)
   (:temperature_deviation readiness)
   (:contributors_json readiness)
   (:raw_json readiness)]) 
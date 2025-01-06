(ns training-personal-data.ouraring.endpoints.readiness.db
  (:require [training-personal-data.ouraring.db :as db]
            [pod.babashka.postgresql :as pg]))

(def table-name "ouraring_daily_readiness")

(def columns
  ["score" "temperature_trend" "temperature_deviation"
   "contributors_json" "raw_json"])

(def schema
  {:date [:date]
   :score :integer
   :temperature_trend ["double precision"]
   :temperature_deviation ["double precision"]
   :contributors_json :text
   :raw_json :text
   :timestamp [:timestamp :default "CURRENT_TIMESTAMP"]})

(defn record-exists? [db-spec date score]
  (-> (pg/execute! db-spec
                   [(str "SELECT EXISTS(SELECT 1 FROM " table-name 
                         " WHERE date = ?::date"
                         " AND score = ?) AS exists") 
                    date score])
      first
      :exists))

(defn extract-values [readiness]
  [(:score readiness)
   (:temperature_trend readiness)
   (:temperature_deviation readiness)
   (:contributors_json readiness)
   (:raw_json readiness)]) 
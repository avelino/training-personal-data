(ns training-personal-data.ouraring.endpoints.sleep.db
  (:require [training-personal-data.ouraring.db :as db]))

(def table-name "ouraring_daily_sleep")

(def columns
  ["id" "score" "deep_sleep" "efficiency" "latency" "rem_sleep"
   "restfulness" "timing" "total_sleep" "timestamp"
   "contributors_json" "raw_json"])

(def schema
  {:date [:date :primary-key]
   :id :text
   :score :integer
   :deep_sleep :integer
   :efficiency :integer
   :latency :integer
   :rem_sleep :integer
   :restfulness :integer
   :timing :integer
   :total_sleep :integer
   :timestamp :timestamp
   :contributors_json :text
   :raw_json :text})

(defn extract-values [sleep]
  [(:id sleep)
   (:score sleep)
   (:deep_sleep sleep)
   (:efficiency sleep)
   (:latency sleep)
   (:rem_sleep sleep)
   (:restfulness sleep)
   (:timing sleep)
   (:total_sleep sleep)
   (:timestamp sleep)
   (:contributors_json sleep)
   (:raw_json sleep)])
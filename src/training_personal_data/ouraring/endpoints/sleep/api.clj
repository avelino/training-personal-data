(ns training-personal-data.ouraring.endpoints.sleep.api
  (:require [training-personal-data.ouraring.api :as api]
            [cheshire.core :as json]))

(def ^:private endpoint "/daily_sleep")

(defn fetch [token start-date end-date]
  (api/fetch-data token endpoint start-date end-date))

(defn normalize [sleep]
  {:date (:day sleep)
   :id (:id sleep)
   :score (:score sleep)
   :deep_sleep (get-in sleep [:contributors :deep_sleep])
   :efficiency (get-in sleep [:contributors :efficiency])
   :latency (get-in sleep [:contributors :latency])
   :rem_sleep (get-in sleep [:contributors :rem_sleep])
   :restfulness (get-in sleep [:contributors :restfulness])
   :timing (get-in sleep [:contributors :timing])
   :total_sleep (get-in sleep [:contributors :total_sleep])
   :timestamp (:timestamp sleep)
   :contributors_json (json/generate-string (:contributors sleep))
   :raw_json (json/generate-string sleep)}) 
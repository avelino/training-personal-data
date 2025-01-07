(ns training-personal-data.ouraring.endpoints.sleep.api
  (:require [training-personal-data.ouraring.api :as api]
            [cheshire.core :as json]))

(def ^:private endpoint "/daily_sleep")

(defn fetch [token start-date end-date]
  (api/fetch-data token endpoint start-date end-date))

(defn normalize [sleep]
  {:id (:id sleep)
   :date (str (:day sleep))
   :score (:score sleep)
   :deep_sleep (:deep_sleep sleep)
   :efficiency (:efficiency sleep)
   :latency (:latency sleep)
   :rem_sleep (:rem_sleep sleep)
   :restfulness (:restfulness sleep)
   :timing (:timing sleep)
   :total_sleep (:total_sleep sleep)
   :timestamp (:timestamp sleep)
   :contributors_json (json/generate-string (:contributors sleep))
   :raw_json (json/generate-string sleep)}) 
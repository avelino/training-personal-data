(ns training-personal-data.ouraring.endpoints.sleep.api
  (:require [training-personal-data.ouraring.api :as api]
            [cheshire.core :as json]))

(def ^:private endpoint "/daily_sleep")

(defn fetch [token start-date end-date]
  (api/fetch-data token endpoint start-date end-date))

(defn normalize [sleep]
  (let [contributors (:contributors sleep)]
    {:id (:id sleep)
     :date (:day sleep)
     :score (:score sleep)
     :deep_sleep (:deep_sleep contributors)
     :efficiency (:efficiency contributors)
     :latency (:latency contributors)
     :rem_sleep (:rem_sleep contributors)
     :restfulness (:restfulness contributors)
     :timing (:timing contributors)
     :total_sleep (:total_sleep contributors)
     :timestamp (:timestamp sleep)
     :contributors_json (json/generate-string contributors)
     :raw_json (json/generate-string sleep)})) 
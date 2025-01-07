(ns training-personal-data.ouraring.endpoints.readiness.api
  (:require [training-personal-data.ouraring.api :as api]
            [cheshire.core :as json]))

(def ^:private endpoint "/daily_readiness")

(defn fetch [token start-date end-date]
  (api/fetch-data token endpoint start-date end-date))

(defn normalize [readiness]
  {:id (:id readiness)
   :date (str (:day readiness))
   :score (:score readiness)
   :temperature_trend (:temperature_trend readiness)
   :temperature_deviation (:temperature_deviation readiness)
   :contributors_json (json/generate-string (:contributors readiness))
   :raw_json (json/generate-string readiness)}) 
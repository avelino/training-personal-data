(ns training-personal-data.ouraring.endpoints.heart-rate.api
  (:require [training-personal-data.ouraring.api :as api]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def ^:private endpoint "/heartrate")

(defn fetch [token start-date end-date]
  (api/fetch-data token endpoint start-date end-date))

(defn normalize [heart-rate]
  (let [timestamp (:timestamp heart-rate)
        date (first (str/split timestamp #"T"))]
    {:date date
     :timestamp timestamp
     :bpm (:bpm heart-rate)
     :source (:source heart-rate)
     :raw_json (json/generate-string heart-rate)})) 
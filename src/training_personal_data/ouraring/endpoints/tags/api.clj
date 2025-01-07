(ns training-personal-data.ouraring.endpoints.tags.api
  (:require [training-personal-data.ouraring.api :as api]
            [cheshire.core :as json]))

(def ^:private endpoint "/tag")

(defn fetch [token start-date end-date]
  (api/fetch-data token endpoint start-date end-date))

(defn normalize [tag]
  {:id (:id tag)
   :date (str (:day tag))
   :text (:text tag)
   :tags (:tags tag)
   :timestamp (:timestamp tag)
   :raw_json (json/generate-string tag)}) 
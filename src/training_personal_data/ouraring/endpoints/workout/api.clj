(ns training-personal-data.ouraring.endpoints.workout.api
  (:require [training-personal-data.ouraring.api :as api]
            [cheshire.core :as json]))

(def ^:private endpoint "/workout")

(defn fetch [token start-date end-date]
  (api/fetch-data token endpoint start-date end-date))

(defn normalize [workout]
  {:id (:id workout)
   :date (str (:day workout))
   :activity (:activity workout)
   :calories (:calories workout)
   :day_id (:day_id workout)
   :distance (:distance workout)
   :end_datetime (:end_datetime workout)
   :intensity (:intensity workout)
   :label (:label workout)
   :source (:source workout)
   :start_datetime (:start_datetime workout)
   :raw_json (json/generate-string workout)}) 
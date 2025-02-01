(ns training-personal-data.wahoo.endpoints.workout.api
  (:require [training-personal-data.wahoo.api :as api]
            [cheshire.core :as json]))

(defn fetch [token workout-id]
  (api/fetch-workout token workout-id))

(defn normalize [workout]
  {:id (:id workout)
   :starts (:starts workout)
   :minutes (:minutes workout)
   :name (:name workout)
   :plan_id (:plan_id workout)
   :workout_token (:workout_token workout)
   :workout_type_id (:workout_type_id workout)
   :workout_summary (:workout_summary workout)
   :created_at (:created_at workout)
   :updated_at (:updated_at workout)
   :raw_json (json/generate-string workout)})
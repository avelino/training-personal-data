(ns training-personal-data.wahoo.endpoints.workout.api
  (:require [training-personal-data.wahoo.api :as api]
            [cheshire.core :as json]))

(defn fetch [token start-date end-date]
  (let [resp (api/fetch-workouts-by-date token start-date end-date)]
    (if (:success? resp)
      (let [data (:data resp)
            workouts (cond
                       (map? data) (or (:workouts data)
                                       (:data data)
                                       (when (:workout data) [(:workout data)])
                                       [])
                       (sequential? data) data
                       :else [])]
        {:success? true :data workouts})
      resp)))

(defn normalize [workout]
  (let [id (or (:id workout) (:workout_id workout))
        plan-id (:plan_id workout)]
    {:id (some-> id str)
     :starts (:starts workout)
     :minutes (:minutes workout)
     :name (:name workout)
     :plan_id (when plan-id (str plan-id))
     :workout_token (:workout_token workout)
     :workout_type_id (:workout_type_id workout)
     :workout_summary (:workout_summary workout)
     :created_at (:created_at workout)
     :updated_at (:updated_at workout)
     :raw_json (json/generate-string workout)}))
(ns training-personal-data.wahoo
  (:require [training-personal-data.wahoo.endpoints.workout.core :as workout]
            [training-personal-data.db :as db]
            [training-personal-data.config :as config]))

(defn -main [& args]
  (let [token (first args)
        workout-id (second args)
        db-spec (db/make-db-spec (config/get-db-config))]
    (workout/fetch-and-save token workout-id db-spec)))
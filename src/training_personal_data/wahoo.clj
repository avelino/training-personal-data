(ns training-personal-data.wahoo
  (:require [training-personal-data.wahoo.endpoints.workout.core :as workout]
            [training-personal-data.db :as db]
            [training-personal-data.config :as config]))

(defn -main [& args]
  (let [token (System/getenv "WAHOO_TOKEN")
        start-date (first args)
        end-date (second args)
        db-spec (db/make-db-spec (config/get-db-config))]
    (workout/fetch-and-save-by-date token start-date end-date db-spec)))

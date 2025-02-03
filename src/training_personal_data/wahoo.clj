(ns training-personal-data.wahoo
  (:require [training-personal-data.wahoo.endpoints.workout.core :as workout]
            [training-personal-data.db :as db]
            [training-personal-data.config :as config]
            [taoensso.timbre :as log]))

(defn -main [& args]
  (try
    (let [token (System/getenv "WAHOO_TOKEN")
          start-date (first args)
          end-date (second args)
          db-spec (db/make-db-spec (config/get-db-config))]
      (when (or (nil? start-date) (nil? end-date))
        (throw (ex-info "Usage: bb -m training-personal-data.wahoo <start-date> <end-date>" {:args args})))
      (when (nil? token)
        (throw (ex-info "Missing WAHOO_TOKEN environment variable" {})))
      (log/info {:event :start :msg "Starting Wahoo workout sync" :start start-date :end end-date})
      (workout/fetch-and-save-by-date token start-date end-date db-spec)
      (log/info {:event :complete :msg "Completed Wahoo workout sync"}))
    (catch Exception e
      (log/error {:event :error :msg (ex-message e) :data (ex-data e)})
      (println "\n❌ Wahoo sync failed:" (ex-message e))
      (when-let [data (ex-data e)]
        (println "   Details:" data))
      (System/exit 1))))

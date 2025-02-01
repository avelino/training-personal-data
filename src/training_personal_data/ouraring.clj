(ns training-personal-data.ouraring
  (:require [training-personal-data.config :as config]
            [training-personal-data.ouraring.endpoints.activity.core :as activity]
            [training-personal-data.ouraring.endpoints.sleep.core :as sleep]
            [training-personal-data.ouraring.endpoints.readiness.core :as readiness]
            [training-personal-data.ouraring.endpoints.heart-rate.core :as heart-rate]
            [training-personal-data.ouraring.endpoints.workout.core :as workout]
            [training-personal-data.ouraring.endpoints.tags.core :as tags]
            [training-personal-data.db :as db]
            [taoensso.timbre :as log]))

(defn validate-env []
  (let [required-vars ["OURA_TOKEN" "SUPABASE_DB_NAME" "SUPABASE_HOST"
                       "SUPABASE_USER" "SUPABASE_PASSWORD"]
        missing-vars (filter #(nil? (System/getenv %)) required-vars)]
    (when (seq missing-vars)
      (throw (ex-info "Missing required environment variables"
                      {:missing missing-vars})))))

(defn validate-dates [start-date end-date]
  (when (or (nil? start-date) (nil? end-date))
    (throw (ex-info "Both start-date and end-date are required"
                    {:usage "bb -m training-personal-data.ouraring <start-date> <end-date>"}))))

(defn process-endpoint
  "Processes a single Oura Ring API endpoint asynchronously.
  Takes a tuple of [endpoint-name endpoint-fn], authentication token, date range, and database spec.
  Returns a future that will:
  1. Log the start of sync for the endpoint
  2. Execute the endpoint function with provided parameters
  3. Log successful completion
  4. Catch and log any errors before re-throwing

  The endpoint function is expected to handle fetching and saving data for a specific Oura Ring metric."
  [[endpoint-name endpoint-fn] token start-date end-date db-spec]
  (future
    (log/info {:event :sync-start :endpoint endpoint-name})
    (try
      (endpoint-fn token start-date end-date db-spec)
      (log/info {:event :sync-complete :endpoint endpoint-name})
      (catch Exception e
        (log/error {:event :sync-error
                    :endpoint endpoint-name
                    :error (ex-message e)})
        (throw e)))))

(defn -main [& args]
  (try
    (log/info {:event :start :msg "Starting Oura Ring data sync"})
    (validate-env)
    (let [start-date (first args)
          end-date (second args)]
      (validate-dates start-date end-date)
      (log/info {:event :processing :msg "Processing data" :start start-date :end end-date})

      (let [token (System/getenv "OURA_TOKEN")
            db-spec (db/make-db-spec (config/get-db-config))
            endpoints {:activity activity/fetch-and-save
                       :sleep sleep/fetch-and-save
                       :readiness readiness/fetch-and-save
                       ;; :heart-rate heart-rate/fetch-and-save
                       :workout workout/fetch-and-save
                       :tags tags/fetch-and-save}]
        (->> endpoints
             (map #(process-endpoint % token start-date end-date db-spec))
             (doall)  ; Start all futures
             (map deref)  ; Wait for all to complete
             (doall))
        (log/info {:event :complete :msg "Successfully completed Oura Ring data sync"})))
    (catch Exception e
      (log/error {:event :error :msg (ex-message e) :data (ex-data e)})
      (System/exit 1))))
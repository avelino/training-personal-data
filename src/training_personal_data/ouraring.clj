(ns training-personal-data.ouraring
  (:require [training-personal-data.ouraring.endpoints.activity.core :as activity]
            [training-personal-data.ouraring.endpoints.sleep.core :as sleep]
            [training-personal-data.ouraring.endpoints.readiness.core :as readiness]
            [training-personal-data.ouraring.endpoints.heart-rate.core :as heart-rate]
            [training-personal-data.ouraring.endpoints.workout.core :as workout]
            [training-personal-data.ouraring.db :as db]
            [taoensso.timbre :as log]))

(defn get-db-config []
  (let [config {:dbname (System/getenv "SUPABASE_DB_NAME")
                :host (System/getenv "SUPABASE_HOST")
                :port 5432
                :user (System/getenv "SUPABASE_USER")
                :password (System/getenv "SUPABASE_PASSWORD")
                :sslmode "require"}]
    (when (some nil? ((juxt :dbname :host :user :password) config))
      (throw (ex-info "Missing required database configuration" 
                     {:config (-> config 
                                 (dissoc :password)
                                 (assoc :password "[REDACTED]"))})))
    config))

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
            db-spec (db/make-db-spec (get-db-config))
            endpoints {:activity activity/fetch-and-save
                      :sleep sleep/fetch-and-save
                      :readiness readiness/fetch-and-save
                      :heart-rate heart-rate/fetch-and-save
                      :workout workout/fetch-and-save}]
        (->> endpoints
             (map #(process-endpoint % token start-date end-date db-spec))
             (doall)  ; Start all futures
             (map deref)  ; Wait for all to complete
             (doall))
        (log/info {:event :complete :msg "Successfully completed Oura Ring data sync"})))
    (catch Exception e
      (log/error {:event :error :msg (ex-message e) :data (ex-data e)})
      (System/exit 1))))
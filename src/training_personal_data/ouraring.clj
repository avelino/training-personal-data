(ns training-personal-data.ouraring
  "Main namespace for Oura Ring data processing"
  (:require [training-personal-data.ouraring.api :as api]
            [training-personal-data.ouraring.db :as db]
            [clojure.java.io :as io]))

(defn save-to-json
  "Saves data to a JSON file.
   Parameters:
   - data: Raw data to save
   - start-date: Date string used in filename (YYYY-MM-DD)
   Returns:
   - Filename where data was saved"
  [data start-date]
  (let [filename (format "ouraring-%s.json" start-date)]
    (spit filename data)
    filename))

(defn get-db-config
  "Creates database configuration from environment variables.
   Returns:
   - Map with database configuration using environment variables or defaults"
  []
  {:dbname (or (System/getenv "SUPABASE_DB_NAME") "postgres")
   :host (or (System/getenv "SUPABASE_HOST") "127.0.0.1")
   :port (parse-long (or (System/getenv "SUPABASE_PORT") "5432"))
   :user (or (System/getenv "SUPABASE_USER") "postgres")
   :password (or (System/getenv "SUPABASE_PASSWORD") "postgres")
   :sslmode "require"})

(defn process-activity
  "Processes and saves activity data.
   Parameters:
   - db-spec: Database connection specification
   - data: Raw activity data from API
   Returns:
   - Normalized activity data"
  [db-spec data]
  (let [normalized (api/normalize-activity data)]
    (db/save-activity db-spec normalized)
    normalized))

(defn validate-env
  "Validates required environment variables are set.
   Throws:
   - ExceptionInfo if any required variables are missing"
  []
  (let [required-vars ["OURA_TOKEN" "SUPABASE_HOST" 
                      "SUPABASE_USER" "SUPABASE_PASSWORD"]
        missing-vars (filter #(nil? (System/getenv %)) required-vars)]
    (when (seq missing-vars)
      (throw (ex-info "Missing required environment variables" 
                     {:missing missing-vars})))))

(defn validate-dates
  "Validates start and end dates are provided.
   Parameters:
   - start-date: Start date string
   - end-date: End date string
   Throws:
   - ExceptionInfo if either date is missing"
  [start-date end-date]
  (when (or (nil? start-date) (nil? end-date))
    (throw (ex-info "Both start-date and end-date are required"
                   {:usage "bb -m training-personal-data.ouraring <start-date> <end-date>"}))))

(defn -main
  "Main entry point for the application.
   Fetches Oura Ring data and saves it to both file and database.
   
   Usage:
   bb -m training-personal-data.ouraring <start-date> <end-date>
   
   Environment Variables:
   - OURA_TOKEN: Oura Ring API token
   - SUPABASE_*: Database connection details
   
   Arguments:
   - start-date: Start date in YYYY-MM-DD format
   - end-date: End date in YYYY-MM-DD format"
  [& args]
  (try
    (validate-env)
    (let [start-date (first args)
          end-date (second args)]
      (validate-dates start-date end-date)
      
      (let [token (System/getenv "OURA_TOKEN")
            db-spec (db/make-db-spec (get-db-config))]
        
        ;; Ensure database table exists
        (db/ensure-table db-spec)
        
        ;; Fetch and process data
        (let [{:keys [success? data error]} (api/fetch-daily-activity token start-date end-date)]
          (if success?
            (let [save-file-future (future (save-to-json data start-date))
                  save-db-future (future (process-activity db-spec data))]
              ;; Aguarda a conclusão dos futures
              (println "Saving to file...")
              @save-file-future
              (println "Saving to database...")
              @save-db-future
              (println "All operations completed!"))
            (throw (ex-info "Failed to fetch data" error))))))
    (catch Exception e
      (println "Error:" (ex-message e))
      (when-let [data (ex-data e)]
        (println "Details:" data))
      (System/exit 1))))
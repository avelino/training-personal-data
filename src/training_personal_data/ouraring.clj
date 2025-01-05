(ns training-personal-data.ouraring
  (:require [training-personal-data.ouraring.endpoints.activity.core :as activity]
            [training-personal-data.ouraring.endpoints.sleep.core :as sleep]
            [training-personal-data.ouraring.db :as db]))

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

(defn -main [& args]
  (try
    (validate-env)
    (let [start-date (first args)
          end-date (second args)]
      (validate-dates start-date end-date)
      
      (let [token (System/getenv "OURA_TOKEN")
            db-spec (db/make-db-spec (get-db-config))]
        
        ;; Fetch and save activity data
        (activity/fetch-and-save token start-date end-date db-spec)
        
        ;; Fetch and save sleep data
        (sleep/fetch-and-save token start-date end-date db-spec)))
    (catch Exception e
      (println "Error:" (ex-message e))
      (when-let [data (ex-data e)]
        (println "Details:" data))
      (System/exit 1))))
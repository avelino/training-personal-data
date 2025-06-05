(ns training-personal-data.ouraring
  "Oura Ring data synchronization using the refactored generic pipeline.

   This system eliminates code duplication by using a unified pipeline
   that handles fetch → transform → save for all endpoints."
  (:require [training-personal-data.config :as config]
            [training-personal-data.ouraring.config :as oura-config]
            [training-personal-data.core.pipeline :as pipeline]
            [training-personal-data.db :as db]
            [taoensso.timbre :as log]))

(defn validate-env []
  "Validates that required environment variables are present"
  (let [required-vars ["OURA_TOKEN" "SUPABASE_DB_NAME" "SUPABASE_HOST"
                       "SUPABASE_USER" "SUPABASE_PASSWORD"]
        missing-vars (filter #(nil? (System/getenv %)) required-vars)]
    (when (seq missing-vars)
      (throw (ex-info "Missing required environment variables"
                      {:missing missing-vars})))))

(defn validate-dates [start-date end-date]
  "Validates that both start and end dates are provided"
  (when (or (nil? start-date) (nil? end-date))
    (throw (ex-info "Both start-date and end-date are required"
                    {:usage "bb -m training-personal-data.ouraring <start-date> <end-date>"}))))

(defn process-endpoint-async
  "Processes a single endpoint asynchronously using the generic pipeline.
   Returns a future that will complete when the endpoint processing is done."
  [[endpoint-key endpoint-config] token start-date end-date db-spec]
  (future
    (try
      (log/info {:event :endpoint-start :endpoint endpoint-key})
      (pipeline/execute-pipeline endpoint-config token start-date end-date db-spec)
      (log/info {:event :endpoint-complete :endpoint endpoint-key})
      {:endpoint endpoint-key :status :success}
      (catch Exception e
        (log/error {:event :endpoint-error
                    :endpoint endpoint-key
                    :error (ex-message e)
                    :data (ex-data e)})
        {:endpoint endpoint-key :status :error :error (ex-message e)}))))

(defn process-endpoint-batch-async
  "Processes a single endpoint with batch processing for better performance.
   Useful for endpoints with large amounts of data."
  [[endpoint-key endpoint-config] token start-date end-date db-spec batch-size]
  (future
    (try
      (log/info {:event :endpoint-batch-start :endpoint endpoint-key :batch-size batch-size})
      (pipeline/batch-execute-pipeline endpoint-config token start-date end-date db-spec
                                       :batch-size batch-size)
      (log/info {:event :endpoint-batch-complete :endpoint endpoint-key})
      {:endpoint endpoint-key :status :success :mode :batch}
      (catch Exception e
        (log/error {:event :endpoint-batch-error
                    :endpoint endpoint-key
                    :error (ex-message e)
                    :data (ex-data e)})
        {:endpoint endpoint-key :status :error :mode :batch :error (ex-message e)}))))

(defn sync-all-endpoints
  "Synchronizes all enabled Oura Ring endpoints in parallel.

   Options:
   - :batch-size - Process records in batches (default: no batching)
   - :endpoints - Specific endpoints to sync (default: all enabled)
   - :parallel? - Whether to process in parallel (default: true)"
  [token start-date end-date db-spec & {:keys [batch-size endpoints parallel?]
                                        :or {parallel? true
                                             endpoints (oura-config/get-enabled-endpoint-configs)}}]
  (let [process-fn (if batch-size
                     #(process-endpoint-batch-async % token start-date end-date db-spec batch-size)
                     #(process-endpoint-async % token start-date end-date db-spec))]

    (log/info {:event :sync-start
               :endpoints (keys endpoints)
               :parallel parallel?
               :batch-size batch-size})

    (if parallel?
      ;; Parallel processing
      (let [futures (->> endpoints
                         (map process-fn)
                         (doall))
            results (->> futures
                         (map deref)
                         (doall))]
        (log/info {:event :sync-complete :results results})
        results)

      ;; Sequential processing
      (let [results (->> endpoints
                         (map (fn [endpoint-entry]
                                @(process-fn endpoint-entry)))
                         (doall))]
        (log/info {:event :sync-complete :results results})
        results))))

(defn sync-single-endpoint
  "Synchronizes a specific endpoint. Useful for testing or targeted syncing."
  [endpoint-key token start-date end-date db-spec & {:keys [batch-size]}]
  (let [endpoint-config (oura-config/get-endpoint-config endpoint-key)]
    (when-not endpoint-config
      (throw (ex-info "Unknown endpoint" {:endpoint endpoint-key
                                          :available (keys (oura-config/get-all-endpoint-configs))})))

    (if batch-size
      (pipeline/batch-execute-pipeline endpoint-config token start-date end-date db-spec
                                       :batch-size batch-size)
      (pipeline/execute-pipeline endpoint-config token start-date end-date db-spec))))

(defn get-sync-summary
  "Returns a summary of available endpoints and their configuration"
  []
  (let [all-configs (oura-config/get-all-endpoint-configs)
        enabled-configs (oura-config/get-enabled-endpoint-configs)]
    {:total-endpoints (count all-configs)
     :enabled-endpoints (count enabled-configs)
     :disabled-endpoints (- (count all-configs) (count enabled-configs))
     :available-endpoints (keys all-configs)
     :enabled-endpoints-list (keys enabled-configs)
     :disabled-endpoints-list (keys (apply dissoc all-configs (keys enabled-configs)))}))

(defn -main
  "Main entry point for Oura Ring synchronization using the refactored pipeline.

   Usage:
   bb -m training-personal-data.ouraring <start-date> <end-date>
   bb -m training-personal-data.ouraring <start-date> <end-date> --batch-size 50
   bb -m training-personal-data.ouraring <start-date> <end-date> --endpoint activity
   bb -m training-personal-data.ouraring --summary"
  [& args]
  (try
    (log/info {:event :start :msg "Starting Oura Ring data sync"})

    ;; Handle summary command
    (when (= (first args) "--summary")
      (let [summary (get-sync-summary)]
        (println "\n📊 Oura Ring Sync Summary")
        (println "========================")
        (println "Total endpoints:" (:total-endpoints summary))
        (println "Enabled endpoints:" (:enabled-endpoints-list summary))
        (println "Disabled endpoints:" (:disabled-endpoints-list summary))
        (System/exit 0)))

    (validate-env)

    (let [start-date (first args)
          end-date (second args)
          options (apply hash-map (drop 2 args))]

      (validate-dates start-date end-date)
      (log/info {:event :processing
                 :msg "Processing data with unified pipeline"
                 :start start-date
                 :end end-date
                 :options options})

      (let [token (System/getenv "OURA_TOKEN")
            db-spec (db/make-db-spec (config/get-db-config))
            batch-size (when-let [bs (:batch-size options)]
                         (Integer/parseInt bs))
            specific-endpoint (when-let [ep (:endpoint options)]
                                (keyword ep))]

        ;; Test database connection
        (when-not (db/test-connection db-spec)
          (throw (ex-info "Failed to connect to database" {})))

        (cond
          ;; Sync specific endpoint
          specific-endpoint
          (do
            (log/info {:event :single-endpoint-sync :endpoint specific-endpoint})
            (sync-single-endpoint specific-endpoint token start-date end-date db-spec
                                  :batch-size batch-size)
            (log/info {:event :single-endpoint-complete :endpoint specific-endpoint}))

          ;; Sync all endpoints
          :else
          (let [results (sync-all-endpoints token start-date end-date db-spec
                                            :batch-size batch-size)]
            (let [successful (filter #(= (:status %) :success) results)
                  failed (filter #(= (:status %) :error) results)]
              (log/info {:event :sync-summary
                         :total (count results)
                         :successful (count successful)
                         :failed (count failed)
                         :failed-endpoints (map :endpoint failed)})

              (when (seq failed)
                (println "\n⚠️  Some endpoints failed:")
                (doseq [failure failed]
                  (println "  -" (:endpoint failure) ":" (:error failure))))))))

      (log/info {:event :complete :msg "Successfully completed Oura Ring data sync"}))

    (catch Exception e
      (log/error {:event :error :msg (ex-message e) :data (ex-data e)})
      (println "\n❌ Sync failed:" (ex-message e))
      (when-let [data (ex-data e)]
        (println "   Details:" data))
      (System/exit 1))))

(comment
  ;; Usage examples:

  ;; Sync all endpoints for a date range
  (-main "2024-01-01" "2024-01-07")

  ;; Sync with batch processing
  (-main "2024-01-01" "2024-01-31" "--batch-size" "25")

  ;; Sync specific endpoint
  (-main "2024-01-01" "2024-01-07" "--endpoint" "activity")

  ;; Get summary of available endpoints
  (-main "--summary")

  ;; Programmatic usage:

  ;; Sync all endpoints
  (let [token (System/getenv "OURA_TOKEN")
        db-spec (db/make-db-spec (config/get-db-config))]
    (sync-all-endpoints token "2024-01-01" "2024-01-07" db-spec))

  ;; Sync with batching
  (let [token (System/getenv "OURA_TOKEN")
        db-spec (db/make-db-spec (config/get-db-config))]
    (sync-all-endpoints token "2024-01-01" "2024-01-31" db-spec :batch-size 50))

  ;; Sync specific endpoint
  (let [token (System/getenv "OURA_TOKEN")
        db-spec (db/make-db-spec (config/get-db-config))]
    (sync-single-endpoint :activity token "2024-01-01" "2024-01-07" db-spec))

  ;; Get available endpoints
  (get-sync-summary))

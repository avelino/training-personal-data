(ns training-personal-data.examples.pipeline-demo
  "Demonstration of the refactored pipeline system.
   Shows how the generic pipeline eliminates code(ns training-personal-data.examples.pipeline-demo) repetition."
  (:require [training-personal-data.ouraring.config :as oura-config]
            [training-personal-data.core.pipeline :as pipeline]
            [training-personal-data.config :as config]
            [training-personal-data.db :as db]
            [taoensso.timbre :as log]))

(defn demo-single-endpoint
  "Demonstrates using the pipeline for a single endpoint"
  []
  (println "\n=== Single Endpoint Demo ===")
  (let [token (System/getenv "OURA_TOKEN")
        db-spec (db/make-db-spec (config/get-db-config))
        activity-config (oura-config/get-endpoint-config :activity)]

    (println "Processing activity data using generic pipeline...")

    ;; Before: Required 20+ lines of repetitive code in each endpoint
    ;; After: Single pipeline call
    (pipeline/execute-pipeline
     activity-config
     token
     "2024-01-01"
     "2024-01-07"
     db-spec)

    (println "✅ Activity data processed successfully!")))

(defn demo-batch-processing
  "Demonstrates batch processing for better performance"
  []
  (println "\n=== Batch Processing Demo ===")
  (let [token (System/getenv "OURA_TOKEN")
        db-spec (db/make-db-spec (config/get-db-config))
        sleep-config (oura-config/get-endpoint-config :sleep)]

    (println "Processing sleep data with batch size 25...")

    ;; Batch processing for better performance
    (pipeline/batch-execute-pipeline
     sleep-config
     token
     "2024-01-01"
     "2024-01-31"
     db-spec
     :batch-size 25)

    (println "✅ Sleep data processed in batches!")))

(defn demo-parallel-endpoints
  "Demonstrates parallel processing of multiple endpoints"
  []
  (println "\n=== Parallel Processing Demo ===")
  (let [token (System/getenv "OURA_TOKEN")
        db-spec (db/make-db-spec (config/get-db-config))
        endpoints (select-keys (oura-config/get-enabled-endpoint-configs)
                              [:activity :sleep :readiness])]

    (println "Processing multiple endpoints in parallel...")

    ;; Process all endpoints in parallel
    (->> endpoints
         (map (fn [[endpoint-key endpoint-config]]
                (future
                  (log/info {:event :demo-start :endpoint endpoint-key})
                  (pipeline/execute-pipeline
                   endpoint-config
                   token
                   "2024-01-01"
                   "2024-01-03"
                   db-spec)
                  (log/info {:event :demo-complete :endpoint endpoint-key}))))
         (doall)  ; Start all futures
         (map deref)  ; Wait for completion
         (doall))

    (println "✅ All endpoints processed in parallel!")))

(defn demo-custom-endpoint
  "Demonstrates creating a custom endpoint configuration"
  []
  (println "\n=== Custom Endpoint Demo ===")

  ;; Example of creating a custom endpoint configuration
  (let [custom-config (pipeline/create-endpoint-config
                       "custom-data"
                       "custom_table"
                       ["id" "name" "value" "timestamp"]
                       {:id [:text :primary-key]
                        :name :text
                        :value :integer
                        :timestamp [:timestamp :default "CURRENT_TIMESTAMP"]}
                       ;; Mock fetch function
                       (fn [_token _start _end]
                         {:success? true
                          :data [{:id "test-1" :name "Sample" :value 42}]})
                       ;; Mock normalize function
                       (fn [record]
                         (assoc record :normalized true))
                       ;; Mock extract values function
                       (fn [record]
                         [(:id record) (:name record) (:value record)]))]

    (println "Custom endpoint configuration created:")
    (println "- Name:" (:name custom-config))
    (println "- Table:" (:table-name custom-config))
    (println "- Columns:" (:columns custom-config))
    (println "✅ Custom configuration ready for use!")))

(defn demo-error-handling
  "Demonstrates error handling in the pipeline"
  []
  (println "\n=== Error Handling Demo ===")

  ;; Create a configuration that will fail
  (let [failing-config (pipeline/create-endpoint-config
                        "failing-endpoint"
                        "test_table"
                        ["id"]
                        {:id [:text :primary-key]}
                        ;; This will fail
                        (fn [_token _start _end]
                          {:success? false
                           :error "Simulated API failure"})
                        identity
                        (fn [record] [(:id record)]))]

    (try
      (pipeline/execute-pipeline
       failing-config
       "fake-token"
       "2024-01-01"
       "2024-01-02"
       {})
      (catch Exception e
        (println "❌ Expected error caught:")
        (println "  Message:" (ex-message e))
        (println "  Data:" (ex-data e))
        (println "✅ Error handling working correctly!"))))

(defn -main
  "Run all pipeline demonstrations"
  [& args]
  (println "🚀 Pipeline Refactoring Demo")
  (println "=============================")

  (try
    ;; Only run demos that don't require actual API calls
    (demo-custom-endpoint)
    (demo-error-handling)

    ;; These require valid environment variables
    (when (and (System/getenv "OURA_TOKEN")
               (System/getenv "SUPABASE_DB_NAME"))
      (demo-single-endpoint)
      (demo-batch-processing)
      (demo-parallel-endpoints))

    (println "\n🎉 All demos completed successfully!")

    (catch Exception e
      (log/error {:event :demo-error :error (ex-message e)})
      (println "\n❌ Demo failed:" (ex-message e)))))

(comment
  ;; Usage examples:

  ;; Run all demos
  (-main)

  ;; Run individual demos
  (demo-custom-endpoint)
  (demo-error-handling)

  ;; Compare old vs new approach:

  ;; OLD WAY (20+ lines per endpoint):
  ;; (defn fetch-and-save [token start-date end-date db-spec]
  ;;   (log/info {:event :activity-sync :action :start})
  ;;   (common-db/create-table db-spec db/table-name db/schema)
  ;;   (let [{:keys [success? data error]} (api/fetch token start-date end-date)]
  ;;     (if success?
  ;;       (do
  ;;         (log/info {:event :activity-sync :action :process :count (count data)})
  ;;         (doseq [activity data]
  ;;           (let [normalized (api/normalize activity)
  ;;                 values (db/extract-values normalized)]
  ;;             (common-db/save db-spec db/table-name db/columns normalized values)))
  ;;         (log/info {:event :activity-sync :action :complete}))
  ;;       (throw (ex-info "Failed to fetch activity data" error)))))

  ;; NEW WAY (3 lines):
  ;; (defn fetch-and-save [token start-date end-date db-spec]
  ;;   (pipeline/execute-pipeline activity-config token start-date end-date db-spec))
  )

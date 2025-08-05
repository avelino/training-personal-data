(ns training-personal-data.core.pipeline
  (:require [training-personal-data.db :as common-db]
            [training-personal-data.cache :as cache]
            [training-personal-data.resilience :as resilience]
            [taoensso.timbre :as log]))

(defn- log-event [event-type endpoint-name & [extra-data]]
  (log/info (merge {:event event-type :endpoint endpoint-name} extra-data)))

(defn- ensure-table-exists! [db-spec endpoint-config]
  (common-db/create-table
   db-spec
   (:table-name endpoint-config)
   (:schema endpoint-config)))

(defn- fetch-data [endpoint-config token start-date end-date]
  (log-event :fetch-start (:name endpoint-config))
  (let [cache-key (cache/oura-data-key (:name endpoint-config) (str start-date "-" end-date))
        fetch-fn (:fetch-fn endpoint-config)
        endpoint-name (:name endpoint-config)]
    
    ;; Try to get from cache first
    (if-let [cached-data (cache/get cache-key)]
      (do
        (log-event :fetch-cache-hit endpoint-name {:count (count cached-data)})
        cached-data)
      ;; Not in cache, fetch with resilience patterns
      (let [result (resilience/with-resilience 
                     (str "fetch-" endpoint-name)
                     (fn [] (fetch-fn token start-date end-date))
                     :circuit-name (str "oura-api-" endpoint-name)
                     :rate-limiter-name "oura-api")]
        (if (:success? result)
          (let [data (:data result)]
            (log-event :fetch-success endpoint-name {:count (count data)})
            ;; Cache the successful result
            (cache/put! cache-key data :ttl-minutes 30)
            data)
          (throw (ex-info (str "Failed to fetch " endpoint-name " data")
                          {:endpoint endpoint-name :error (:error result)})))))))

(defn- transform-record [endpoint-config record]
  (let [normalize-fn (:normalize-fn endpoint-config)]
    (normalize-fn record)))

(defn- extract-values [endpoint-config normalized-record]
  (let [extract-fn (:extract-values-fn endpoint-config)]
    (extract-fn normalized-record)))

(defn- save-record! [db-spec endpoint-config normalized-record]
  (let [values (extract-values endpoint-config normalized-record)]
    (resilience/with-retry 
      (str "save-" (:name endpoint-config))
      (fn []
        (common-db/save
         db-spec
         (:table-name endpoint-config)
         (:columns endpoint-config)
         normalized-record
         values))
      :config {:max-attempts 2
               :initial-delay-ms 500
               :backoff-multiplier 1.5})))

(defn- process-records [db-spec endpoint-config records]
  (log-event :process-start (:name endpoint-config) {:count (count records)})
  (doseq [record records]
    (let [normalized (transform-record endpoint-config record)]
      (log/debug {:event :record-save
                  :endpoint (:name endpoint-config)
                  :id (:id normalized)})
      (save-record! db-spec endpoint-config normalized)))
  (log-event :process-complete (:name endpoint-config)))

(defn execute-pipeline
  "Generic pipeline that executes: fetch → transform → save
   Now includes caching and resilience patterns

   endpoint-config should contain:
   - :name           - endpoint name for logging
   - :table-name     - database table name
   - :columns        - vector of column names
   - :schema         - database schema map
   - :fetch-fn       - function to fetch data (token, start-date, end-date) -> {:success? bool :data [] :error}
   - :normalize-fn   - function to normalize a single record
   - :extract-values-fn - function to extract values from normalized record for DB"
  [endpoint-config token start-date end-date db-spec & {:keys [use-cache use-resilience]
                                                        :or {use-cache true use-resilience true}}]
  (try
    (log-event :pipeline-start (:name endpoint-config))

    ;; Ensure table exists with retry
    (resilience/with-retry 
      (str "create-table-" (:name endpoint-config))
      (fn [] (ensure-table-exists! db-spec endpoint-config))
      :config {:max-attempts 2 :initial-delay-ms 1000})

    ;; Execute pipeline: fetch → transform → save
    (-> (fetch-data endpoint-config token start-date end-date)
        (as-> data (process-records db-spec endpoint-config data)))

    (log-event :pipeline-complete (:name endpoint-config))

    (catch Exception e
      (log/error {:event :pipeline-error
                  :endpoint (:name endpoint-config)
                  :error (ex-message e)
                  :data (ex-data e)})
      
      ;; Invalidate cache on error to prevent serving stale data
      (let [cache-key (cache/oura-data-key (:name endpoint-config) (str start-date "-" end-date))]
        (cache/invalidate! cache-key))
      
      (throw e))))

(defn batch-execute-pipeline
  "Executes pipeline with batch processing for better performance.
   Processes records in batches instead of one by one.
   Now includes caching and resilience patterns."
  [endpoint-config token start-date end-date db-spec & {:keys [batch-size use-cache use-resilience] 
                                                        :or {batch-size 50 use-cache true use-resilience true}}]
  (try
    (log-event :batch-pipeline-start (:name endpoint-config) {:batch-size batch-size})

    ;; Ensure table exists with retry
    (resilience/with-retry 
      (str "create-table-" (:name endpoint-config))
      (fn [] (ensure-table-exists! db-spec endpoint-config))
      :config {:max-attempts 2 :initial-delay-ms 1000})

    ;; Fetch data (with caching and resilience)
    (let [raw-data (fetch-data endpoint-config token start-date end-date)
          batches (partition-all batch-size raw-data)]

      (doseq [batch batches]
        (log/debug {:event :batch-process
                    :endpoint (:name endpoint-config)
                    :batch-size (count batch)})
        
        ;; Process batch with resilience
        (resilience/with-retry 
          (str "process-batch-" (:name endpoint-config))
          (fn [] (process-records db-spec endpoint-config batch))
          :config {:max-attempts 2 :initial-delay-ms 500})))

    (log-event :batch-pipeline-complete (:name endpoint-config))

    (catch Exception e
      (log/error {:event :batch-pipeline-error
                  :endpoint (:name endpoint-config)
                  :error (ex-message e)
                  :data (ex-data e)})
      
      ;; Invalidate cache on error
      (let [cache-key (cache/oura-data-key (:name endpoint-config) (str start-date "-" end-date))]
        (cache/invalidate! cache-key))
      
      (throw e))))

(defn create-endpoint-config
  "Helper function to create endpoint configuration map"
  [name table-name columns schema fetch-fn normalize-fn extract-values-fn]
  {:name name
   :table-name table-name
   :columns columns
   :schema schema
   :fetch-fn fetch-fn
   :normalize-fn normalize-fn
   :extract-values-fn extract-values-fn})

;; New utility functions for monitoring and management
(defn get-pipeline-health
  "Returns health status of pipeline components"
  []
  {:cache (cache/get-stats)
   :resilience (resilience/get-resilience-stats)
   :database (common-db/get-pool-stats)
   :timestamp (System/currentTimeMillis)})

(defn warm-pipeline-cache!
  "Pre-warms cache with recent data for all endpoints"
  [endpoints token db-spec & {:keys [days-back] :or {days-back 7}}]
  (let [end-date (java.time.LocalDate/now)
        start-date (.minusDays end-date days-back)
        date-range (str start-date "-" end-date)]
    
    (log/info {:event :pipeline-cache-warming :endpoints (count endpoints) :date-range date-range})
    
    (doseq [[endpoint-name endpoint-config] endpoints]
      (try
        (let [cache-key (cache/oura-data-key endpoint-name date-range)]
          (cache/get-or-compute cache-key
                               (fn []
                                 (log/debug {:event :cache-warming :endpoint endpoint-name})
                                 (let [fetch-fn (:fetch-fn endpoint-config)
                                       result (fetch-fn token (str start-date) (str end-date))]
                                   (if (:success? result)
                                     (:data result)
                                     [])))
                               :ttl-minutes 60))
        (catch Exception e
          (log/warn {:event :cache-warm-error :endpoint endpoint-name :error (ex-message e)}))))
    
    (log/info {:event :pipeline-cache-warmed :final-cache-size (:current-size (cache/get-stats))})))

(ns training-personal-data.core.pipeline
  (:require [training-personal-data.db :as common-db]
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
  (let [fetch-fn (:fetch-fn endpoint-config)
        result (fetch-fn token start-date end-date)]
    (if (:success? result)
      (do
        (log-event :fetch-success (:name endpoint-config) {:count (count (:data result))})
        (:data result))
      (throw (ex-info (str "Failed to fetch " (:name endpoint-config) " data")
                      {:endpoint (:name endpoint-config) :error (:error result)})))))

(defn- transform-record [endpoint-config record]
  (let [normalize-fn (:normalize-fn endpoint-config)]
    (normalize-fn record)))

(defn- extract-values [endpoint-config normalized-record]
  (let [extract-fn (:extract-values-fn endpoint-config)]
    (extract-fn normalized-record)))

(defn- save-record! [db-spec endpoint-config normalized-record]
  (let [values (extract-values endpoint-config normalized-record)]
    (common-db/save
     db-spec
     (:table-name endpoint-config)
     (:columns endpoint-config)
     normalized-record
     values)))

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

   endpoint-config should contain:
   - :name           - endpoint name for logging
   - :table-name     - database table name
   - :columns        - vector of column names
   - :schema         - database schema map
   - :fetch-fn       - function to fetch data (token, start-date, end-date) -> {:success? bool :data [] :error}
   - :normalize-fn   - function to normalize a single record
   - :extract-values-fn - function to extract values from normalized record for DB"
  [endpoint-config token start-date end-date db-spec]
  (try
    (log-event :pipeline-start (:name endpoint-config))

    ;; Ensure table exists
    (ensure-table-exists! db-spec endpoint-config)

    ;; Execute pipeline: fetch → transform → save
    (-> (fetch-data endpoint-config token start-date end-date)
        (as-> data (process-records db-spec endpoint-config data)))

    (log-event :pipeline-complete (:name endpoint-config))

    (catch Exception e
      (log/error {:event :pipeline-error
                  :endpoint (:name endpoint-config)
                  :error (ex-message e)
                  :data (ex-data e)})
      (throw e))))

(defn batch-execute-pipeline
  "Executes pipeline with batch processing for better performance.
   Processes records in batches instead of one by one."
  [endpoint-config token start-date end-date db-spec & {:keys [batch-size] :or {batch-size 50}}]
  (try
    (log-event :batch-pipeline-start (:name endpoint-config) {:batch-size batch-size})

    ;; Ensure table exists
    (ensure-table-exists! db-spec endpoint-config)

    ;; Fetch data
    (let [raw-data (fetch-data endpoint-config token start-date end-date)
          batches (partition-all batch-size raw-data)]

      (doseq [batch batches]
        (log/debug {:event :batch-process
                    :endpoint (:name endpoint-config)
                    :batch-size (count batch)})
        (process-records db-spec endpoint-config batch)))

    (log-event :batch-pipeline-complete (:name endpoint-config))

    (catch Exception e
      (log/error {:event :batch-pipeline-error
                  :endpoint (:name endpoint-config)
                  :error (ex-message e)
                  :data (ex-data e)})
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

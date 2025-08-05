(ns training-personal-data.core.pipeline
  (:require [training-personal-data.db :as common-db]
            [taoensso.timbre :as log]))

(defn- log-event [event endpoint & [data]]
  (log/info {:event event :endpoint endpoint}))

(defn- fetch-data [endpoint-config token start-date end-date]
  (log-event :fetch-start (:name endpoint-config))
  (let [fetch-fn (:fetch-fn endpoint-config)]
    (try
      (let [result (fetch-fn token start-date end-date)]
        (if (:success? result)
          (do
            (log-event :fetch-success (:name endpoint-config) {:count (count (:data result))})
            (:data result))
          (throw (ex-info "Fetch failed" {:endpoint (:name endpoint-config) :error (:error result)}))))
      (catch Exception e
        (log/error {:event :fetch-error :endpoint (:name endpoint-config) :error (ex-message e)})
        (throw e)))))

(defn- save-record [endpoint-config record]
  (let [table-name (:table-name endpoint-config)
        normalize-fn (:normalize-fn endpoint-config)
        extract-values-fn (:extract-values-fn endpoint-config)
        schema (:schema endpoint-config)]
    
    (try
      ;; Create table if not exists
      (common-db/create-table table-name schema)
      
      ;; Normalize and extract values
      (let [normalized-record (normalize-fn record)
            values (extract-values-fn normalized-record)]
        
        ;; Save to database
        (common-db/save table-name (:columns endpoint-config) values)
        
        (log/debug {:event :record-save :endpoint (:name endpoint-config) :id (str (first values))})
        normalized-record)
      (catch Exception e
        (log/error {:event :save-error :endpoint (:name endpoint-config) :error (ex-message e)})
        (throw e)))))

(defn- process-data [endpoint-config data]
  (log-event :process-start (:name endpoint-config) {:count (count data)})
  (try
    (let [processed-data (doall (map #(save-record endpoint-config %) data))]
      (log-event :process-complete (:name endpoint-config))
      processed-data)
    (catch Exception e
      (log/error {:event :process-error :endpoint (:name endpoint-config) :error (ex-message e)})
      (throw e))))

(defn execute-pipeline [endpoint-config token start-date end-date & {:keys [mock] :or {mock false}}]
  (log-event :pipeline-start (:name endpoint-config))
  (try
    (let [data (fetch-data endpoint-config token start-date end-date)
          processed-data (process-data endpoint-config data)]
      (log-event :pipeline-complete (:name endpoint-config))
      processed-data)
    (catch Exception e
      (log/error {:event :pipeline-error :endpoint (:name endpoint-config) :error (ex-message e) :data (ex-data e)})
      (when-not mock
        (throw e)))))

(defn execute-batch-pipeline [endpoint-config token date-ranges & {:keys [batch-size] :or {batch-size 50}}]
  (log-event :batch-pipeline-start (:name endpoint-config) {:batch-size (count date-ranges)})
  (try
    (let [results (doall
                   (map (fn [date-range]
                          (log/debug {:event :batch-process :endpoint (:name endpoint-config) :batch-size 1})
                          (execute-pipeline endpoint-config token (:start date-range) (:end date-range)))
                        date-ranges))]
      (log-event :batch-pipeline-complete (:name endpoint-config))
      (flatten results))
    (catch Exception e
      (log/error {:event :batch-pipeline-error :endpoint (:name endpoint-config) :error (ex-message e)})
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

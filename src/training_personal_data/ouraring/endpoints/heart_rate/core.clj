(ns training-personal-data.ouraring.endpoints.heart-rate.core
  (:require [training-personal-data.ouraring.endpoints.heart-rate.api :as api]
            [training-personal-data.ouraring.endpoints.heart-rate.db :as db]
            [training-personal-data.core.pipeline :as pipeline]))

(def heart-rate-config
  "Heart rate endpoint configuration using the generic pipeline"
  (pipeline/create-endpoint-config
   "heart-rate"
   db/table-name
   db/columns
   db/schema
   api/fetch
   api/normalize
   db/extract-values))

(defn fetch-and-save
  "Refactored function using the generic pipeline.
   This replaces the old repetitive code with a single pipeline call."
  [token start-date end-date db-spec]
  (pipeline/execute-pipeline heart-rate-config token start-date end-date db-spec))

(defn fetch-and-save-batch
  "Batch version for better performance with large datasets"
  [token start-date end-date db-spec & {:keys [batch-size] :or {batch-size 50}}]
  (pipeline/batch-execute-pipeline heart-rate-config token start-date end-date db-spec
                                   :batch-size batch-size))

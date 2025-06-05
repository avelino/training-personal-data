(ns training-personal-data.ouraring.endpoints.readiness.core
  (:require [training-personal-data.ouraring.endpoints.readiness.api :as api]
            [training-personal-data.ouraring.endpoints.readiness.db :as db]
            [training-personal-data.core.pipeline :as pipeline]))

(def readiness-config
  "Readiness endpoint configuration using the generic pipeline"
  (pipeline/create-endpoint-config
   "readiness"
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
  (pipeline/execute-pipeline readiness-config token start-date end-date db-spec))

(defn fetch-and-save-batch
  "Batch version for better performance with large datasets"
  [token start-date end-date db-spec & {:keys [batch-size] :or {batch-size 50}}]
  (pipeline/batch-execute-pipeline readiness-config token start-date end-date db-spec
                                   :batch-size batch-size))

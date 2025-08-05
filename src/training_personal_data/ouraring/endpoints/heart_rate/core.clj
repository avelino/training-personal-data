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
  [token start-date end-date & {:keys [mock] :or {mock false}}]
  (pipeline/execute-pipeline heart-rate-config token start-date end-date :mock mock))


(ns training-personal-data.ouraring.endpoints.tags.core
  (:require [training-personal-data.ouraring.endpoints.tags.api :as api]
            [training-personal-data.ouraring.endpoints.tags.db :as db]
            [training-personal-data.core.pipeline :as pipeline]))

(def tags-config
  "Tags endpoint configuration using the generic pipeline"
  (pipeline/create-endpoint-config
   "tags"
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
  (pipeline/execute-pipeline tags-config token start-date end-date :mock mock))


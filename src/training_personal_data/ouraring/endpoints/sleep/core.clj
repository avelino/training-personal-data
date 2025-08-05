(ns training-personal-data.ouraring.endpoints.sleep.core
  (:require [training-personal-data.ouraring.endpoints.sleep.api :as api]
            [training-personal-data.ouraring.endpoints.sleep.db :as db]
            [training-personal-data.core.pipeline :as pipeline]))

(def sleep-config
  "Sleep endpoint configuration using the generic pipeline"
  (pipeline/create-endpoint-config
   "sleep"
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
  (pipeline/execute-pipeline sleep-config token start-date end-date :mock mock))


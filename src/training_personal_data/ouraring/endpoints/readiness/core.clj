(ns training-personal-data.ouraring.endpoints.readiness.core
  (:require [training-personal-data.ouraring.endpoints.readiness.api :as api]
            [training-personal-data.ouraring.endpoints.readiness.db :as db]
            [training-personal-data.ouraring.db :as common-db]
            [taoensso.timbre :as log]))

(defn fetch-and-save [token start-date end-date db-spec]
  (log/info {:event :readiness-process :msg "Processing readiness data"})
  ;; Ensure table exists
  (common-db/create-table db-spec db/table-name db/schema)
  
  (let [{:keys [success? data error]} (api/fetch token start-date end-date)]
    (if success?
      (do
        (log/info {:event :readiness-save :msg "Processing readiness records" :count (count data)})
        (doseq [readiness data]
          (let [normalized (api/normalize readiness)]
            (log/info {:event :readiness-save-record 
                      :msg "Saving readiness record" 
                      :id (:id normalized)})
            (common-db/save db-spec db/table-name db/columns normalized (db/extract-values normalized))))
        (log/info {:event :readiness-complete :msg "Successfully processed all readiness records"}))
      (throw (ex-info "Failed to fetch readiness data" error))))) 
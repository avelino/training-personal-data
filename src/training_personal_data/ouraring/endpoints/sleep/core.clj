(ns training-personal-data.ouraring.endpoints.sleep.core
  (:require [training-personal-data.ouraring.endpoints.sleep.api :as api]
            [training-personal-data.ouraring.endpoints.sleep.db :as db]
            [training-personal-data.ouraring.db :as common-db]
            [taoensso.timbre :as log]))

(defn fetch-and-save [token start-date end-date db-spec]
  (log/info {:event :sleep-process :msg "Processing daily sleep data"})
  ;; Ensure table exists
  (common-db/create-table db-spec db/table-name db/schema)
  
  (let [{:keys [success? data error]} (api/fetch token start-date end-date)]
    (if success?
      (do
        (log/info {:event :sleep-save :msg "Saving sleep records" :count (count data)})
        (doseq [sleep data]
          (let [normalized (api/normalize sleep)]
            (common-db/save db-spec db/table-name db/columns normalized (db/extract-values normalized))))
        (log/info {:event :sleep-complete :msg "Successfully saved all sleep records"}))
      (throw (ex-info "Failed to fetch sleep data" error))))) 
(ns training-personal-data.ouraring.endpoints.heart-rate.core
  (:require [training-personal-data.ouraring.endpoints.heart-rate.api :as api]
            [training-personal-data.ouraring.endpoints.heart-rate.db :as db]
            [training-personal-data.ouraring.db :as common-db]
            [taoensso.timbre :as log]))

(defn fetch-and-save [token start-date end-date db-spec]
  (log/info {:event :heart-rate-process :msg "Processing heart rate data"})
  ;; Ensure table exists
  (common-db/create-table db-spec db/table-name db/schema)
  
  (let [{:keys [success? data error]} (api/fetch token start-date end-date)]
    (if success?
      (do
        (log/info {:event :heart-rate-save :msg "Processing heart rate records" :count (count data)})
        (doseq [heart-rate data]
          (let [normalized (api/normalize heart-rate)]
            (log/info {:event :heart-rate-save-record 
                      :msg "Saving heart rate record" 
                      :id (:id normalized)})
            (common-db/save db-spec db/table-name db/columns normalized (db/extract-values normalized))))
        (log/info {:event :heart-rate-complete :msg "Successfully processed all heart rate records"}))
      (throw (ex-info "Failed to fetch heart rate data" error))))) 
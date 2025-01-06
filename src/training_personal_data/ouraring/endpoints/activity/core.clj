(ns training-personal-data.ouraring.endpoints.activity.core
  (:require [training-personal-data.ouraring.endpoints.activity.api :as api]
            [training-personal-data.ouraring.endpoints.activity.db :as db]
            [training-personal-data.ouraring.db :as common-db]
            [taoensso.timbre :as log]))

(defn fetch-and-save [token start-date end-date db-spec]
  (log/info {:event :activity-process :msg "Processing daily activity data"})
  ;; Ensure table exists
  (common-db/create-table db-spec db/table-name db/schema)
  
  (let [{:keys [success? data error]} (api/fetch token start-date end-date)]
    (if success?
      (do
        (log/info {:event :activity-save :msg "Saving activity records" :count (count data)})
        (doseq [activity data]
          (let [normalized (api/normalize activity)]
            (common-db/save db-spec db/table-name db/columns normalized (db/extract-values normalized))))
        (log/info {:event :activity-complete :msg "Successfully saved all activity records"}))
      (throw (ex-info "Failed to fetch activity data" error))))) 
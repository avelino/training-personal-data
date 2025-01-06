(ns training-personal-data.ouraring.endpoints.activity.core
  (:require [training-personal-data.ouraring.endpoints.activity.api :as api]
            [training-personal-data.ouraring.endpoints.activity.db :as db]
            [training-personal-data.ouraring.db :as common-db]
            [taoensso.timbre :as log]))

(defn fetch-and-save [token start-date end-date db-spec]
  (log/info {:event :activity-process :msg "Processing activity data"})
  ;; Ensure table exists
  (common-db/create-table db-spec db/table-name db/schema)
  
  (let [{:keys [success? data error]} (api/fetch token start-date end-date)]
    (if success?
      (do
        (log/info {:event :activity-save :msg "Processing activity records" :count (count data)})
        (doseq [activity data]
          (let [normalized (api/normalize activity)]
            (when-not (db/record-exists? db-spec 
                                       (:date normalized)
                                       (:score normalized)
                                       (:active_calories normalized))
              (log/info {:event :activity-insert 
                        :msg "Inserting new activity record" 
                        :date (:date normalized)
                        :score (:score normalized)
                        :active_calories (:active_calories normalized)})
              (common-db/save db-spec db/table-name db/columns normalized (db/extract-values normalized)))))
        (log/info {:event :activity-complete :msg "Successfully processed all activity records"}))
      (throw (ex-info "Failed to fetch activity data" error))))) 
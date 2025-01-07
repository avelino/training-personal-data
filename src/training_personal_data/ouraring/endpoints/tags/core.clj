(ns training-personal-data.ouraring.endpoints.tags.core
  (:require [training-personal-data.ouraring.endpoints.tags.api :as api]
            [training-personal-data.ouraring.endpoints.tags.db :as db]
            [training-personal-data.ouraring.db :as common-db]
            [taoensso.timbre :as log]))

(defn fetch-and-save [token start-date end-date db-spec]
  (log/info {:event :tags-process :msg "Processing tags data"})
  ;; Ensure table exists
  (common-db/create-table db-spec db/table-name db/schema)
  
  (let [{:keys [success? data error]} (api/fetch token start-date end-date)]
    (if success?
      (do
        (log/info {:event :tags-save :msg "Processing tags records" :count (count data)})
        (doseq [tag data]
          (let [normalized (api/normalize tag)]
            (log/info {:event :tags-save-record 
                      :msg "Saving tag record" 
                      :id (:id normalized)})
            (common-db/save db-spec db/table-name db/columns normalized (db/extract-values normalized))))
        (log/info {:event :tags-complete :msg "Successfully processed all tags records"}))
      (throw (ex-info "Failed to fetch tags data" error))))) 
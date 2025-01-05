(ns training-personal-data.ouraring.endpoints.activity.core
  (:require [training-personal-data.ouraring.endpoints.activity.api :as api]
            [training-personal-data.ouraring.endpoints.activity.db :as db]
            [training-personal-data.ouraring.db :as common-db]))

(defn fetch-and-save [token start-date end-date db-spec]
  (let [{:keys [success? data error]} (api/fetch token start-date end-date)]
    (if success?
      (doseq [activity data]
        (let [normalized (api/normalize activity)]
          (common-db/save db-spec db/table-name db/columns normalized (db/extract-values normalized))))
      (throw (ex-info "Failed to fetch activity data" error))))) 
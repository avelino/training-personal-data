(ns training-personal-data.strava.endpoints.activities.core
  (:require [training-personal-data.strava.endpoints.activities.api :as api]
            [training-personal-data.strava.endpoints.activities.db :as db]
            [training-personal-data.db :as common-db]
            [taoensso.timbre :as log]))

(defn fetch-and-save [token db-spec & {:keys [page per-page]
                                     :or {page 1
                                         per-page 30}}]
  (log/info {:event :activities-sync :action :start})
  ;; Ensure table exists
  (common-db/create-table db-spec db/table-name db/schema)

  (let [{:keys [success? data error]} (api/fetch token page per-page)]
    (if success?
      (do
        (log/info {:event :activities-sync :action :process :count (count data)})
        (doseq [activity data]
          (let [normalized (api/normalize activity)
                values (db/extract-values normalized)]
            (common-db/save db-spec db/table-name db/columns normalized values)))
        (log/info {:event :activities-sync :action :complete})
        ;; Return data for potential pagination handling
        {:success? true :count (count data) :data data})
      (do
        (log/error {:event :activities-sync :action :error :error error})
        {:success? false :error error}))))

(defn fetch-all-activities [token db-spec & {:keys [per-page]
                                           :or {per-page 30}}]
  (loop [page 1
         total-activities 0]
    (let [result (fetch-and-save token db-spec :page page :per-page per-page)]
      (if (and (:success? result)
               (= per-page (count (:data result))))
        (recur (inc page) (+ total-activities (count (:data result))))
        total-activities))))
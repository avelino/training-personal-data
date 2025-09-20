(ns training-personal-data.wahoo.endpoints.workout.core
  (:require [training-personal-data.wahoo.endpoints.workout.api :as api]
            [training-personal-data.wahoo.endpoints.workout.db :as db]
            [training-personal-data.db :as common-db]
            [taoensso.timbre :as log]))

(defn fetch-and-save-by-date [token start-date end-date db-spec]
  (log/info {:event :workout-process :msg "Processing workouts by date range"})
  ;; Ensure table exists
  (common-db/create-table db-spec db/table-name db/schema)

  (let [{:keys [success? data error]} (api/fetch token start-date end-date)]
    (if success?
      (do
        (log/info {:event :workout-save :msg "Processing workout records" :count (count data)})
        (doseq [workout data]
          (let [normalized (api/normalize workout)]
            (log/info {:event :workout-save-record
                      :msg "Saving workout record"
                      :id (:id normalized)})
            (common-db/save db-spec db/table-name db/columns normalized (db/extract-values normalized))))
        (log/info {:event :workout-complete :msg "Successfully processed all workout records"}))
      (throw (ex-info "Failed to fetch workout data" error)))))

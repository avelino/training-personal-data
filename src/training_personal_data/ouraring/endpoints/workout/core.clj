(ns training-personal-data.ouraring.endpoints.workout.core
  (:require [training-personal-data.ouraring.endpoints.workout.api :as api]
            [training-personal-data.ouraring.endpoints.workout.db :as db]
            [training-personal-data.ouraring.db :as common-db]
            [taoensso.timbre :as log]))

(defn fetch-and-save [token start-date end-date db-spec]
  (log/info {:event :workout-process :msg "Processing workout data"})
  ;; Ensure table exists
  (common-db/create-table db-spec db/table-name db/schema)
  
  (let [{:keys [success? data error]} (api/fetch token start-date end-date)]
    (if success?
      (do
        (log/info {:event :workout-save :msg "Processing workout records" :count (count data)})
        (let [futures (doall
                       (for [workout data]
                         (let [normalized (api/normalize workout)]
                           (when-not (db/record-exists? db-spec 
                                                      (:date normalized)
                                                      (:start_datetime normalized)
                                                      (:activity normalized))
                             (log/info {:event :workout-insert 
                                      :msg "Inserting new workout record" 
                                      :date (:date normalized)
                                      :activity (:activity normalized)
                                      :start_datetime (:start_datetime normalized)})
                             (common-db/save db-spec db/table-name db/columns normalized (db/extract-values normalized))))))]
          ;; Esperar todos os inserts terminarem antes de retornar
          (doseq [f (remove nil? futures)]
            @f))
        (log/info {:event :workout-complete :msg "Successfully processed all workout records"}))
      (throw (ex-info "Failed to fetch workout data" error))))) 
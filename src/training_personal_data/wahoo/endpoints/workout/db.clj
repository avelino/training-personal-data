(ns training-personal-data.wahoo.endpoints.workout.db
  (:require [cheshire.core :as json]))

(def table-name "wahoo_workout")

(def columns
  ["id" "starts" "minutes" "name" "plan_id" "workout_token"
   "workout_type_id" "workout_summary" "created_at" "updated_at" "raw_json"])

(def schema
  {:id [:text :primary-key]
   :starts :timestamp
   :minutes :integer
   :name :text
   :plan_id [:text :null]
   :workout_token [:text :null]
   :workout_type_id :integer
   :workout_summary [:jsonb :null]
   :created_at :timestamp
   :updated_at :timestamp
   :raw_json :jsonb
   :timestamp [:timestamp :default "CURRENT_TIMESTAMP"]})

(defn extract-values [workout]
  [(:id workout)
   (:starts workout)
   (:minutes workout)
   (:name workout)
   (:plan_id workout)
   (:workout_token workout)
   (:workout_type_id workout)
   (when (:workout_summary workout)
     (json/generate-string (:workout_summary workout)))
   (:created_at workout)
   (:updated_at workout)
   (:raw_json workout)])
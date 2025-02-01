(ns training-personal-data.strava.endpoints.activities.db
  (:require [training-personal-data.strava.db :as db]
            [pod.babashka.postgresql :as pg]))

(def table-name "strava_activities")

(def columns
  ["id" "name" "distance" "moving_time" "elapsed_time" "total_elevation_gain"
   "type" "sport_type" "start_date" "start_date_local" "timezone"
   "achievement_count" "kudos_count" "comment_count" "athlete_count"
   "average_speed" "max_speed" "average_watts" "kilojoules" "device_watts"
   "has_heartrate" "average_heartrate" "max_heartrate" "elev_high" "elev_low"
   "upload_id" "external_id" "trainer" "commute" "manual" "private" "flagged"
   "workout_type" "raw_json"])

(def schema
  {:id [:bigint :primary-key]
   :name :text
   :distance ["double precision"]
   :moving_time :integer
   :elapsed_time :integer
   :total_elevation_gain ["double precision"]
   :type :text
   :sport_type :text
   :start_date [:timestamp]
   :start_date_local [:timestamp]
   :timezone :text
   :achievement_count :integer
   :kudos_count :integer
   :comment_count :integer
   :athlete_count :integer
   :average_speed ["double precision"]
   :max_speed ["double precision"]
   :average_watts ["double precision"]
   :kilojoules ["double precision"]
   :device_watts :boolean
   :has_heartrate :boolean
   :average_heartrate ["double precision"]
   :max_heartrate ["double precision"]
   :elev_high ["double precision"]
   :elev_low ["double precision"]
   :upload_id :bigint
   :external_id :text
   :trainer :boolean
   :commute :boolean
   :manual :boolean
   :private :boolean
   :flagged :boolean
   :workout_type :integer
   :raw_json :jsonb
   :created_at [:timestamp :default "CURRENT_TIMESTAMP"]})

(defn extract-values [activity]
  [(:id activity)
   (:name activity)
   (:distance activity)
   (:moving_time activity)
   (:elapsed_time activity)
   (:total_elevation_gain activity)
   (:type activity)
   (:sport_type activity)
   (:start_date activity)
   (:start_date_local activity)
   (:timezone activity)
   (:achievement_count activity)
   (:kudos_count activity)
   (:comment_count activity)
   (:athlete_count activity)
   (:average_speed activity)
   (:max_speed activity)
   (:average_watts activity)
   (:kilojoules activity)
   (:device_watts activity)
   (:has_heartrate activity)
   (:average_heartrate activity)
   (:max_heartrate activity)
   (:elev_high activity)
   (:elev_low activity)
   (:upload_id activity)
   (:external_id activity)
   (:trainer activity)
   (:commute activity)
   (:manual activity)
   (:private activity)
   (:flagged activity)
   (:workout_type activity)
   (:raw_json activity)])
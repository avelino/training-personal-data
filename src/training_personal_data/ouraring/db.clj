(ns training-personal-data.ouraring.db
  (:require [cheshire.core :as json]
            [babashka.pods :as pods]))

;; Load PostgreSQL babashka/pods
(pods/load-pod 'org.babashka/postgresql "0.1.0")
(require '[pod.babashka.postgresql :as pg])

(def db-spec
  {:dbtype "postgresql"
   :dbname (or (System/getenv "SUPABASE_DB_NAME") "postgres")
   :host (or (System/getenv "SUPABASE_HOST") "db.example.supabase.co")
   :port (or (System/getenv "SUPABASE_PORT") 5432)
   :user (or (System/getenv "SUPABASE_USER") "postgres")
   :password (System/getenv "SUPABASE_PASSWORD")
   :sslmode "require"})

(def daily-activity-table-name "ouraring_daily_activity")

(def schema
  [(str "CREATE TABLE IF NOT EXISTS " daily-activity-table-name " (
     date DATE PRIMARY KEY,
     class_5_min TEXT,
     score INTEGER,
     active_calories INTEGER,
     average_met DOUBLE PRECISION,
     daily_movement INTEGER,
     equivalent_walking_distance INTEGER,
     high_activity_met_minutes INTEGER,
     high_activity_time INTEGER,
     inactivity_alerts INTEGER,
     low_activity_met_minutes INTEGER,
     low_activity_time INTEGER,
     medium_activity_met_minutes INTEGER,
     medium_activity_time INTEGER,
     met TEXT,
     meters_to_target INTEGER,
     non_wear_time INTEGER,
     resting_time INTEGER,
     sedentary_met_minutes INTEGER,
     sedentary_time INTEGER,
     steps INTEGER,
     target_calories INTEGER,
     target_meters INTEGER,
     total_calories INTEGER,
     day_summary TEXT,
     timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   )")])

(defn ensure-db []
  (pg/execute! db-spec [(first schema)]))

(defn normalize-activity-data [data]
  (let [activity (-> data
                     (json/parse-string true)
                     :data
                     first)]
    {:date (:day activity)
     :class_5_min (json/generate-string (:class_5_min activity))
     :score (:score activity)
     :active_calories (:active_calories activity)
     :average_met (:average_met activity)
     :daily_movement (:daily_movement activity)
     :equivalent_walking_distance (:equivalent_walking_distance activity)
     :high_activity_met_minutes (:high_activity_met_minutes activity)
     :high_activity_time (:high_activity_time activity)
     :inactivity_alerts (:inactivity_alerts activity)
     :low_activity_met_minutes (:low_activity_met_minutes activity)
     :low_activity_time (:low_activity_time activity)
     :medium_activity_met_minutes (:medium_activity_met_minutes activity)
     :medium_activity_time (:medium_activity_time activity)
     :met (json/generate-string (:met activity))
     :meters_to_target (:meters_to_target activity)
     :non_wear_time (:non_wear_time activity)
     :resting_time (:resting_time activity)
     :sedentary_met_minutes (:sedentary_met_minutes activity)
     :sedentary_time (:sedentary_time activity)
     :steps (:steps activity)
     :target_calories (:target_calories activity)
     :target_meters (:target_meters activity)
     :total_calories (:total_calories activity)
     :day_summary (json/generate-string activity)}))

(defn activity-exists? [date]
  (-> (pg/execute! db-spec
                   [(str "SELECT EXISTS(SELECT 1 FROM " daily-activity-table-name " WHERE date = ?::date)") date])
      first
      :exists))

(defn edit-activity [normalized date]
  (let [values [(:class_5_min normalized)
                (:score normalized)
                (:active_calories normalized)
                (:average_met normalized)
                (:daily_movement normalized)
                (:equivalent_walking_distance normalized)
                (:high_activity_met_minutes normalized)
                (:high_activity_time normalized)
                (:inactivity_alerts normalized)
                (:low_activity_met_minutes normalized)
                (:low_activity_time normalized)
                (:medium_activity_met_minutes normalized)
                (:medium_activity_time normalized)
                (:met normalized)
                (:meters_to_target normalized)
                (:non_wear_time normalized)
                (:resting_time normalized)
                (:sedentary_met_minutes normalized)
                (:sedentary_time normalized)
                (:steps normalized)
                (:target_calories normalized)
                (:target_meters normalized)
                (:total_calories normalized)
                (:day_summary normalized)]]
    (if (activity-exists? date)
      ;; Update
      (pg/execute! db-spec
                   (into [(str "UPDATE " daily-activity-table-name " SET
                           class_5_min = ?, score = ?, active_calories = ?,
                           average_met = ?, daily_movement = ?, equivalent_walking_distance = ?,
                           high_activity_met_minutes = ?, high_activity_time = ?, inactivity_alerts = ?,
                           low_activity_met_minutes = ?, low_activity_time = ?, medium_activity_met_minutes = ?,
                           medium_activity_time = ?, met = ?, meters_to_target = ?, non_wear_time = ?,
                           resting_time = ?, sedentary_met_minutes = ?, sedentary_time = ?, steps = ?,
                           target_calories = ?, target_meters = ?, total_calories = ?, day_summary = ?
                         WHERE date = ?::date")]
                         (conj values date)))
      ;; Insert
      (pg/execute! db-spec
                   (into [(str "INSERT INTO " daily-activity-table-name " (
                           date, class_5_min, score, active_calories, average_met,
                           daily_movement, equivalent_walking_distance, high_activity_met_minutes,
                           high_activity_time, inactivity_alerts, low_activity_met_minutes,
                           low_activity_time, medium_activity_met_minutes, medium_activity_time,
                           met, meters_to_target, non_wear_time, resting_time,
                           sedentary_met_minutes, sedentary_time, steps, target_calories,
                           target_meters, total_calories, day_summary
                         ) VALUES (?::date, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")]
                         (cons date values))))))

(defn save-activity [data date]
  (ensure-db)
  (let [normalized (normalize-activity-data data)
        activity-date (:day (-> data (json/parse-string true) :data first))]
    (println (if (activity-exists? activity-date)
              "Updating activity for date:"
              "Inserting new activity for date:")
            activity-date)
    (edit-activity normalized activity-date))) 
(ns training-personal-data.ouraring.db
  "Database operations for Oura Ring data"
  (:require [babashka.pods :as pods]
            [clojure.string :as str]))

;; Load PostgreSQL pod
(pods/load-pod 'org.babashka/postgresql "0.1.0")
(require '[pod.babashka.postgresql :as pg])

(def ^:private daily-activity-table "ouraring_daily_activity")

(defn make-db-spec
  "Creates a database specification map from configuration.
   Parameters:
   - config: Map containing database configuration with keys:
     :dbname, :host, :port, :user, :password, :sslmode
   Returns:
   - Map with PostgreSQL connection specification"
  [config]
  (let [spec {:dbtype "postgresql"
              :dbname (get config :dbname "postgres")
              :host (get config :host "localhost")
              :port (get config :port 5432)
              :user (get config :user "postgres")
              :password (:password config)
              :sslmode (get config :sslmode "require")}]
    (println "Connecting to database with config:" 
             (-> spec
                 (dissoc :password)
                 (assoc :password "[REDACTED]")))
    spec))

(defn test-connection
  "Tests database connection by executing a simple query.
   Parameters:
   - db-spec: Database connection specification
   Returns:
   - true if connection successful, false otherwise"
  [db-spec]
  (try
    (pg/execute! db-spec ["SELECT 1"])
    (println "Database connection successful!")
    true
    (catch Exception e
      (println "Failed to connect to database:" (ex-message e))
      (println "Database config:" 
               (-> db-spec
                   (dissoc :password)
                   (assoc :password "[REDACTED]")))
      false)))

(defn create-table-sql
  "Generates SQL for creating the daily activity table.
   Returns:
   - String containing CREATE TABLE SQL statement"
  []
  (str "CREATE TABLE IF NOT EXISTS " daily-activity-table " (
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
   )"))

(defn ensure-table
  "Ensures the daily activity table exists in the database.
   Parameters:
   - db-spec: Database connection specification
   Returns:
   - Result of CREATE TABLE execution if successful"
  [db-spec]
  (when (test-connection db-spec)
    (pg/execute! db-spec [(create-table-sql)])))

(defn activity-exists?
  "Checks if an activity record exists for the given date.
   Parameters:
   - db-spec: Database connection specification
   - date: Date string in YYYY-MM-DD format
   Returns:
   - true if activity exists, false otherwise"
  [db-spec date]
  (-> (pg/execute! db-spec
                   [(str "SELECT EXISTS(SELECT 1 FROM " daily-activity-table " WHERE date = ?::date)") date])
      first
      :exists))

(defn build-update-sql
  "Builds SQL for updating an existing activity record.
   Returns:
   - String containing UPDATE SQL statement"
  []
  (let [columns ["class_5_min" "score" "active_calories" "average_met"
                 "daily_movement" "equivalent_walking_distance" "high_activity_met_minutes"
                 "high_activity_time" "inactivity_alerts" "low_activity_met_minutes"
                 "low_activity_time" "medium_activity_met_minutes" "medium_activity_time"
                 "met" "meters_to_target" "non_wear_time" "resting_time"
                 "sedentary_met_minutes" "sedentary_time" "steps" "target_calories"
                 "target_meters" "total_calories" "day_summary"]
        set-clause (str/join ", " (map #(str % " = ?") columns))]
    (str "UPDATE " daily-activity-table
         " SET " set-clause
         " WHERE date = ?::date")))

(defn build-insert-sql
  "Builds SQL for inserting a new activity record.
   Returns:
   - String containing INSERT SQL statement"
  []
  (let [columns (str/join ", " (cons "date" ["class_5_min" "score" "active_calories" "average_met"
                                            "daily_movement" "equivalent_walking_distance" "high_activity_met_minutes"
                                            "high_activity_time" "inactivity_alerts" "low_activity_met_minutes"
                                            "low_activity_time" "medium_activity_met_minutes" "medium_activity_time"
                                            "met" "meters_to_target" "non_wear_time" "resting_time"
                                            "sedentary_met_minutes" "sedentary_time" "steps" "target_calories"
                                            "target_meters" "total_calories" "day_summary"]))
        placeholders (str/join ", " (cons "?::date" (repeat 24 "?")))]
    (str "INSERT INTO " daily-activity-table " (" columns ") VALUES (" placeholders ")")))

(defn extract-values
  "Extracts values from activity map in the correct order for SQL parameters.
   Parameters:
   - activity: Map containing activity data
   Returns:
   - Vector of values in the order matching SQL columns"
  [activity]
  [(:class_5_min activity)
   (:score activity)
   (:active_calories activity)
   (:average_met activity)
   (:daily_movement activity)
   (:equivalent_walking_distance activity)
   (:high_activity_met_minutes activity)
   (:high_activity_time activity)
   (:inactivity_alerts activity)
   (:low_activity_met_minutes activity)
   (:low_activity_time activity)
   (:medium_activity_met_minutes activity)
   (:medium_activity_time activity)
   (:met activity)
   (:meters_to_target activity)
   (:non_wear_time activity)
   (:resting_time activity)
   (:sedentary_met_minutes activity)
   (:sedentary_time activity)
   (:steps activity)
   (:target_calories activity)
   (:target_meters activity)
   (:total_calories activity)
   (:day_summary activity)])

(defn save-activity
  "Saves or updates an activity record in the database.
   Parameters:
   - db-spec: Database connection specification
   - activity: Map containing activity data with :date and other fields
   Returns:
   - Result of database operation"
  [db-spec activity]
  (println "Attempting to save activity for date:" (:date activity))
  (let [date (:date activity)
        values (extract-values activity)]
    (println "Activity exists check for date:" date)
    (if (activity-exists? db-spec date)
      (do
        (println "Updating existing activity for date:" date)
        (pg/execute! db-spec (into [(build-update-sql)] (conj values date))))
      (do
        (println "Inserting new activity for date:" date)
        (println "SQL:" (build-insert-sql))
        (println "Values:" (cons date values))
        (pg/execute! db-spec (into [(build-insert-sql)] (cons date values))))))) 
(ns training-personal-data.ouraring.db
  (:require [babashka.pods :as pods]
            [clojure.string :as str]))

;; Load PostgreSQL pod
(pods/load-pod 'org.babashka/postgresql "0.1.0")
(require '[pod.babashka.postgresql :as pg])

(defn normalize-column-name [col-name]
  (-> col-name
      name
      (str/replace "-" "_")))

(defn make-db-spec [config]
  (let [required-keys [:dbname :host :user :password]
        missing-keys (remove #(get config %) required-keys)]
    (when (seq missing-keys)
      (throw (ex-info "Missing required database configuration keys" 
                     {:missing missing-keys}))))
  
  (let [spec {:dbtype "postgresql"
              :dbname (:dbname config)
              :host (:host config)
              :port (get config :port 5432)
              :user (:user config)
              :password (:password config)
              :sslmode (get config :sslmode "require")}]
    (println "Connecting to database with config:" 
             (-> spec
                 (dissoc :password)
                 (assoc :password "[REDACTED]")))
    spec))

(defn test-connection [db-spec]
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

(defn create-table [db-spec table schema]
  (let [columns (->> schema
                    (map (fn [[col type]]
                          (let [type-str (if (vector? type)
                                         (case (second type)
                                           :primary-key (str (name (first type)) " PRIMARY KEY")
                                           :default (str (name (first type)) 
                                                       " DEFAULT " 
                                                       (last type))
                                           (str/join " " (map name type)))
                                         (name type))
                                col-name (normalize-column-name col)]
                            (str col-name " " type-str))))
                    (str/join ",\n"))
        sql (str "CREATE TABLE IF NOT EXISTS " table " (\n" columns "\n)")]
    (pg/execute! db-spec [sql])))

(defn record-exists? [db-spec table date]
  (-> (pg/execute! db-spec
                   [(str "SELECT EXISTS(SELECT 1 FROM " table " WHERE date = ?::date) AS exists") date])
      first
      :exists))

(defn build-update-sql [table columns]
  (let [set-pairs (map-indexed (fn [idx col]
                                (let [col-name (normalize-column-name col)]
                                  (if (= col-name "timestamp")
                                    (str col-name " = ?::timestamp")
                                    (str col-name " = ?"))))
                              columns)
        set-clause (str/join ", " set-pairs)]
    (str "UPDATE " table
         " SET " set-clause
         " WHERE date = ?::date")))

(defn build-insert-sql [table columns]
  (let [normalized-columns (map normalize-column-name columns)
        all-columns (str/join ", " (cons "date" normalized-columns))
        placeholders (str/join ", " 
                             (cons "?::date" 
                                  (map #(if (= % "timestamp") "?::timestamp" "?") 
                                       normalized-columns)))]
    (str "INSERT INTO " table " (" all-columns ") VALUES (" placeholders ")")))

(defn save [db-spec table columns record values]
  (println "Attempting to save record for date:" (:date record))
  (let [date (:date record)]
    (println "Record exists check for date:" date)
    (if (record-exists? db-spec table date)
      (do
        (println "Updating existing record for date:" date)
        (pg/execute! db-spec (into [(build-update-sql table columns)] (conj values date))))
      (do
        (println "Inserting new record for date:" date)
        (println "SQL:" (build-insert-sql table columns))
        (println "Values:" (cons date values))
        (pg/execute! db-spec (into [(build-insert-sql table columns)] (cons date values))))))) 
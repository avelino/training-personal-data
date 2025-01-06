(ns training-personal-data.ouraring.db
  (:require [babashka.pods :as pods]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

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
    (log/info {:event :db-connect :msg "Connecting to PostgreSQL database" :host (:host spec)})
    spec))

(defn test-connection [db-spec]
  (try
    (pg/execute! db-spec ["SELECT 1"])
    (log/info {:event :db-connect-success :msg "Successfully connected to PostgreSQL database"})
    true
    (catch Exception e
      (log/error {:event :db-connect-error :msg "Failed to connect to PostgreSQL database" :error (ex-message e)})
      false)))

(defn create-table [db-spec table schema]
  (let [pk-columns (get schema :pk)
        schema-without-pk (dissoc schema :pk)
        columns (->> schema-without-pk
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
                    (concat (when pk-columns
                             [(str "PRIMARY KEY (" (str/join ", " (map normalize-column-name pk-columns)) ")")]))
                    (str/join ",\n"))
        sql (str "CREATE TABLE IF NOT EXISTS " table " (\n" columns "\n)")]
    (log/info {:event :db-create-table :msg "Ensuring table exists" :table table})
    (pg/execute! db-spec [sql])))

(defn record-exists? [db-spec table date timestamp]
  (-> (pg/execute! db-spec
                   [(str "SELECT EXISTS(SELECT 1 FROM " table 
                         " WHERE date = ?::date AND timestamp = ?::timestamp) AS exists") 
                    date timestamp])
      first
      :exists))

(defn build-update-sql [table columns]
  (let [set-pairs (map-indexed (fn [idx col]
                                (let [col-name (normalize-column-name col)]
                                  (cond
                                    (= col-name "timestamp") (str col-name " = ?::timestamp")
                                    (= col-name "start_datetime") (str col-name " = ?::timestamp")
                                    (= col-name "end_datetime") (str col-name " = ?::timestamp")
                                    :else (str col-name " = ?"))))
                              columns)
        set-clause (str/join ", " set-pairs)]
    (str "UPDATE " table
         " SET " set-clause
         " WHERE date = ?::date AND timestamp = ?::timestamp")))

(defn build-insert-sql [table columns]
  (let [normalized-columns (map normalize-column-name columns)
        all-columns (str/join ", " (cons "date" normalized-columns))
        placeholders (str/join ", " 
                             (cons "?::date" 
                                  (map #(cond
                                        (= % "timestamp") "?::timestamp"
                                        (= % "start_datetime") "?::timestamp"
                                        (= % "end_datetime") "?::timestamp"
                                        :else "?")
                                       normalized-columns)))]
    (str "INSERT INTO " table " (" all-columns ") VALUES (" placeholders ")")))

(defn save [db-spec table columns record values]
  (let [date (:date record)
        timestamp (:timestamp record)]
    (future
      (if (record-exists? db-spec table date timestamp)
        (do
          (log/info {:event :db-update :msg "Updating record" :table table :date date :timestamp timestamp})
          (pg/execute! db-spec (into [(build-update-sql table columns)] (conj values date timestamp))))
        (do
          (log/info {:event :db-insert :msg "Inserting new record" :table table :date date :timestamp timestamp})
          (pg/execute! db-spec (into [(build-insert-sql table columns)] (cons date values)))))
      ;; Retornar o registro para facilitar encadeamento de operações
      record))) 
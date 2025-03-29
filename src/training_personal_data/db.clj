(ns training-personal-data.db
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

(defn query
  "Execute a SQL query with parameters and return results.
   This is a wrapper around pg/execute! for consistency."
  [db-spec [sql & params]]
  (try
    (log/debug {:event :db-query :sql sql :params params})
    (pg/execute! db-spec (into [sql] params))
    (catch Exception e
      (log/error {:event :db-query-error :sql sql :params params :error (ex-message e)})
      (throw e))))

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
                                             (case type
                                               :jsonb "jsonb"
                                               (name type)))
                                  col-name (normalize-column-name col)]
                              (str col-name " " type-str))))
                     (str/join ",\n"))
        sql (str "CREATE TABLE IF NOT EXISTS " table " (\n" columns "\n)")]
    (log/info {:event :db-create-table :msg "Ensuring table exists" :table table})
    (pg/execute! db-spec [sql])))

(defn record-exists? [db-spec table id]
  (-> (pg/execute! db-spec
                   [(str "SELECT EXISTS(SELECT 1 FROM " table
                         " WHERE id = ?) AS exists")
                    id])
      first
      :exists))

(defn build-update-sql [table columns]
  (let [update-columns (remove #(= % "id") columns)
        set-pairs (map (fn [col]
                         (let [col-name (normalize-column-name col)]
                           (cond
                             (= col-name "date") (str col-name " = ?::date")
                             (str/ends-with? col-name "_datetime") (str col-name " = ?::timestamp")
                             (= col-name "timestamp") (str col-name " = ?::timestamp")
                             (= col-name "tags") (str col-name " = ?::text[]")
                             (or (= col-name "raw_json")
                                 (= col-name "met")
                                 (= col-name "day_summary")
                                 (= col-name "raw_data")
                                 (= col-name "gpt_metrics_table")
                                 (str/ends-with? col-name "_json")) (str col-name " = ?::jsonb")
                             :else (str col-name " = ?"))))
                       update-columns)
        set-clause (str/join ", " set-pairs)]
    (str "UPDATE " table " SET " set-clause " WHERE id = ?")))

(defn build-insert-sql [table columns]
  (let [normalized-columns (map normalize-column-name columns)
        placeholders (map #(cond
                             (= % "date") "?::date"
                             (str/ends-with? % "_datetime") "?::timestamp"
                             (= % "timestamp") "?::timestamp"
                             (= % "tags") "?::text[]"
                             (or (= % "raw_json")
                                 (= % "met")
                                 (= % "day_summary")
                                 (= % "raw_data")
                                 (= % "gpt_metrics_table")
                                 (str/ends-with? % "_json")) "?::jsonb"
                             :else "?")
                          normalized-columns)]
    (str "INSERT INTO " table " ("
         (str/join ", " normalized-columns)
         ") VALUES ("
         (str/join ", " placeholders)
         ")")))

(defn save [db-spec table columns record values]
  (let [id (:id record)]
    (when-not id
      (throw (ex-info "Record must have an id" {:record record})))
    (try
      (if (record-exists? db-spec table id)
        (do
          (log/debug {:event :db-save :action :update :table table :id id})
          (let [sql (build-update-sql table columns)
                update-values (vec (concat (rest values) [id]))]
            (log/debug {:event :db-save :action :update-sql :sql sql})
            (pg/execute! db-spec (into [sql] update-values))))
        (do
          (log/debug {:event :db-save :action :insert :table table :id id})
          (let [sql (build-insert-sql table columns)]
            (log/debug {:event :db-save :action :insert-sql :sql sql :values (pr-str values)})
            (pg/execute! db-spec (into [sql] values)))))
      (catch Exception e
        (log/error {:event :db-save :action :error :table table :id id :error (ex-message e)})
        (throw e)))
    record))
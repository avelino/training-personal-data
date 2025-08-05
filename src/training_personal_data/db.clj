(ns training-personal-data.db
  (:require [babashka.pods :as pods]
            [clojure.string :as str]
            [taoensso.timbre :as log])
  (:import [java.util.concurrent ConcurrentHashMap]
           [java.util.concurrent.atomic AtomicInteger]))

;; Load PostgreSQL pod
(pods/load-pod 'org.babashka/postgresql "0.1.0")
(require '[pod.babashka.postgresql :as pg])

;; Connection Pool Management
(def ^:private connection-pools (ConcurrentHashMap.))
(def ^:private pool-stats (atom {}))

(defn- create-pool-key [db-spec]
  (str (:host db-spec) ":" (:port db-spec) "/" (:dbname db-spec) ":" (:user db-spec)))

(defn- create-connection-pool 
  "Creates a connection pool for the given database specification"
  [db-spec & {:keys [max-connections initial-connections connection-timeout-ms]
              :or {max-connections 10 initial-connections 2 connection-timeout-ms 30000}}]
  (let [pool-key (create-pool-key db-spec)
        pool {:db-spec db-spec
              :max-connections max-connections
              :current-connections (AtomicInteger. 0)
              :active-connections (AtomicInteger. 0)
              :connection-timeout-ms connection-timeout-ms
              :created-at (System/currentTimeMillis)}]
    (log/info {:event :connection-pool-created 
               :pool-key pool-key 
               :max-connections max-connections
               :initial-connections initial-connections})
    
    ;; Update pool stats
    (swap! pool-stats assoc pool-key 
           {:created-at (:created-at pool)
            :max-connections max-connections
            :total-connections-created 0
            :total-queries-executed 0
            :last-used (System/currentTimeMillis)})
    
    pool))

(defn get-connection-pool 
  "Gets or creates a connection pool for the given database specification"
  [db-spec & pool-options]
  (let [pool-key (create-pool-key db-spec)]
    (.computeIfAbsent connection-pools pool-key
                      (fn [_] (apply create-connection-pool db-spec pool-options)))))

(defn get-pool-stats
  "Returns statistics for all connection pools"
  []
  (let [current-stats @pool-stats
        runtime-stats (reduce-kv 
                       (fn [acc pool-key pool]
                         (assoc acc pool-key 
                                (merge (get current-stats pool-key {})
                                       {:current-connections (.get (:current-connections pool))
                                        :active-connections (.get (:active-connections pool))
                                        :max-connections (:max-connections pool)})))
                       {}
                       connection-pools)]
    runtime-stats))

(defn close-connection-pools!
  "Closes all connection pools and clears the pool cache"
  []
  (log/info {:event :closing-all-connection-pools :count (.size connection-pools)})
  (.clear connection-pools)
  (reset! pool-stats {})
  (log/info {:event :connection-pools-closed}))

(defn with-pooled-connection
  "Executes a function with a pooled database connection"
  [db-spec f & pool-options]
  (let [pool (apply get-connection-pool db-spec pool-options)
        pool-key (create-pool-key db-spec)
        start-time (System/currentTimeMillis)]
    
    ;; Check if we can create a new connection
    (when (>= (.get (:current-connections pool)) (:max-connections pool))
      (log/warn {:event :connection-pool-exhausted :pool-key pool-key})
      (throw (ex-info "Connection pool exhausted" 
                      {:pool-key pool-key 
                       :max-connections (:max-connections pool)
                       :current-connections (.get (:current-connections pool))})))
    
    (try
      (.incrementAndGet (:active-connections pool))
      (let [result (f (:db-spec pool))]
        ;; Update stats
        (swap! pool-stats update-in [pool-key :total-queries-executed] (fnil inc 0))
        (swap! pool-stats assoc-in [pool-key :last-used] (System/currentTimeMillis))
        
        (log/debug {:event :pooled-connection-used 
                    :pool-key pool-key
                    :duration-ms (- (System/currentTimeMillis) start-time)
                    :active-connections (.get (:active-connections pool))})
        result)
      (catch Exception e
        (log/error {:event :pooled-connection-error 
                    :pool-key pool-key 
                    :error (ex-message e)})
        (throw e))
      (finally
        (.decrementAndGet (:active-connections pool))))))

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

(defn test-connection 
  "Tests database connection, optionally using connection pool"
  [db-spec & {:keys [use-pool] :or {use-pool true}}]
  (try
    (if use-pool
      (with-pooled-connection db-spec
        (fn [spec] 
          (pg/execute! spec ["SELECT 1"])
          (log/info {:event :db-connect-success :msg "Successfully connected to PostgreSQL database (pooled)"})
          true))
      (do
        (pg/execute! db-spec ["SELECT 1"])
        (log/info {:event :db-connect-success :msg "Successfully connected to PostgreSQL database (direct)"})
        true))
    (catch Exception e
      (log/error {:event :db-connect-error :msg "Failed to connect to PostgreSQL database" :error (ex-message e)})
      false)))

(defn query
  "Execute a SQL query with parameters and return results.
   Supports both direct connection and connection pooling."
  [db-spec [sql & params] & {:keys [use-pool] :or {use-pool true}}]
  (try
    (log/debug {:event :db-query :sql sql :params params :use-pool use-pool})
    (if use-pool
      (with-pooled-connection db-spec
        (fn [spec] (pg/execute! spec (into [sql] params))))
      (pg/execute! db-spec (into [sql] params)))
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
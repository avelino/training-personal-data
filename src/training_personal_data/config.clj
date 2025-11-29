(ns training-personal-data.config)

(defn get-db-config []
  (let [config {:dbname (System/getenv "SUPABASE_DB_NAME")
                :host (System/getenv "SUPABASE_HOST")
                :port 5432
                :user (System/getenv "SUPABASE_USER")
                :password (System/getenv "SUPABASE_PASSWORD")
                :sslmode "require"}]
    (when (some nil? ((juxt :dbname :host :user :password) config))
      (throw (ex-info "Missing required database configuration"
                      {:config (-> config
                                   (dissoc :password)
                                   (assoc :password "[REDACTED]"))})))
    config))

(defn get-roam-config []
  (let [token (System/getenv "ROAM_API_TOKEN")
        graph (System/getenv "ROAM_GRAPH")
        graph-password (System/getenv "ROAM_GRAPH_PASSWORD")]
    (when (and token graph)
      {:token token
       :graph graph
       :page-prefix (or (System/getenv "ROAM_PAGE_PREFIX") "oura")
       :graph-password graph-password})))

(defn roam-enabled? []
  (boolean (get-roam-config)))
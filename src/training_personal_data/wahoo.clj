(ns training-personal-data.wahoo
  (:require [clojure.string :as str]
            [training-personal-data.wahoo.endpoints.workout.core :as workout]
            [training-personal-data.wahoo.api :as wahoo-api]
            [training-personal-data.wahoo.tokens :as tokens]
            [training-personal-data.db :as db]
            [training-personal-data.config :as config]
            [taoensso.timbre :as log]))

(defn- present? [value]
  (boolean (and value (not (str/blank? value)))))


(def transient-sqlstates
  #{"08006" "08003" "08001" "57P01"})

(def transient-message-fragments
  ["connection attempt failed"
   "connection reset"
   "broken pipe"
   "terminating connection"
   "connection prematurely closed"])

(defn transient-db-error? [e]
  (let [msg (some-> e ex-message str/lower-case)
        cause-msg (some-> e .getCause (.getMessage) str/lower-case)
        data (ex-data e)
        sqlstate (some-> data :sqlstate)]
    (or (and sqlstate (contains? transient-sqlstates sqlstate))
        (some #(when msg (str/includes? msg %)) transient-message-fragments)
        (some #(when cause-msg (str/includes? cause-msg %)) transient-message-fragments))))

(defn with-transient-retries [attempts f]
  (loop [attempt 1]
    (let [outcome (try
                     {:result (f)}
                     (catch Exception e
                       {:error e}))
          error (:error outcome)]
      (if error
        (if (and (< attempt attempts) (transient-db-error? error))
          (let [sleep-ms (* attempt 2000)]
            (log/warn {:event :db-transient-error
                       :msg (ex-message error)
                       :attempt attempt
                       :next-attempt (inc attempt)
                       :sleep-ms sleep-ms})
            (Thread/sleep sleep-ms)
            (recur (inc attempt)))
          (throw error))
        (:result outcome)))))

(defn- refresh-token! [db-spec client-id client-secret redirect-uri refresh-token source fallback can-refresh?]
  (when (and can-refresh? (present? refresh-token))
    (log/info {:event :oauth-refresh-start
               :provider :wahoo
               :source source})
    (try
      (let [resp (wahoo-api/refresh-access-token client-id client-secret refresh-token redirect-uri)
            saved (tokens/save-token! db-spec (assoc resp :raw resp)
                                      {:fallback-refresh fallback
                                       :source source})]
        (log/info {:event :oauth-refresh-success
                   :provider :wahoo
                   :source source
                   :expires_in (:expires_in resp)})
        (assoc saved :token-source (keyword (str (name source) "-refresh"))))
      (catch Exception e
        (let [data (ex-data e)]
          (log/error {:event :oauth-refresh-error
                      :provider :wahoo
                      :source source
                      :error (ex-message e)
                      :status (:status data)
                      :body (:body data)})
          (when (and (= source :stored)
                     (= 400 (:status data)))
            (tokens/delete-token! db-spec))
          nil)))))

(defn- exchange-auth-code! [db-spec client-id client-secret redirect-uri auth-code ci? can-refresh?]
  (when (and can-refresh? (not ci?) (present? auth-code) (present? redirect-uri))
    (log/info {:event :oauth-authcode-start :provider :wahoo})
    (try
      (let [resp (wahoo-api/exchange-authorization-code client-id client-secret auth-code redirect-uri)
            saved (tokens/save-token! db-spec (assoc resp :raw resp)
                                      {:source :auth-code})]
        (log/info {:event :oauth-authcode-success :provider :wahoo})
        (assoc saved :token-source :auth-code))
      (catch Exception e
        (let [data (ex-data e)]
          (log/error {:event :oauth-authcode-error
                      :provider :wahoo
                      :error (ex-message e)
                      :status (:status data)
                      :body (:body data)})
          nil)))))

(defn- resolve-access-token [db-spec]
  (tokens/ensure-table! db-spec)
  (let [client-id (System/getenv "WAHOO_CLIENT_ID")
        client-secret (System/getenv "WAHOO_CLIENT_SECRET")
        redirect-uri (System/getenv "WAHOO_REDIRECT_URI")
        stored (tokens/get-token db-spec)
        env-refresh (System/getenv "WAHOO_REFRESH_TOKEN")
        env-token (System/getenv "WAHOO_TOKEN")
        auth-code (System/getenv "WAHOO_AUTH_CODE")
        ci? (= "true" (System/getenv "CI"))
        can-refresh? (and (present? client-id) (present? client-secret))
        resolved (or (when (tokens/token-valid? stored)
                       (assoc stored :token-source :stored-cache))
                     (refresh-token! db-spec client-id client-secret redirect-uri
                                     (:refresh_token stored) :stored (:refresh_token stored) can-refresh?)
                     (refresh-token! db-spec client-id client-secret redirect-uri
                                     env-refresh :env env-refresh can-refresh?)
                     (exchange-auth-code! db-spec client-id client-secret redirect-uri auth-code ci? can-refresh?)
                     (when (present? env-token)
                       {:access_token env-token
                        :token-source :env-token}))]
    (when (and (nil? resolved) (not can-refresh?))
      (log/warn {:event :oauth-credentials-missing
                 :provider :wahoo
                 :msg "WAHOO_CLIENT_ID and WAHOO_CLIENT_SECRET are required for automatic refresh"}))
    resolved))

(defn -main [& args]
  (try
    (let [start-date (first args)
          end-date (second args)]
      (when (or (nil? start-date) (nil? end-date))
        (throw (ex-info "Usage: bb -m training-personal-data.wahoo <start-date> <end-date>" {:args args})))
      (let [db-spec (db/make-db-spec (config/get-db-config))]
        (when-not (db/test-connection db-spec)
          (throw (ex-info "Failed to connect to database" {})))
        (let [token-map (resolve-access-token db-spec)
              access-token (:access_token token-map)]
          (when-not access-token
            (throw (ex-info "Unable to resolve Wahoo access token"
                            {:hint "Provide WAHOO_REFRESH_TOKEN (recommended) or WAHOO_TOKEN"})))
          (log/info {:event :start
                     :msg "Starting Wahoo workout sync"
                     :start start-date
                     :end end-date
                     :token_source (:token-source token-map)})
          (with-transient-retries 3
            (fn []
              (workout/fetch-and-save-by-date access-token start-date end-date db-spec)))
          (log/info {:event :complete :msg "Completed Wahoo workout sync"}))))
    (catch Exception e
      (let [data (ex-data e)
            safe-details (select-keys data [:status :body :hint])]
        (log/error {:event :error
                    :msg (ex-message e)
                    :status (:status safe-details)
                    :error_body (:body safe-details)
                    :hint (:hint safe-details)})
        (println "\n❌ Wahoo sync failed:" (ex-message e))
        (when (seq safe-details)
          (println "   Details:" safe-details)))
      (System/exit 1))))

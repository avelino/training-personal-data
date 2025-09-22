(ns training-personal-data.wahoo.tokens
  (:require [training-personal-data.db :as db]
            [cheshire.core :as json]
            [taoensso.timbre :as log]))

(def table-name "wahoo_oauth_tokens")
(def columns
  ["id" "provider" "access_token" "refresh_token" "expires_at_epoch" "raw_json"])

(def schema
  {:id [:text :primary-key]
   :provider :text
   :access_token :text
   :refresh_token :text
   :expires_at_epoch :bigint
   :raw_json :jsonb
   :created_at [:timestamp :default "CURRENT_TIMESTAMP"]})

(def ^:private primary-id "default")

(defn- unqualify-keys [m]
  (into {} (map (fn [[k v]] [(keyword (name k)) v]) m)))

(defn ensure-table! [db-spec]
  (db/create-table db-spec table-name schema))

(defn now-epoch []
  (quot (System/currentTimeMillis) 1000))

(defn- ->long [v]
  (when v
    (cond
      (number? v) (long v)
      (string? v) (try (Long/parseLong v)
                       (catch NumberFormatException _ nil))
      :else nil)))

(defn record->token [record]
  (when record
    (let [rec (unqualify-keys record)]
      {:id (:id rec)
       :provider (:provider rec)
       :access_token (:access_token rec)
       :refresh_token (:refresh_token rec)
       :expires_at_epoch (some-> (:expires_at_epoch rec) ->long)
       :raw_json (:raw_json rec)})))

(defn get-token [db-spec]
  (some-> (db/query db-spec [(str "SELECT id, provider, access_token, refresh_token, expires_at_epoch, raw_json "
                                   "FROM " table-name " WHERE id = ?")
                              primary-id])
          first
          record->token))

(defn delete-token! [db-spec]
  (log/warn {:event :wahoo-token-delete :msg "Removing stored Wahoo OAuth token"})
  (db/query db-spec [(str "DELETE FROM " table-name " WHERE id = ?") primary-id])
  nil)

(def default-expiry-seconds 3600)

(defn- compute-expiry-epoch [expires-in]
  (+ (now-epoch)
     (long (max 0 (or (some-> expires-in ->long) default-expiry-seconds)))))

(defn save-token!
  "Persist refreshed tokens. Expects a map that includes :access_token and either :expires_at_epoch
   or :expires_in. If the refresh endpoint omits :refresh_token we reuse fallback-refresh."
  [db-spec {:keys [access_token refresh_token expires_at_epoch expires_in raw_json raw] :as token}
   {:keys [fallback-refresh source]}]
  (when-not access_token
    (throw (ex-info "Missing Wahoo access_token" {:token token :source source})))
  (let [refresh-token (or refresh_token fallback-refresh)
        expires-epoch (or (some-> expires_at_epoch ->long)
                           (compute-expiry-epoch expires_in))
        raw-data (or raw raw_json token)
        record {:id primary-id
                :provider "wahoo"
                :access_token access_token
                :refresh_token refresh-token
                :expires_at_epoch expires-epoch
                :raw_json (when raw-data (json/generate-string raw-data))}
        values [(:id record)
                (:provider record)
                (:access_token record)
                (:refresh_token record)
                (:expires_at_epoch record)
                (:raw_json record)]]
    (when-not refresh-token
      (throw (ex-info "Missing Wahoo refresh_token" {:token token :source source})))
    (db/save db-spec table-name columns record values)
    (log/info {:event :wahoo-token-save
               :msg "Stored Wahoo OAuth tokens"
               :expires_at_epoch expires-epoch
               :source source})
    record))

(defn token-valid?
  ([token] (token-valid? token {:leeway 120}))
  ([{:keys [access_token expires_at_epoch]} {:keys [leeway] :or {leeway 120}}]
   (boolean
    (and access_token
         expires_at_epoch
         (> (long expires_at_epoch)
            (+ (now-epoch) (long leeway)))))))

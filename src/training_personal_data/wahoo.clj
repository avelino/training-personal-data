(ns training-personal-data.wahoo
  (:require [training-personal-data.wahoo.endpoints.workout.core :as workout]
            [training-personal-data.wahoo.api :as wahoo-api]
            [training-personal-data.db :as db]
            [training-personal-data.config :as config]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]))

(defn -main [& args]
  (try
    (let [raw-token (System/getenv "WAHOO_TOKEN")
          env-refresh (System/getenv "WAHOO_REFRESH_TOKEN")
          refresh-file (System/getenv "WAHOO_REFRESH_TOKEN_FILE")
          file-refresh (try
                         (when (.exists (io/file refresh-file))
                           (let [s (str/trim (slurp refresh-file))]
                             (when (seq s) s)))
                         (catch Exception _ nil))
          refresh-token (or env-refresh file-refresh)
          client-id (System/getenv "WAHOO_CLIENT_ID")
          client-secret (System/getenv "WAHOO_CLIENT_SECRET")
          token (cond
                  ;; Preferred: refresh flow
                  (and refresh-token client-id client-secret)
                  (let [resp (wahoo-api/refresh-access-token client-id client-secret refresh-token)
                        access (:access_token resp)
                        new-refresh (:refresh_token resp)]
                    (when new-refresh
                      (if (and refresh-file (not (str/blank? refresh-file)))
                        (try
                          (let [f (io/file refresh-file)
                                parent (.getParentFile f)]
                            (when parent (.mkdirs parent))
                            (spit f new-refresh))
                          (log/info {:event :oauth-refresh-rotate
                                     :msg "Persisted rotated refresh_token to file"
                                     :file refresh-file})
                          (catch Exception e
                            (log/error {:event :oauth-refresh-rotate-error
                                        :msg "Failed to persist rotated refresh_token"
                                        :error (ex-message e)
                                        :file refresh-file})))
                        (do
                          (log/warn {:event :oauth-refresh-rotate
                                     :msg "Refresh token rotated. Update your WAHOO_REFRESH_TOKEN environment variable for future runs."})
                          (println (str "UPDATE_ENV_WAHOO_REFRESH_TOKEN=" new-refresh)))))
                    access)

                  ;; First-run bootstrap with authorization code (disabled on CI/non-interactive)
                  (and (not (= (System/getenv "CI") "true"))
                       client-id client-secret (System/getenv "WAHOO_AUTH_CODE") (System/getenv "WAHOO_REDIRECT_URI"))
                  (let [code (System/getenv "WAHOO_AUTH_CODE")
                        redirect (System/getenv "WAHOO_REDIRECT_URI")
                        resp (wahoo-api/exchange-authorization-code client-id client-secret code redirect)
                        access (:access_token resp)
                        new-refresh (:refresh_token resp)]
                    (when new-refresh
                      (if (and refresh-file (not (str/blank? refresh-file)))
                        (try
                          (let [f (io/file refresh-file)
                                parent (.getParentFile f)]
                            (when parent (.mkdirs parent))
                            (spit f new-refresh))
                          (log/info {:event :oauth-authcode-persist
                                     :msg "Persisted initial refresh_token to file"
                                     :file refresh-file})
                          (catch Exception e
                            (log/error {:event :oauth-authcode-persist-error
                                        :msg "Failed to persist initial refresh_token"
                                        :error (ex-message e)
                                        :file refresh-file})))
                        (do
                          (log/warn {:event :oauth-authcode-persist
                                     :msg "Obtained refresh_token. Update your WAHOO_REFRESH_TOKEN environment variable for future runs."})
                          (println (str "UPDATE_ENV_WAHOO_REFRESH_TOKEN=" new-refresh)))))
                    access)

                  ;; Fallback: direct token
                  :else raw-token)
          start-date (first args)
          end-date (second args)
          db-spec (db/make-db-spec (config/get-db-config))]
      (when (or (nil? start-date) (nil? end-date))
        (throw (ex-info "Usage: bb -m training-personal-data.wahoo <start-date> <end-date>" {:args args})))
      (when (nil? token)
        (throw (ex-info "Missing credentials to obtain access token"
                        {:hint "Provide WAHOO_TOKEN, or WAHOO_REFRESH_TOKEN (or seed .secrets/wahoo_refresh_token), plus WAHOO_CLIENT_ID and WAHOO_CLIENT_SECRET"})))
      (log/info {:event :start :msg "Starting Wahoo workout sync" :start start-date :end end-date})
      (workout/fetch-and-save-by-date token start-date end-date db-spec)
      (log/info {:event :complete :msg "Completed Wahoo workout sync"}))
    (catch Exception e
      (log/error {:event :error :msg (ex-message e) :data (ex-data e)})
      (println "\n❌ Wahoo sync failed:" (ex-message e))
      (when-let [data (ex-data e)]
        (println "   Details:" data))
      (System/exit 1))))

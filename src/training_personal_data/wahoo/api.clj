(ns training-personal-data.wahoo.api
  "API client functions for Wahoo"
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as log]))

(def ^:private host "https://api.wahooligan.com/v1")
(def ^:private oauth-token-url "https://api.wahooligan.com/oauth/token")

(defn build-headers [token]
  {"Authorization" (str "Bearer " token)
   "Content-Type" "application/json"})

(defn parse-response [response]
  (let [{:keys [status body]} response]
    (if (= status 200)
      {:success? true
       :data (json/parse-string body true)}
      {:success? false
       :error {:status status
               :body body}})))

(defn refresh-access-token
  "Exchange a refresh token for a new access token (and refresh token).
  Returns a map {:access_token ... :refresh_token ... :expires_in ... :scope ...} on success.
  Throws ex-info on failure. Pass nil for redirect-uri if not required."
  [client-id client-secret refresh-token redirect-uri]
  (log/info {:event :oauth-refresh-start :msg "Refreshing Wahoo access token"})
  (let [base-params {"client_id" client-id
                     "client_secret" client-secret
                     "grant_type" "refresh_token"
                     "refresh_token" refresh-token}
        form-params (if (some? redirect-uri)
                      (assoc base-params "redirect_uri" redirect-uri)
                      base-params)
        response (http/post oauth-token-url
                            {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                             :form-params form-params})
        {:keys [status body]} response]
    (if (= status 200)
      (let [data (json/parse-string body true)
            {:keys [refresh_token expires_in token_type]} data]
        (log/info {:event :oauth-refresh-success
                   :msg "Refreshed Wahoo access token"
                   :expires_in expires_in
                   :token_type token_type
                   :has_refresh_token (boolean refresh_token)})
        data)
      (do
        (log/error {:event :oauth-refresh-error
                    :msg "Failed to refresh Wahoo access token"
                    :status status
                    :body body})
        (throw (ex-info "Failed to refresh Wahoo access token"
                        {:status status :body body}))))))

(defn exchange-authorization-code
  "Exchange an authorization code for access and refresh tokens.
  Returns {:access_token :refresh_token :expires_in ...} or throws ex-info on failure."
  [client-id client-secret code redirect-uri]
  (log/info {:event :oauth-authcode-start :msg "Exchanging authorization code for tokens"})
  (let [response (http/post oauth-token-url
                            {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                             :form-params {"client_id" client-id
                                           "client_secret" client-secret
                                           "code" code
                                           "redirect_uri" redirect-uri
                                           "grant_type" "authorization_code"}})
        {:keys [status body]} response]
    (if (= status 200)
      (let [data (json/parse-string body true)]
        (log/info {:event :oauth-authcode-success :msg "Obtained tokens via authorization code"})
        data)
      (do
        (log/error {:event :oauth-authcode-error
                    :msg "Failed to exchange authorization code"
                    :status status
                    :body body})
        (throw (ex-info "Failed to exchange authorization code"
                        {:status status :body body}))))))

(defn fetch-workouts-by-date
  [token start-date end-date]
  (log/info {:event :api-fetch :msg "Fetching workouts by date range"
             :start start-date :end end-date})
  (let [url (str host "/workouts")
        params {:start start-date
                :end end-date}
        response (-> url
                     (http/get {:headers (build-headers token)
                                :query-params params})
                     parse-response)]
    (if (:success? response)
      (do
        (log/info {:event :api-success
                   :msg "Successfully fetched workouts"
                   :count (count (:data response))})
        response)
      (do
        (log/error {:event :api-error
                    :msg "Failed to fetch workouts"
                    :status (get-in response [:error :status])})
        response))))
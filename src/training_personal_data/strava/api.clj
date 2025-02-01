(ns training-personal-data.strava.api
  "API client functions for Strava"
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as log]))

(def ^:private host "https://www.strava.com/api/v3")

(defn build-url [endpoint]
  (str host endpoint))

(defn build-headers [token]
  {"Authorization" (str "Bearer " token)})

(defn parse-response [response]
  (let [{:keys [status body]} response]
    (if (= status 200)
      {:success? true
       :data (if (string? body)
               (json/parse-string body true)
               body)}
      {:success? false
       :error {:status status
               :body body}})))

(defn fetch-activities [token page per-page]
  (log/info {:event :api-fetch :msg "Fetching Strava activities" :page page})
  (let [response (-> (str (build-url "/athlete/activities")
                         "?page=" page
                         "&per_page=" per-page)
                    (http/get {:headers (build-headers token)})
                    parse-response)]
    (if (:success? response)
      (do
        (log/info {:event :api-success :msg "Successfully fetched activities" :count (count (:data response))})
        response)
      (do
        (log/error {:event :api-error :msg "Failed to fetch activities" :status (get-in response [:error :status])})
        response))))
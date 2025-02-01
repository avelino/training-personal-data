(ns training-personal-data.wahoo.api
  "API client functions for Wahoo"
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as log]))

(def ^:private host "https://api.wahooligan.com/v1")

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

(defn fetch-workout [token workout-id]
  (log/info {:event :api-fetch :msg "Fetching workout" :workout-id workout-id})
  (let [url (str host "/workouts/" workout-id)
        response (-> url
                     (http/get {:headers (build-headers token)})
                     parse-response)]
    (if (:success? response)
      (do
        (log/info {:event :api-success :msg "Successfully fetched workout"})
        response)
      (do
        (log/error {:event :api-error :msg "Failed to fetch workout" :status (get-in response [:error :status])})
        response))))
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

(defn fetch-workouts-by-date [token start-date end-date]
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
(ns training-personal-data.ouraring.api
  "API client functions for Oura Ring"
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as log]))

(def ^:private host "https://api.ouraring.com/v2/usercollection")

(defn build-url [endpoint start-date end-date]
  (str host endpoint
       "?start_date=" start-date 
       "&end_date=" end-date))

(defn build-headers [token]
  {"Authorization" (str "Bearer " token)})

(defn parse-response [response]
  (let [{:keys [status body]} response]
    (if (= status 200)
      {:success? true 
       :data (-> body
                (json/parse-string true)
                :data)}
      {:success? false 
       :error {:status status 
               :body body}})))

(defn fetch-data [token endpoint start-date end-date]
  (log/info {:event :api-fetch :msg "Fetching data" :endpoint endpoint :start start-date :end end-date})
  (let [response (-> (build-url endpoint start-date end-date)
                    (http/get {:headers (build-headers token)})
                    parse-response)]
    (if (:success? response)
      (do
        (log/info {:event :api-success :msg "Successfully fetched records" :count (count (:data response))})
        response)
      (do
        (log/error {:event :api-error :msg "Failed to fetch data" :status (get-in response [:error :status])})
        response)))) 
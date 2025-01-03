(ns training-personal-data.ouraring
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]))

(def host "https://api.ouraring.com/v2")
(def daily-activity-endpoint "/usercollection/daily_activity")
(def token
  (or (System/getenv "OURA_TOKEN")
      (throw (ex-info "OURA_TOKEN environment variable not set" {}))))

(defn save-to-json
  "Saves Oura Ring data to a JSON file.
   Parameters:
   - data: The data to save (response body from Oura Ring API)
   - start-date: Start date in YYYY-MM-DD format used in filename
   - end-date: End date in YYYY-MM-DD format used in filename
   Returns the filename where data was saved."
  [data start-date end-date]
  (let [filename (format "ouraring-%s-%s.json" start-date end-date)]
    (spit filename data)
    filename))

(defn get-daily-activity
  "Retrieves daily activity data from Oura Ring API for a given date range.
   Parameters:
   - start-date: Start date in YYYY-MM-DD format
   - end-date: End date in YYYY-MM-DD format
   Returns the response body on success, throws exception on failure."
  [start-date end-date]
  (let [url (str host daily-activity-endpoint "?start_date=" start-date "&end_date=" end-date)
        response (http/get url {:headers {"Authorization" (str "Bearer " token)}})
        status (:status response)
        body (:body response)]
    (if (= status 200)
      body
      (throw (ex-info "Failed to get daily activity" {:status status :body body}))))) 

(defn -main
  "Main function that fetches daily activity data from Oura Ring API
   for a given date range passed as command line arguments.
   
   Usage:
   bb -m training-personal-data.ouraring 2024-01-01 2024-01-31
   
   Arguments:
   - start-date: Start date in YYYY-MM-DD format
   - end-date: End date in YYYY-MM-DD format
   
   Returns:
   - Prints the raw API data
   - Prints the path of the saved JSON file containing the data"
  [& args]
  (let [start-date (first args)
        end-date (second args)
        body (get-daily-activity start-date end-date)]
    (println body)
    (println "save file:" (save-to-json body start-date end-date))))
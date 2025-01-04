(ns training-personal-data.ouraring.api
  "Oura Ring API client functions for fetching and processing activity data"
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]))

(def ^:private host "https://api.ouraring.com/v2")
(def ^:private daily-activity-endpoint "/usercollection/daily_activity")
(def ^:private daily-sleep-endpoint "/usercollection/daily_sleep")

(defn build-url
  "Builds the API URL for fetching daily activity data.
   Parameters:
   - start-date: Start date in YYYY-MM-DD format
   - end-date: End date in YYYY-MM-DD format"
  [start-date end-date]
  (str host daily-activity-endpoint 
       "?start_date=" start-date 
       "&end_date=" end-date))

(defn build-headers
  "Creates HTTP headers required for API authentication.
   Parameters:
   - token: Oura Ring API token"
  [token]
  {"Authorization" (str "Bearer " token)})

(defn parse-response
  "Parses HTTP response from Oura Ring API.
   Parameters:
   - response: HTTP response map containing :status and :body"
  [response]
  (let [{:keys [status body]} response]
    (if (= status 200)
      {:success? true :data body}
      {:success? false :error {:status status :body body}})))

(defn fetch-daily-activity 
  "Fetches daily activity data from Oura Ring API.
   Parameters:
   - token: Oura Ring API token
   - start-date: Start date in YYYY-MM-DD format
   - end-date: End date in YYYY-MM-DD format"
  [token start-date end-date]
  (-> (build-url start-date end-date)
      (http/get {:headers (build-headers token)})
      parse-response))

(defn normalize-activity 
  "Normalizes raw API response into a database-friendly format.
   Converts the JSON response into a map with standardized keys
   and data types suitable for database storage.
   
   Parameters:
   - raw-data: Raw JSON string from API response"
  [raw-data]
  (let [activity (-> raw-data
                     (json/parse-string true)
                     :data
                     first)]
    {:date (:day activity)
     :class_5_min (json/generate-string (:class_5_min activity))
     :score (:score activity)
     :active_calories (:active_calories activity)
     :average_met (:average_met activity)
     :daily_movement (:daily_movement activity)
     :equivalent_walking_distance (:equivalent_walking_distance activity)
     :high_activity_met_minutes (:high_activity_met_minutes activity)
     :high_activity_time (:high_activity_time activity)
     :inactivity_alerts (:inactivity_alerts activity)
     :low_activity_met_minutes (:low_activity_met_minutes activity)
     :low_activity_time (:low_activity_time activity)
     :medium_activity_met_minutes (:medium_activity_met_minutes activity)
     :medium_activity_time (:medium_activity_time activity)
     :met (json/generate-string (:met activity))
     :meters_to_target (:meters_to_target activity)
     :non_wear_time (:non_wear_time activity)
     :resting_time (:resting_time activity)
     :sedentary_met_minutes (:sedentary_met_minutes activity)
     :sedentary_time (:sedentary_time activity)
     :steps (:steps activity)
     :target_calories (:target_calories activity)
     :target_meters (:target_meters activity)
     :total_calories (:total_calories activity)
     :day_summary (json/generate-string activity)}))

(defn fetch-daily-sleep
  "Fetches daily sleep data from Oura Ring API.
   Parameters:
   - token: Oura Ring API token
   - start-date: Start date in YYYY-MM-DD format
   - end-date: End date in YYYY-MM-DD format"
  [token start-date end-date]
  (-> (str host daily-sleep-endpoint 
           "?start_date=" start-date 
           "&end_date=" end-date)
      (http/get {:headers (build-headers token)})
      parse-response))

(defn normalize-sleep
  "Normalizes raw sleep API response into a database-friendly format.
   Parameters:
   - raw-data: Raw JSON string from API response
   Returns:
   - Map containing normalized sleep data with fields:
     :date - Date of sleep (YYYY-MM-DD)
     :id - Unique identifier
     :score - Sleep score
     :deep_sleep - Deep sleep contributor score
     :efficiency - Sleep efficiency contributor score
     :latency - Sleep latency contributor score
     :rem_sleep - REM sleep contributor score
     :restfulness - Restfulness contributor score
     :timing - Sleep timing contributor score
     :total_sleep - Total sleep contributor score
     :timestamp - Timestamp of the record
     :contributors - Original contributors data as map
     :raw_json - Complete sleep data as JSON string"
  [raw-data]
  (let [sleep (-> raw-data
                  (json/parse-string true)
                  :data
                  first)]
    {:date (:day sleep)
     :id (:id sleep)
     :score (:score sleep)
     :contributors (:contributors sleep)
     :timestamp (:timestamp sleep)})) 
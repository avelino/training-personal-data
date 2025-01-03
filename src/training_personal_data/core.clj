(ns training-personal-data.core
  (:require [training-personal-data.ouraring :as ouraring]))

(defn -main
  "Main function that fetches daily activity data from Oura Ring API
   for a specific period (December 1-31, 2024) and saves it to a JSON file.
   
   Returns:
   - Prints the raw API data
   - Prints the path of the saved JSON file containing the data"
  [& _args]
  (let [start-date "2024-12-01"
        end-date "2024-12-31"
        body (ouraring/get-daily-activity start-date end-date)]
    (println body)
    (println "save file:" (ouraring/save-to-json body start-date))))
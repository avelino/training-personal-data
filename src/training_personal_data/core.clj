(ns training-personal-data.core
  (:require [training-personal-data.ouraring :as ouraring]))

(defn -main
  "initial software here"
  [& _args]
  (let [start-date "2024-12-01"
        end-date "2024-12-31"
        body (ouraring/get-daily-activity start-date end-date)]
    (println body)
    (println "save file:" (ouraring/save-to-json body start-date end-date))))
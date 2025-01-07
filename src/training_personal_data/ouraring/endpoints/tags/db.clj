(ns training-personal-data.ouraring.endpoints.tags.db
  (:require [training-personal-data.ouraring.db :as db]
            [pod.babashka.postgresql :as pg]
            [clojure.string :as str]))

(def table-name "ouraring_tags")

(def columns
  ["id" "date" "text" "tags" "timestamp" "raw_json"])

(def schema
  {:id [:text :primary-key]
   :date [:date]
   :text :text
   :tags ["text[]"]
   :timestamp [:timestamp]
   :raw_json :jsonb
   :created_at [:timestamp :default "CURRENT_TIMESTAMP"]})

(defn clj-vector->pg-array [v]
  (when v
    (str "{" (str/join "," (map #(str "\"" % "\"") v)) "}")))

(defn extract-values [tag]
  [(:id tag)
   (:date tag)
   (:text tag)
   (clj-vector->pg-array (:tags tag))
   (:timestamp tag)
   (:raw_json tag)]) 
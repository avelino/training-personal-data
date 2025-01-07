(ns training-personal-data.ouraring.endpoints.tags.db
  (:require [training-personal-data.ouraring.db :as db]
            [pod.babashka.postgresql :as pg]
            [clojure.string :as str]))

(def table-name "ouraring_tags")

(def columns
  ["text" "tags" "timestamp" "raw_json"])

(def schema
  {:date [:date]
   :text :text
   :tags ["text[]"]
   :timestamp [:timestamp]
   :raw_json :text
   :created_at [:timestamp :default "CURRENT_TIMESTAMP"]
   :pk ["date" "timestamp" "text"]})

(defn record-exists? [db-spec date timestamp text]
  (-> (pg/execute! db-spec
                   [(str "SELECT EXISTS(SELECT 1 FROM " table-name 
                         " WHERE date = ?::date"
                         " AND timestamp = ?::timestamp"
                         " AND text = ?) AS exists") 
                    date timestamp text])
      first
      :exists))

(defn clj-vector->pg-array [v]
  (when v
    (str "{" (str/join "," (map #(str "\"" % "\"") v)) "}")))

(defn extract-values [tag]
  [(:text tag)
   (clj-vector->pg-array (:tags tag))
   (:timestamp tag)
   (:raw_json tag)]) 
(ns training-personal-data.export.roam.db
  (:require [cheshire.core :as json]
            [training-personal-data.db :as common-db]
            [pod.babashka.postgresql :as pg]
            [taoensso.timbre :as log]))

(def table-name "roam_exports")

(def columns
  ["id" "date" "page_uid" "block_uids" "content_hash" "metadata_json"])

(def schema
  {:id [:text :primary-key]
   :date [:date]
   :page_uid :text
   :block_uids :jsonb
   :content_hash :text
   :metadata_json :jsonb
   :last_synced_at [:timestamp :default "CURRENT_TIMESTAMP"]})

(defn ensure-table! [db-spec]
  (log/info {:event :db-create-table
             :table table-name
             :msg "Ensuring Roam export state table exists"})
  (common-db/create-table db-spec table-name schema))

(defn- encode-json [data]
  (when (some? data)
    (json/generate-string data)))

(defn extract-values [{:keys [id date page_uid block_uids content_hash metadata_json]}]
  [id
   date
   page_uid
   (encode-json block_uids)
   content_hash
   (encode-json metadata_json)])

(defn save-export! [db-spec record]
  (log/info {:event :db-save
             :table table-name
             :id (:id record)
             :msg "Persisting Roam export state"})
  (common-db/save db-spec table-name columns record (extract-values record)))

(defn get-export [db-spec export-id]
  (some-> (pg/execute! db-spec
                       [(str "SELECT * FROM " table-name " WHERE id = ?") export-id])
          first
          (update :block_uids #(some-> % (json/parse-string true)))
          (update :metadata_json #(some-> % (json/parse-string true)))))

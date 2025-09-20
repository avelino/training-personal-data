(ns training-personal-data.insights.db
  (:require [training-personal-data.db :as db]
            [pod.babashka.postgresql :as pg]
            [taoensso.timbre :as log]))

(def table-name "ouraring_weekly_insights")

(def columns
  ["id" "week_start" "week_end" "week_range" "avg_sleep_score"
   "avg_sleep_duration" "avg_sleep_quality" "avg_readiness_score"
   "avg_active_calories" "avg_activity_score" "gpt_analysis" "gpt_metrics_table"
   "gpt_cross_data_insight" "raw_data"])

(def schema
  {:id [:text :primary-key]
   :week_start [:date]
   :week_end [:date]
   :week_range :text
   :avg_sleep_score ["double precision"]
   :avg_sleep_duration ["double precision"]
   :avg_sleep_quality ["double precision"]
   :avg_readiness_score ["double precision"]
   :avg_active_calories ["double precision"]
   :avg_activity_score ["double precision"]
   :gpt_analysis :text
   :gpt_metrics_table :jsonb
   :gpt_cross_data_insight :text
   :raw_data :jsonb
   :timestamp [:timestamp :default "CURRENT_TIMESTAMP"]})

(defn ensure-table-exists [db-spec]
  (db/create-table db-spec table-name schema))

(defn extract-values [insight]
  [(str (:id insight))
   (:week_start insight)
   (:week_end insight)
   (str (:week_range insight))
   (:avg_sleep_score insight)
   (:avg_sleep_duration insight)
   (:avg_sleep_quality insight)
   (:avg_readiness_score insight)
   (:avg_active_calories insight)
   (:avg_activity_score insight)
   (str (:gpt_analysis insight))
   ;; Campo já é uma string JSON ou nil
   (:gpt_metrics_table insight)
   (str (:gpt_cross_data_insight insight))
   (:raw_data insight)])

(defn save-weekly-insight [db-spec insight]
  (log/info {:event :db-save-insight
             :msg "Saving weekly insight"
             :week-range (:week_range insight)})
  (db/save db-spec
           table-name
           columns
           insight
           (extract-values insight)))

(defn get-weekly-insight [db-spec week-start]
  (first (db/query db-spec [(str "SELECT * FROM " table-name " WHERE week_start = ?") week-start])))

(defn get-all-weekly-insights [db-spec]
  (db/query db-spec [(str "SELECT * FROM " table-name " ORDER BY week_start DESC")]))

(defn weekly-insight-exists? [db-spec week-start]
  (-> (pg/execute! db-spec
                   [(str "SELECT EXISTS(SELECT 1 FROM " table-name
                         " WHERE week_start = ?) AS exists")
                    week-start])
      first
      :exists))
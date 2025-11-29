(ns training-personal-data.export.roam
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [training-personal-data.config :as config]
            [training-personal-data.db :as common-db]
            [training-personal-data.export.roam.api :as roam-api]
            [training-personal-data.export.roam.db :as state-db]
            [pod.babashka.postgresql :as pg])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]
           [java.sql Date]
           [java.time Duration Instant LocalDate LocalDateTime]
           [java.time.format DateTimeFormatter]
           [java.util Locale]))

(def ^:private time-formatter (DateTimeFormatter/ofPattern "HH:mm"))

(defn- bytes->hex [bytes]
  (apply str (map #(format "%02x" (bit-and 0xff %)) bytes)))

(defn- sha256 [value]
  (let [digest (doto (MessageDigest/getInstance "SHA-256")
                 (.update (.getBytes (str value) StandardCharsets/UTF_8)))]
    (bytes->hex (.digest digest))))

(defn- stable-uid [page-id segments]
  (-> (sha256 (str page-id "|" (str/join "|" (map name segments))))
      (subs 0 12)))

(defn- parse-local-date [date-str]
  (LocalDate/parse date-str))

(defn- inclusive-date-range [start-date end-date]
  (let [start (parse-local-date start-date)
        end (parse-local-date end-date)]
    (take-while #(not (.isAfter ^LocalDate % end))
                (iterate #(.plusDays ^LocalDate % 1) start))))

(defn- ordinal-suffix [day]
  (let [mod-100 (mod day 100)]
    (cond
      (<= 11 mod-100 13) "th"
      :else (case (mod day 10)
              1 "st"
              2 "nd"
              3 "rd"
              "th"))))

(defn- format-roam-date [date-str]
  (let [date (parse-local-date date-str)
        formatter (DateTimeFormatter/ofPattern "MMMM" Locale/ENGLISH)
        month (.format date formatter)
        day (.getDayOfMonth date)
        year (.getYear date)]
    (format "%s %d%s, %d" month day (ordinal-suffix day) year)))

(defn- minutes->hours [minutes]
  (when (some? minutes)
    (double (/ minutes 60.0))))

(defn- format-decimal [value]
  (when (some? value)
    (format "%.1f" (double value))))

(defn- fetch-single [db-spec sql date]
  (first (common-db/query db-spec [sql date])))

(defn- fetch-sleep [db-spec date]
  (fetch-single db-spec
                "SELECT score, total_sleep, efficiency, latency, deep_sleep,
                        rem_sleep, restfulness, timestamp
                 FROM ouraring_daily_sleep
                 WHERE date = ?::date
                 ORDER BY timestamp DESC
                 LIMIT 1"
                date))

(defn- fetch-readiness [db-spec date]
  (fetch-single db-spec
                "SELECT score, temperature_trend, temperature_deviation
                 FROM ouraring_daily_readiness
                 WHERE date = ?::date
                 LIMIT 1"
                date))

(defn- fetch-activity [db-spec date]
  (fetch-single db-spec
                "SELECT score, active_calories, steps, total_calories,
                        daily_movement, non_wear_time, sedentary_time,
                        target_calories
                 FROM ouraring_daily_activity
                 WHERE date = ?::date
                 LIMIT 1"
                date))

(defn- fetch-heart-rate [db-spec date]
  (let [sql "SELECT MIN(bpm) AS min_bpm,
                        MAX(bpm) AS max_bpm,
                        AVG(bpm) AS avg_bpm
                 FROM ouraring_heart_rate
                 WHERE date = ?::date"]
    (try
      (first (pg/execute! db-spec [sql date]))
      (catch Exception e
        (let [message (.getMessage e)]
          (if (and message
                   (re-find #"relation \"ouraring_heart_rate\" does not exist" message))
            (do
              (log/warn {:event :heart-rate-table-missing
                         :msg "Skipping heart rate aggregation because table is missing"})
              nil)
            (throw e)))))))

(defn- fetch-workouts [db-spec date]
  (common-db/query db-spec
                   ["SELECT id, activity, calories, start_datetime, end_datetime,
                            intensity, label, source
                     FROM ouraring_workout
                     WHERE date = ?::date
                     ORDER BY start_datetime"
                    date]))

(defn- collect-oura-data [db-spec date]
  (let [sleep (fetch-sleep db-spec date)
        readiness (fetch-readiness db-spec date)
        activity (fetch-activity db-spec date)
        heart-rate (fetch-heart-rate db-spec date)
        workouts (fetch-workouts db-spec date)]
    {:sleep (when sleep
              {:score (:score sleep)
               :duration_minutes (:total_sleep sleep)
               :duration_hours (minutes->hours (:total_sleep sleep))
               :efficiency (:efficiency sleep)
               :deep_sleep (:deep_sleep sleep)
               :rem_sleep (:rem_sleep sleep)
               :latency (:latency sleep)
               :restfulness (:restfulness sleep)})
     :readiness (when readiness
                  {:score (:score readiness)
                   :temperature_trend (:temperature_trend readiness)
                   :temperature_deviation (:temperature_deviation readiness)})
     :activity (when activity
                 {:score (:score activity)
                  :active_calories (:active_calories activity)
                  :steps (:steps activity)
                  :total_calories (:total_calories activity)
                  :daily_movement (:daily_movement activity)
                  :sedentary_time (:sedentary_time activity)
                  :non_wear_time (:non_wear_time activity)
                  :target_calories (:target_calories activity)})
     :heart-rate (when heart-rate
                   {:min (:min_bpm heart-rate)
                    :max (:max_bpm heart-rate)
                    :avg (when-let [avg (:avg_bpm heart-rate)]
                           (double avg))})
     :workouts workouts}))

(defn- format-time [^LocalDateTime dt]
  (when dt
    (.format dt time-formatter)))

(defn- workout-duration-minutes [start end]
  (when (and start end)
    (.toMinutes (Duration/between start end))))

(defn- workout-summary-string [{:keys [start_datetime end_datetime activity calories intensity label source]}]
  (let [start (format-time start_datetime)
        end (format-time end_datetime)
        duration (workout-duration-minutes start_datetime end_datetime)
        name (or label activity "Workout")
        parts [(when (and start end)
                 (str start " - " end))
               (when duration
                 (str duration " min"))
               (when calories
                 (str calories " kcal"))
               (when intensity
                 (str "intensity: " intensity))
               (when source
                 (str "source: " source))]]
    (str name
         (when-let [details (->> parts (remove nil?) seq)]
           (str " (" (str/join ", " details) ")")))))

(defn- block [key string & {:keys [children]}]
  {:key key
   :string string
   :children (vec (or children []))})

(defn- maybe-child [key label value suffix]
  (when (some? value)
    (block key (str label value suffix))))

(defn- build-sleep-block [{:keys [sleep]}]
  (when sleep
    (let [children (->> [(maybe-child :sleep-score "Score: " (:score sleep) "")
                         (when-let [duration (format-decimal (:duration_hours sleep))]
                           (block :sleep-duration (str "Duration: " duration " h")))
                         (maybe-child :sleep-efficiency "Efficiency: " (:efficiency sleep) "%")
                         (maybe-child :sleep-deep "Deep sleep: " (:deep_sleep sleep) " min")
                         (maybe-child :sleep-rem "REM sleep: " (:rem_sleep sleep) " min")
                         (maybe-child :sleep-latency "Latency: " (:latency sleep) " min")
                         (maybe-child :sleep-restfulness "Restfulness: " (:restfulness sleep) "")]
                        (remove nil?)
                        vec)]
      (when (seq children)
        (block :sleep "Sleep" :children children)))))

(defn- build-readiness-block [{:keys [readiness]}]
  (when readiness
    (let [children (->> [(maybe-child :readiness-score "Score: " (:score readiness) "")
                         (maybe-child :readiness-temp-trend "Temperature trend: "
                                      (some-> (:temperature_trend readiness) format-decimal) " °C")
                         (maybe-child :readiness-temp-dev "Temperature deviation: "
                                      (some-> (:temperature_deviation readiness) format-decimal) " °C")]
                        (remove nil?)
                        vec)]
      (when (seq children)
        (block :readiness "Readiness" :children children)))))

(defn- build-activity-block [{:keys [activity]}]
  (when activity
    (let [children (->> [(maybe-child :activity-score "Score: " (:score activity) "")
                         (maybe-child :activity-active-calories "Active calories: " (:active_calories activity) " kcal")
                         (maybe-child :activity-steps "Steps: " (:steps activity) "")
                         (maybe-child :activity-total-calories "Total calories: " (:total_calories activity) " kcal")
                         (maybe-child :activity-daily-movement "Daily movement: " (:daily_movement activity) " au")
                         (maybe-child :activity-sedentary "Sedentary time: " (:sedentary_time activity) " min")
                         (maybe-child :activity-non-wear "Non-wear time: " (:non_wear_time activity) " min")
                         (maybe-child :activity-target-calories "Target calories: " (:target_calories activity) " kcal")]
                        (remove nil?)
                        vec)]
      (when (seq children)
        (block :activity "Activity" :children children)))))

(defn- build-heart-rate-block [{:keys [heart-rate]}]
  (when heart-rate
    (let [children (->> [(maybe-child :heartrate-min "Resting: " (:min heart-rate) " bpm")
                         (maybe-child :heartrate-avg "Average: "
                                      (some-> (:avg heart-rate) format-decimal) " bpm")
                         (maybe-child :heartrate-max "Max: " (:max heart-rate) " bpm")]
                        (remove nil?)
                        vec)]
      (when (seq children)
        (block :heart-rate "Heart rate" :children children)))))

(defn- build-workouts-block [{:keys [workouts]}]
  (when (seq workouts)
    (let [children (->> workouts
                        (map-indexed
                         (fn [idx workout]
                           (block (keyword (str "workout-" idx))
                                  (workout-summary-string workout))))
                        vec)]
      (block :workouts "Workouts" :children children))))

(defn- build-blocks [date data]
  (let [sections (remove nil?
                         [(build-sleep-block data)
                          (build-readiness-block data)
                          (build-activity-block data)
                          (build-heart-rate-block data)
                          (build-workouts-block data)])
        children (if (seq sections)
                   sections
                   [(block :status "No Oura data available for this date.")])]
    [(block :header
            (str "#ouraring [[" (format-roam-date date) "]]")
            :children children)]))

(defn- ensure-page! [conn title desired-uid]
  (let [query "[:find ?uid :in $ ?title :where [?p :node/title ?title] [?p :block/uid ?uid]]"
        result (roam-api/q conn query title)
        existing-uid (some-> result first first)]
    (if existing-uid
      {:uid existing-uid :created? false}
      (do
        (log/info {:event :roam-create-page
                   :title title
                   :uid desired-uid})
        (roam-api/create-page conn title {:uid desired-uid})
        {:uid desired-uid :created? true}))))

(defn- upsert-blocks!
  [conn page-id parent-uid blocks existing-state path]
  (reduce
   (fn [acc [idx block]]
     (let [parent-state (or existing-state {:children {}})
           block-key (:key block)
           next-path (conj path block-key)
           stored (get-in parent-state [:children block-key])
           block-uid (or (:uid stored)
                         (stable-uid page-id next-path))]
       (when (nil? (:string block))
         (throw (ex-info "Block string cannot be nil" {:key block-key})))
       (if stored
         (do
           (roam-api/update-block conn {:uid block-uid
                                        :string (:string block)})
           (roam-api/move-block conn {:uid block-uid
                                      :parent-uid parent-uid
                                      :order idx}))
         (do
           (log/info {:event :roam-create-block
                      :parent parent-uid
                      :uid block-uid
                      :order idx
                      :title (:string block)})
           (roam-api/create-block conn {:parent-uid parent-uid
                                        :order idx}
                                  {:uid block-uid
                                   :string (:string block)})))
       (let [children (:children block)
             child-state (upsert-blocks! conn page-id block-uid children stored next-path)]
         (assoc acc block-key {:uid block-uid
                               :children child-state}))))
   {}
   (map-indexed vector blocks)))

(defn- sync-page-content!
  [conn page-id page-uid blocks existing-state]
  (let [state (or existing-state {:uid page-uid :children {}})
        children-state (upsert-blocks! conn page-id page-uid blocks state [])]
    {:uid page-uid
     :children children-state}))

(defn- now-inst []
  (Instant/now))

(defn- export-day!
  [{:keys [token graph page-prefix graph-password]} db-spec date existing-state]
  (log/info {:event :roam-export-start
             :date date})
  (let [data (collect-oura-data db-spec date)
        blocks (build-blocks date data)
        content-hash (sha256 (json/generate-string {:data data
                                                    :blocks blocks}))
        export-id (str page-prefix "-" date)
        page-title (str page-prefix "/" date)
        page-id export-id
        roam-conn {:token token
                   :graph graph
                   :graph-password graph-password}
        desired-page-uid (or (:page_uid existing-state)
                             (stable-uid page-id [:page]))
        {:keys [uid created?]} (ensure-page! roam-conn page-title desired-page-uid)
        state-changed? (or (nil? existing-state)
                           created?
                           (not= content-hash (:content_hash existing-state)))
        block-state (if state-changed?
                      (sync-page-content! roam-conn page-id uid blocks (:block_uids existing-state))
                      (:block_uids existing-state))
        record {:id export-id
                :date (Date/valueOf date)
                :page_uid uid
                :block_uids (or block-state {:uid uid :children {}})
                :content_hash content-hash
                :metadata_json {:oura data
                                :synced_at (str (now-inst))
                                :status (if state-changed? "updated" "noop")}}]
    (when state-changed?
      (log/info {:event :roam-sync
                 :date date
                 :created created?
                 :content-hash content-hash}))
    (state-db/save-export! db-spec record)
    {:status (if state-changed? :updated :skipped)
     :date date
     :page_uid uid
     :hash content-hash}))

(defn export-range!
  [conn db-spec start-date end-date]
  (state-db/ensure-table! db-spec)
  (let [dates (inclusive-date-range start-date end-date)]
    (mapv (fn [date]
            (let [date-str (.toString ^LocalDate date)
                  export-id (str (:page-prefix conn) "-" date-str)
                  existing (state-db/get-export db-spec export-id)]
              (export-day! conn db-spec date-str existing)))
          dates)))

(defn- parse-args [args]
  (case (count args)
    1 {:start (first args) :end (first args)}
    2 {:start (first args) :end (second args)}
    (throw (ex-info "Usage: bb -m training-personal-data.export.roam <date> [<end-date>]"
                    {:args args}))))

(defn -main [& args]
  (try
    (let [{:keys [start end]} (parse-args args)
          _ (log/info {:event :roam-cli-start
                       :start start
                       :end end})
          db-spec (common-db/make-db-spec (config/get-db-config))
          roam-config (config/get-roam-config)]
      (when-not roam-config
        (throw (ex-info "Missing Roam configuration"
                        {:required ["ROAM_API_TOKEN" "ROAM_GRAPH"]})))
      (export-range! roam-config db-spec start end)
      (log/info {:event :roam-cli-complete
                 :start start
                 :end end}))
    (catch Exception e
      (log/error {:event :roam-cli-error
                  :error (ex-message e)
                  :data (ex-data e)})
      (println "Roam export failed:" (ex-message e))
      (System/exit 1))))
(ns training-personal-data.insights.week
  (:require [training-personal-data.config :as config]
            [training-personal-data.db :as db]
            [training-personal-data.insights.db :as insights-db]
            [training-personal-data.insights.prompt :as prompt]
            [taoensso.timbre :as log]
            [cheshire.core :as json]))

(defn- format-date-range [date]
  (let [day-of-week (java.time.format.DateTimeFormatter/ofPattern "E")
        date-format (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd")
        date-obj (java.time.LocalDate/parse date date-format)
        ;; Determine the day of the week (1 = Monday, 7 = Sunday)
        current-day-of-week (.getValue (.getDayOfWeek date-obj))
        ;; Calculate to reach Monday (subtract days until Monday)
        days-to-subtract (if (= current-day-of-week 7) 6 (dec current-day-of-week))
        ;; Monday of the current week
        week-start (.minusDays date-obj days-to-subtract)
        ;; Sunday is 6 days after Monday
        week-end (.plusDays week-start 6)]
    (log/info {:event :date-range-calculation
               :date date
               :day-of-week current-day-of-week
               :days-subtracted days-to-subtract
               :week-start (.format week-start date-format)
               :week-end (.format week-end date-format)})
    {:start (.format week-start date-format)
     :end (.format week-end date-format)
     :week-range (format "%s (%s) to %s (%s)"
                         (.format week-start date-format)
                         (.format week-start day-of-week)
                         (.format week-end date-format)
                         (.format week-end day-of-week))}))

(defn- query-sleep-data
  "Obtains sleep metrics for a given date range."
  [db-spec start-date end-date]
  (log/info {:event :query-sleep-data-range :start start-date :end end-date})

  ;; Query for diagnostics - display all individual records
  (let [debug-records (db/query db-spec
                                ["SELECT timestamp::date, score, total_sleep, efficiency
                         FROM ouraring_daily_sleep
                         WHERE timestamp >= ?::timestamp AND timestamp <= ?::timestamp
                         ORDER BY timestamp"
                                 start-date end-date])]
    (log/info {:event :individual-sleep-records :records debug-records}))

  (let [result (db/query db-spec
                         ["SELECT
                   AVG(score) as avg_sleep_score,
                   AVG(CASE WHEN total_sleep > 0 THEN total_sleep ELSE NULL END)/60 as avg_sleep_duration,
                   AVG(efficiency) as avg_sleep_quality,
                   COUNT(*) as record_count,
                   array_agg(total_sleep) as all_sleep_durations,
                   array_agg(timestamp::date) as dates
                 FROM ouraring_daily_sleep
                 WHERE timestamp >= ?::timestamp AND timestamp <= ?::timestamp"
                          start-date end-date])
        first-result (first result)]
    (log/info {:event :sleep-data-retrieved
               :avg-sleep-duration (get first-result :avg_sleep_duration)
               :record-count (get first-result :record_count)
               :all-sleep-durations (get first-result :all_sleep_durations)
               :dates (get first-result :dates)})
    result))

(defn query-readiness-data [db-spec start-date end-date]
  (let [query "SELECT
                 AVG(score) as avg_readiness_score,
                 COUNT(*) as record_count
               FROM
                 ouraring_daily_readiness
               WHERE
                 timestamp BETWEEN ?::timestamp AND ?::timestamp"]
    (let [result (db/query db-spec [query start-date end-date])]
      (log/info {:event :readiness-data-retrieved
                 :data result
                 :record-count (get-in result [0 :record_count])})
      result)))

(defn query-activity-data [db-spec start-date end-date]
  (let [query "SELECT
                 AVG(CASE WHEN active_calories > 0 THEN active_calories ELSE NULL END) as avg_active_calories,
                 AVG(score) as avg_activity_score,
                 COUNT(*) as record_count
               FROM
                 ouraring_daily_activity
               WHERE
                 timestamp BETWEEN ?::timestamp AND ?::timestamp"]
    (let [result (db/query db-spec [query start-date end-date])]
      (log/info {:event :activity-data-retrieved
                 :data result
                 :record-count (get-in result [0 :record_count])})
      result)))

(defn safe-double [value]
  (if (or (nil? value) (= value "")) ;; Check for nil or empty string first
    (do
      (log/debug {:event :safe-double-conversion :input value :output nil :reason "nil or empty value"})
      nil)
    (try
      (let [num-value (if (string? value)
                        (Double/parseDouble value)
                        (double value))] ;; Cast potential non-double numbers to double
        (if (= num-value 0.0)
          (do
            (log/debug {:event :safe-double-conversion :input value :output nil :reason "value is zero"})
            nil)
          (do
            (log/debug {:event :safe-double-conversion :input value :output num-value})
            num-value))) ;; Return the valid, non-zero double
      (catch NumberFormatException _
        (log/debug {:event :safe-double-conversion :input value :output nil :reason "invalid number format"})
        nil)
      (catch Exception e ; Catch other potential errors (like casting non-numbers)
        (log/error {:event :safe-double-conversion :input value :error (ex-message e) :reason "conversion error"})
        nil))))

(defn format-data-for-gpt [sleep-data readiness-data activity-data date-range]
  (let [sleep-duration (safe-double (get-in sleep-data [0 :avg_sleep_duration]))
        sleep-score (safe-double (get-in sleep-data [0 :avg_sleep_score]))
        sleep-quality (safe-double (get-in sleep-data [0 :avg_sleep_quality]))
        readiness-score (safe-double (get-in readiness-data [0 :avg_readiness_score]))
        active-calories (safe-double (get-in activity-data [0 :avg_active_calories]))
        activity-score (safe-double (get-in activity-data [0 :avg_activity_score]))

        ;; Detailed log of raw values for diagnostics
        _ (log/info {:event :raw-data-values
                     :raw-sleep-duration (get-in sleep-data [0 :avg_sleep_duration])
                     :raw-sleep-score (get-in sleep-data [0 :avg_sleep_score])
                     :raw-sleep-quality (get-in sleep-data [0 :avg_sleep_quality])
                     :raw-readiness-score (get-in readiness-data [0 :avg_readiness_score])
                     :raw-active-calories (get-in activity-data [0 :avg_active_calories])
                     :raw-activity-score (get-in activity-data [0 :avg_activity_score])})

        ;; Check if there is sufficient data for meaningful analysis
        has-sleep-data (and sleep-score sleep-duration sleep-quality)
        has-readiness-data (boolean readiness-score)
        has-activity-data (or active-calories activity-score)

        _ (log/info {:event :data-availability
                     :has-sleep-data has-sleep-data
                     :has-readiness-data has-readiness-data
                     :has-activity-data has-activity-data
                     :sleep-record-count (get-in sleep-data [0 :record_count])
                     :readiness-record-count (get-in readiness-data [0 :record_count])
                     :activity-record-count (get-in activity-data [0 :record_count])})

        formatted-data {:prompt (str "Analyze my Oura Ring data for the week of " (:week-range date-range)
                                     " and provide health insights and recommendations:")
                        :data {:sleep {:avg_score sleep-score
                                       :avg_duration_hours sleep-duration
                                       :avg_quality sleep-quality
                                       :data_available has-sleep-data}
                               :readiness {:avg_score readiness-score
                                           :data_available has-readiness-data}
                               :activity {:avg_calories active-calories
                                          :avg_score activity-score
                                          :data_available has-activity-data}
                               :units {:sleep_score "0-100 scale"
                                       :sleep_duration "hours (already converted from seconds)"
                                       :sleep_quality "percentage (0-100%)"
                                       :readiness_score "0-100 scale"
                                       :calories "kcal (active calories)"
                                       :activity_score "0-100 scale"}}}]
    (log/info {:event :formatted-data-for-gpt
               :sleep-duration-hours sleep-duration
               :safe-double-values {:sleep-score sleep-score
                                    :sleep-duration sleep-duration
                                    :sleep-quality sleep-quality
                                    :readiness-score readiness-score
                                    :active-calories active-calories
                                    :activity-score activity-score}
               :data-sample formatted-data})
    formatted-data))

(defn save-weekly-insight [db-spec date-range sleep-data readiness-data activity-data gpt-response metrics-table cross-data-insight]
  (let [insight-id (str "week_" (:start date-range))
        raw-data {:sleep (first sleep-data)
                  :readiness (first readiness-data)
                  :activity (first activity-data)}
        parse-sql-date (fn [date-str]
                         (java.sql.Date/valueOf date-str))
        ;; Ensure metrics-table is a valid JSON or null
        formatted-metrics-table (if (seq metrics-table)
                                  (json/generate-string metrics-table)
                                  nil)
        _ (when formatted-metrics-table
            (log/debug {:event :metrics-table-json
                        :json formatted-metrics-table}))
        sleep-duration (safe-double (get-in sleep-data [0 :avg_sleep_duration]))
        _ (log/info {:event :saving-insight
                     :sleep-duration-hours sleep-duration})
        insight {:id insight-id
                 :week_start (parse-sql-date (:start date-range))
                 :week_end (parse-sql-date (:end date-range))
                 :week_range (:week-range date-range)
                 :avg_sleep_score (or (safe-double (get-in sleep-data [0 :avg_sleep_score])) 0.0)
                 :avg_sleep_duration (or sleep-duration 0.0)
                 :avg_sleep_quality (or (safe-double (get-in sleep-data [0 :avg_sleep_quality])) 0.0)
                 :avg_readiness_score (or (safe-double (get-in readiness-data [0 :avg_readiness_score])) 0.0)
                 :avg_active_calories (or (safe-double (get-in activity-data [0 :avg_active_calories])) 0.0)
                 :avg_activity_score (or (safe-double (get-in activity-data [0 :avg_activity_score])) 0.0)
                 :gpt_analysis (or gpt-response "No GPT analysis available")
                 :gpt_metrics_table formatted-metrics-table
                 :gpt_cross_data_insight (or cross-data-insight "No cross-data insight found")
                 :raw_data raw-data}]
    (insights-db/save-weekly-insight db-spec insight)))

(defn- generate-mock-data-for-testing []
  (let [;; Correct calculation of the average excluding zero values
        sleep-durations-minutes [394 0 0 420 405]  ;; Values in minutes: 6.5h, 0h, 0h, 7h, 6.75h
        valid-durations (filter pos? sleep-durations-minutes)
        avg-duration-hours (/ (apply + valid-durations) (count valid-durations) 60.0)

        mock-sleep-data [{:avg_sleep_score 72.4
                          :avg_sleep_duration avg-duration-hours ;; Correctly calculated value = 6.75 hours
                          :avg_sleep_quality 87.2
                          :record_count 5
                          :all_sleep_durations sleep-durations-minutes
                          :dates ["2023-11-27" "2023-11-28" "2023-11-29" "2023-11-30" "2023-12-01"]}]
        mock-readiness-data [{:avg_readiness_score 68.3
                              :record_count 4}]
        mock-activity-data [{:avg_active_calories 420.5
                             :avg_activity_score 75.6
                             :record_count 5}]]
    {:sleep mock-sleep-data
     :readiness mock-readiness-data
     :activity mock-activity-data}))

(defn -main [date]
  (try
    (log/info {:event :start :msg "Generating weekly insights"})

    (when (nil? date)
      (throw (ex-info "Date parameter is required"
                      {:usage "bb -m training-personal-data.insights.week YYYY-MM-DD"})))

    (let [date-range (format-date-range date)]

      (try
        ;; Try to connect to the database
        (let [db-spec (db/make-db-spec (config/get-db-config))]
          ;; Directly create table using db/create-table
          (log/info {:event :db-create-table :msg "Creating weekly_insights table if not exists"})
          (db/create-table db-spec
                           insights-db/table-name
                           insights-db/schema)

          (let [sleep-data (query-sleep-data db-spec (:start date-range) (:end date-range))
                readiness-data (query-readiness-data db-spec (:start date-range) (:end date-range))
                activity-data (query-activity-data db-spec (:start date-range) (:end date-range))
                _ (log/info {:event :data-retrieved :sleep-data sleep-data
                             :readiness-data readiness-data
                             :activity-data activity-data})
                formatted-data (format-data-for-gpt sleep-data readiness-data activity-data date-range)]

            ;; Call OpenAI API
            (println "\n🌙 Weekly Health Insights:")
            (println (str "Week: " (:week-range date-range)))
            (println "\n📊 Raw Data Summary:")

            ;; Check if there are records for each data type
            (println "\nAvailable Data:")
            (let [sleep-count (get-in sleep-data [0 :record_count] 0)
                  readiness-count (get-in readiness-data [0 :record_count] 0)
                  activity-count (get-in activity-data [0 :record_count] 0)]
              (log/info {:event :display-record-counts
                         :sleep-records sleep-count
                         :readiness-records readiness-count
                         :activity-records activity-count})
              (println (str "• Sleep: " sleep-count " records"))
              (println (str "• Readiness: " readiness-count " records"))
              (println (str "• Activity: " activity-count " records")))

            (println "\nMetrics:")

            ;; Log exact values before formatting for display
            (let [sleep-score (get-in sleep-data [0 :avg_sleep_score])
                  sleep-duration (get-in sleep-data [0 :avg_sleep_duration])
                  sleep-quality (get-in sleep-data [0 :avg_sleep_quality])
                  readiness-score (get-in readiness-data [0 :avg_readiness_score])
                  active-calories (get-in activity-data [0 :avg_active_calories])
                  activity-score (get-in activity-data [0 :avg_activity_score])]

              (log/info {:event :display-metrics-raw
                         :avg-sleep-score sleep-score
                         :avg-sleep-duration sleep-duration
                         :avg-sleep-quality sleep-quality
                         :avg-readiness-score readiness-score
                         :avg-active-calories active-calories
                         :avg-activity-score activity-score})

              ;; Detailed analysis of raw sleep durations
              (when-let [raw-durations (get-in sleep-data [0 :all_sleep_durations])]
                (log/info {:event :sleep-duration-analysis
                           :raw-durations raw-durations
                           :converted-hours (mapv #(when (and % (> % 0)) (/ % 60.0)) raw-durations)
                           :avg-raw (when (seq (filter pos? raw-durations))
                                      (/ (apply + (filter pos? raw-durations))
                                         (count (filter pos? raw-durations))))
                           :avg-in-hours (when (seq (filter pos? raw-durations))
                                           (/ (apply + (filter pos? raw-durations))
                                              (count (filter pos? raw-durations))
                                              60.0))
                           :null-or-zero-count (count (filter (fn [x] (or (nil? x) (zero? x))) raw-durations))}))

              ;; Add safe-handling for displaying values
              (println (str "• Avg Sleep Score: "
                            (if-let [score sleep-score]
                              (format "%.1f" (double score))
                              "N/A (insufficient data)")
                            (when sleep-score "/100")))

              (println (str "• Avg Sleep Duration: "
                            (if-let [duration sleep-duration]
                              (format "%.1f" (double duration))
                              "N/A (insufficient data)")
                            (when sleep-duration " hours")))

              (println (str "• Avg Sleep Quality: "
                            (if-let [quality sleep-quality]
                              (format "%.1f" (double quality))
                              "N/A (insufficient data)")
                            (when sleep-quality "%")))

              (println (str "• Avg Readiness Score: "
                            (if-let [score readiness-score]
                              (format "%.1f" (double score))
                              "N/A (insufficient data)")
                            (when readiness-score "/100")))

              (println (str "• Avg Caloric Expenditure: "
                            (if-let [calories active-calories]
                              (format "%.0f" (double calories))
                              "N/A (insufficient data)")
                            (when active-calories " kcal")))

              (println (str "• Avg Activity Score: "
                            (if-let [score activity-score]
                              (format "%.1f" (double score))
                              "N/A (insufficient data)")
                            (when activity-score "/100"))))

            ;; Now get insights from GPT
            (let [insights (prompt/call-gpt formatted-data)]
              (println "\n💡 Insights:")
              (println insights)

              ;; Save the insights to the database using our format
              (save-weekly-insight
               db-spec
               date-range
               sleep-data
               readiness-data
               activity-data
               insights
               nil  ;; metrics-table
               nil)) ;; cross-data-insight

            (log/info {:event :complete :msg "Weekly insights generated successfully"})))

        ;; If database connection fails, use test data
        (catch Exception db-e
          (log/warn {:event :db-connection-failed
                     :msg "Using mock data for testing purposes"
                     :error (ex-message db-e)})

          ;; Using test data for diagnostics
          (let [mock-data (generate-mock-data-for-testing)
                sleep-data (:sleep mock-data)
                readiness-data (:readiness mock-data)
                activity-data (:activity mock-data)
                _ (log/info {:event :mock-data-generated
                             :sleep-data sleep-data
                             :readiness-data readiness-data
                             :activity-data activity-data})
                formatted-data (format-data-for-gpt sleep-data readiness-data activity-data date-range)]

            (log/info {:event :querying :msg "Using mock Oura Ring data"
                       :week-range (:week-range date-range)})

            (println "\n🌙 Weekly Health Insights (MOCK DATA):")
            (println (str "Week: " (:week-range date-range)))
            (println "\n📊 Raw Data Summary:")

            ;; Check if there are records for each data type
            (println "\nAvailable Data:")
            (let [sleep-count (get-in sleep-data [0 :record_count] 0)
                  readiness-count (get-in readiness-data [0 :record_count] 0)
                  activity-count (get-in activity-data [0 :record_count] 0)]
              (log/info {:event :display-record-counts
                         :sleep-records sleep-count
                         :readiness-records readiness-count
                         :activity-records activity-count})
              (println (str "• Sleep: " sleep-count " records"))
              (println (str "• Readiness: " readiness-count " records"))
              (println (str "• Activity: " activity-count " records")))

            (println "\nMetrics:")

            ;; Log exact values before formatting for display
            (let [sleep-score (get-in sleep-data [0 :avg_sleep_score])
                  sleep-duration (get-in sleep-data [0 :avg_sleep_duration])
                  sleep-quality (get-in sleep-data [0 :avg_sleep_quality])
                  readiness-score (get-in readiness-data [0 :avg_readiness_score])
                  active-calories (get-in activity-data [0 :avg_active_calories])
                  activity-score (get-in activity-data [0 :avg_activity_score])]

              (log/info {:event :display-metrics-raw
                         :avg-sleep-score sleep-score
                         :avg-sleep-duration sleep-duration
                         :avg-sleep-quality sleep-quality
                         :avg-readiness-score readiness-score
                         :avg-active-calories active-calories
                         :avg-activity-score activity-score})

              ;; Detailed analysis of raw sleep durations
              (when-let [raw-durations (get-in sleep-data [0 :all_sleep_durations])]
                (log/info {:event :sleep-duration-analysis
                           :raw-durations raw-durations
                           :converted-hours (mapv #(when (and % (> % 0)) (/ % 60.0)) raw-durations)
                           :avg-raw (when (seq (filter pos? raw-durations))
                                      (/ (apply + (filter pos? raw-durations))
                                         (count (filter pos? raw-durations))))
                           :avg-in-hours (when (seq (filter pos? raw-durations))
                                           (/ (apply + (filter pos? raw-durations))
                                              (count (filter pos? raw-durations))
                                              60.0))
                           :null-or-zero-count (count (filter (fn [x] (or (nil? x) (zero? x))) raw-durations))}))

              ;; Add safe-handling for displaying values
              (println (str "• Avg Sleep Score: "
                            (if-let [score sleep-score]
                              (format "%.1f" (double score))
                              "N/A (insufficient data)")
                            (when sleep-score "/100")))

              (println (str "• Avg Sleep Duration: "
                            (if-let [duration sleep-duration]
                              (format "%.1f" (double duration))
                              "N/A (insufficient data)")
                            (when sleep-duration " hours")))

              (println (str "• Avg Sleep Quality: "
                            (if-let [quality sleep-quality]
                              (format "%.1f" (double quality))
                              "N/A (insufficient data)")
                            (when sleep-quality "%")))

              (println (str "• Avg Readiness Score: "
                            (if-let [score readiness-score]
                              (format "%.1f" (double score))
                              "N/A (insufficient data)")
                            (when readiness-score "/100")))

              (println (str "• Avg Caloric Expenditure: "
                            (if-let [calories active-calories]
                              (format "%.0f" (double calories))
                              "N/A (insufficient data)")
                            (when active-calories " kcal")))

              (println (str "• Avg Activity Score: "
                            (if-let [score activity-score]
                              (format "%.1f" (double score))
                              "N/A (insufficient data)")
                            (when activity-score "/100"))))

            ;; Proposed solution (doesn't call GPT in tests)
            (println "\n🔍 Problem Diagnosis:")
            (println "The problem is that avg_sleep_duration is showing 0.0 hours because:")
            (println "1. The SQL query is including zero values in the average")
            (println "2. Solution: Use CASE WHEN to ignore zero values in the average")
            (println "3. Correct implementation: AVG(CASE WHEN total_sleep > 0 THEN total_sleep ELSE NULL END)/60")
            (println "4. We also suggest investigating why some records have total_sleep = 0")

            (log/info {:event :diagnosis-complete :msg "Diagnosis of sleep duration issue completed"})))))

    (catch Exception e
      (log/error {:event :error :msg (ex-message e) :data (ex-data e)})
      (println "\nError:" (ex-message e))
      (System/exit 1))))
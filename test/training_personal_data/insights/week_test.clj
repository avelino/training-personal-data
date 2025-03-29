(ns training-personal-data.insights.week-test
  (:require [clojure.test :refer [deftest is testing]]
            [training-personal-data.insights.week :as week]))

(def mock-sleep-data
  [{:avg_sleep_score 72.4
    :avg_sleep_duration 6.75 ; Calculated from [394 420 405] / 3 / 60
    :avg_sleep_quality 87.2
    :record_count 5
    :all_sleep_durations [394 0 0 420 405]
    :dates ["2023-11-27" "2023-11-28" "2023-11-29" "2023-11-30" "2023-12-01"]}])

(def mock-readiness-data
  [{:avg_readiness_score 68.3
    :record_count 4}])

(def mock-activity-data
  [{:avg_active_calories 420.5
    :avg_activity_score 75.6
    :record_count 5}])

(def mock-date-range
  {:start "2023-11-27"
   :end "2023-12-03"
   :week-range "2023-11-27 (Mon) to 2023-12-03 (Sun)"})

(deftest format-data-for-gpt-test
  (testing "Correctly formats data for GPT prompt"
    (let [formatted-data (#'week/format-data-for-gpt mock-sleep-data mock-readiness-data mock-activity-data mock-date-range)]
      (is (string? (:prompt formatted-data)))
      (is (= (:prompt formatted-data) "Analyze my Oura Ring data for the week of 2023-11-27 (Mon) to 2023-12-03 (Sun) and provide health insights and recommendations:"))
      (is (map? (:data formatted-data)))
      (is (= (get-in formatted-data [:data :sleep :avg_duration_hours]) 6.75))
      (is (= (get-in formatted-data [:data :sleep :avg_score]) 72.4))
      (is (= (get-in formatted-data [:data :sleep :avg_quality]) 87.2))
      (is (:data_available (get-in formatted-data [:data :sleep])))
      (is (= (get-in formatted-data [:data :readiness :avg_score]) 68.3))
      (is (:data_available (get-in formatted-data [:data :readiness])))
      (is (= (get-in formatted-data [:data :activity :avg_calories]) 420.5))
      (is (= (get-in formatted-data [:data :activity :avg_score]) 75.6))
      (is (:data_available (get-in formatted-data [:data :activity])))
      (is (map? (get-in formatted-data [:data :units]))))))

(deftest safe-double-test
  (testing "Handles valid and invalid double conversions"
    (is (= (#'week/safe-double 10.5) 10.5))
    (is (= (#'week/safe-double "25.3") 25.3))
    (is (nil? (#'week/safe-double 0)))
    (is (nil? (#'week/safe-double "")))
    (is (nil? (#'week/safe-double nil)))))

(deftest format-date-range-test
  (testing "Calculates correct week start and end dates"
    (let [result (#'week/format-date-range "2023-12-01")] ; Friday
      (is (= (:start result) "2023-11-27"))
      (is (= (:end result) "2023-12-03"))
      (is (clojure.string/includes? (:week-range result) "2023-11-27 (Mon) to 2023-12-03 (Sun)")))
    (let [result (#'week/format-date-range "2024-01-01")] ; Monday
      (is (= (:start result) "2024-01-01"))
      (is (= (:end result) "2024-01-07")))
    (let [result (#'week/format-date-range "2024-01-07")] ; Sunday
      (is (= (:start result) "2024-01-01"))
      (is (= (:end result) "2024-01-07")))))
(ns training-personal-data.ouraring.endpoints.activity.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.activity.core :as core]
            [training-personal-data.ouraring.endpoints.activity.api :as api]
            [training-personal-data.ouraring.endpoints.activity.db :as activity-db]
            [training-personal-data.db :as db]))

(def sample-api-response
  {:success? true
   :data [{:id "123"
           :day "2024-01-07"
           :score 85
           :timestamp "2024-01-07T08:00:00+00:00"
           :class_5_min [1 2 3]
           :met {:interval [1.2 1.3 1.4]
                 :detailed [1.1 1.2 1.3]}
           :active_calories 500
           :average_met 1.5
           :daily_movement 1000
           :equivalent_walking_distance 5000
           :high_activity_met_minutes 30
           :high_activity_time 1800
           :inactivity_alerts 2
           :low_activity_met_minutes 120
           :low_activity_time 7200
           :medium_activity_met_minutes 60
           :medium_activity_time 3600
           :meters_to_target 2000
           :non_wear_time 1800
           :resting_time 28800
           :sedentary_met_minutes 480
           :sedentary_time 28800
           :steps 10000
           :target_calories 600
           :target_meters 8000
           :total_calories 2400}]})

(defn mock-fetch [token endpoint start-date end-date]
  sample-api-response)

(def saved-records (atom []))

(defn mock-save [_ table-name columns record values]
  (swap! saved-records conj record)
  {:success true})

(defn mock-create-table [_ table-name schema]
  {:success true})

(deftest test-fetch-and-save
  (testing "fetch and save activity data using refactored pipeline"
    (reset! saved-records [])
    (with-redefs [training-personal-data.ouraring.api/fetch-data mock-fetch
                  training-personal-data.db/save mock-save
                  training-personal-data.db/create-table mock-create-table]
      ;; Execute fetch-and-save with new pipeline
      (core/fetch-and-save "test-token" "2024-01-07" "2024-01-08" {})

      ;; Verify data was saved
      (let [saved-record (first @saved-records)]
        (is (some? saved-record))
        (is (= "123" (:id saved-record)))
        (is (= 85 (:score saved-record)))
        (is (= "[1,2,3]" (:class_5_min saved-record)))
        (is (= 500 (:active_calories saved-record)))
        (is (= 1.5 (:average_met saved-record)))
        (is (some? (:met saved-record)))
        (is (some? (:day_summary saved-record)))
        (is (some? (:raw_json saved-record)))))))

(deftest test-activity-config
  (testing "activity endpoint configuration"
    (let [config core/activity-config]
      (is (= "activity" (:name config)))
      (is (= activity-db/table-name (:table-name config)))
      (is (= activity-db/columns (:columns config)))
      (is (= activity-db/schema (:schema config)))
      (is (fn? (:fetch-fn config)))
      (is (fn? (:normalize-fn config)))
      (is (fn? (:extract-values-fn config))))))

(deftest test-fetch-and-save-batch
  (testing "batch processing for activity data"
    (reset! saved-records [])
    (with-redefs [training-personal-data.ouraring.endpoints.activity.api/fetch mock-fetch
                  training-personal-data.db/save mock-save
                  training-personal-data.db/create-table mock-create-table]
      ;; Execute batch fetch-and-save
      (core/fetch-and-save-batch "test-token" "2024-01-07" "2024-01-08" {} :batch-size 1)

      ;; Verify data was saved
      (let [saved-record (first @saved-records)]
        (is (some? saved-record))
        (is (= "123" (:id saved-record)))))))

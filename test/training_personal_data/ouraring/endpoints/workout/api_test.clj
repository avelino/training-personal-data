(ns training-personal-data.ouraring.endpoints.workout.api-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.workout.api :as api]
            [cheshire.core :as json]))

(def sample-workout-data
  {:id "123"
   :day "2024-01-07"
   :activity "running"
   :calories 500
   :day_id "2024-01-07"
   :distance 5000
   :end_datetime "2024-01-07T09:00:00+00:00"
   :intensity "moderate"
   :label "Morning Run"
   :source "manual"
   :start_datetime "2024-01-07T08:00:00+00:00"})

(deftest test-normalize
  (testing "normalize workout data"
    (let [normalized (api/normalize sample-workout-data)]
      (is (= "123" (:id normalized)))
      (is (= "2024-01-07" (:date normalized)))
      (is (= "running" (:activity normalized)))
      (is (= 500 (:calories normalized)))
      (is (= "2024-01-07" (:day_id normalized)))
      (is (= 5000 (:distance normalized)))
      (is (= "2024-01-07T09:00:00+00:00" (:end_datetime normalized)))
      (is (= "moderate" (:intensity normalized)))
      (is (= "Morning Run" (:label normalized)))
      (is (= "manual" (:source normalized)))
      (is (= "2024-01-07T08:00:00+00:00" (:start_datetime normalized)))
      (is (= (json/generate-string sample-workout-data)
             (:raw_json normalized)))))) 
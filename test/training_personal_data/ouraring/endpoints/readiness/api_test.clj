(ns training-personal-data.ouraring.endpoints.readiness.api-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.readiness.api :as api]
            [cheshire.core :as json]))

(def sample-readiness-data
  {:id "123"
   :day "2024-01-07"
   :score 85
   :temperature_trend_deviation 0.2
   :temperature_deviation 0.1
   :contributors {:activity_balance 85
                 :body_temperature 90
                 :hrv_balance 88
                 :previous_day_activity 82
                 :previous_night 85
                 :recovery_index 87
                 :resting_heart_rate 75
                 :sleep_balance 89}})

(deftest test-normalize
  (testing "normalize readiness data"
    (let [normalized (api/normalize sample-readiness-data)]
      (is (= "123" (:id normalized)))
      (is (= "2024-01-07" (:date normalized)))
      (is (= 85 (:score normalized)))
      (is (= 0.2 (:temperature_trend normalized)))
      (is (= 0.1 (:temperature_deviation normalized)))
      (is (= (json/generate-string (:contributors sample-readiness-data))
             (:contributors_json normalized)))
      (is (= (json/generate-string sample-readiness-data)
             (:raw_json normalized)))))) 
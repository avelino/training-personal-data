(ns training-personal-data.ouraring.endpoints.sleep.api-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.sleep.api :as api]
            [cheshire.core :as json]))

(def sample-sleep-data
  {:id "123"
   :day "2024-01-07"
   :score 85
   :timestamp "2024-01-07T08:00:00+00:00"
   :contributors {:deep_sleep 80
                 :efficiency 90
                 :latency 75
                 :rem_sleep 85
                 :restfulness 88
                 :timing 82
                 :total_sleep 480}})

(deftest test-normalize
  (testing "normalize sleep data"
    (let [normalized (api/normalize sample-sleep-data)]
      (is (= "123" (:id normalized)))
      (is (= "2024-01-07" (:date normalized)))
      (is (= 85 (:score normalized)))
      (is (= 80 (:deep_sleep normalized)))
      (is (= 90 (:efficiency normalized)))
      (is (= 75 (:latency normalized)))
      (is (= 85 (:rem_sleep normalized)))
      (is (= 88 (:restfulness normalized)))
      (is (= 82 (:timing normalized)))
      (is (= 480 (:total_sleep normalized)))
      (is (= "2024-01-07T08:00:00+00:00" (:timestamp normalized)))
      (is (= (json/generate-string (:contributors sample-sleep-data))
             (:contributors_json normalized)))
      (is (= (json/generate-string sample-sleep-data)
             (:raw_json normalized)))))) 
(ns training-personal-data.ouraring.endpoints.heart-rate.api-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.heart-rate.api :as api]
            [cheshire.core :as json]))

(def sample-heart-rate-data
  {:timestamp "2024-01-07T08:00:00+00:00"
   :bpm 65
   :source "sensor"})

(deftest test-normalize
  (testing "normalize heart rate data"
    (let [normalized (api/normalize sample-heart-rate-data)
          expected-id "2024-01-07T08:00:00+00:00-65-sensor"]
      (is (= expected-id (:id normalized)))
      (is (= "2024-01-07" (:date normalized)))
      (is (= 65 (:bpm normalized)))
      (is (= "sensor" (:source normalized)))
      (is (= "2024-01-07T08:00:00+00:00" (:timestamp normalized)))
      (is (= (json/generate-string sample-heart-rate-data)
             (:raw_json normalized)))))) 
(ns training-personal-data.ouraring.endpoints.tags.api-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.tags.api :as api]
            [cheshire.core :as json]))

(def sample-tags-data
  {:id "123"
   :day "2024-01-07"
   :text "Test tag"
   :timestamp "2024-01-07T08:00:00+00:00"
   :tags ["test" "sample"]})

(deftest test-normalize
  (testing "normalize tags data"
    (let [normalized (api/normalize sample-tags-data)]
      (is (= "123" (:id normalized)))
      (is (= "2024-01-07" (:date normalized)))
      (is (= "Test tag" (:text normalized)))
      (is (= ["test" "sample"] (:tags normalized)))
      (is (= "2024-01-07T08:00:00+00:00" (:timestamp normalized)))
      (is (= (json/generate-string sample-tags-data)
             (:raw_json normalized)))))) 
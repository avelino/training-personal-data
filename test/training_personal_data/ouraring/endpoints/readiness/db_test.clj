(ns training-personal-data.ouraring.endpoints.readiness.db-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.readiness.db :as db]
            [training-personal-data.ouraring.db :as common-db]))

(def sample-readiness
  {:id "123"
   :date "2024-01-07"
   :score 85
   :temperature_trend 0.2
   :temperature_deviation 0.1
   :contributors_json "{\"activity_balance\":85,\"body_temperature\":90}"
   :raw_json "{\"id\":\"123\",\"score\":85}"})

(def saved-records (atom []))

(defn mock-save [_ table-name columns record values]
  (swap! saved-records conj {:record record :values values})
  {:success true})

(defn mock-create-table [_ table-name schema]
  {:success true})

(deftest test-extract-values
  (testing "extract values from readiness record"
    (let [values (db/extract-values sample-readiness)]
      (is (= "123" (first values)))
      (is (= "2024-01-07" (second values)))
      (is (= 85 (nth values 2)))
      (is (= 0.2 (nth values 3)))
      (is (= 0.1 (nth values 4)))
      (is (= (:contributors_json sample-readiness) (nth values 5)))
      (is (= (:raw_json sample-readiness) (nth values 6))))))

(deftest test-db-operations
  (testing "save readiness record"
    (reset! saved-records [])
    (with-redefs [training-personal-data.ouraring.db/save mock-save
                  training-personal-data.ouraring.db/create-table mock-create-table]
      ;; Test save
      (let [values (db/extract-values sample-readiness)]
        (common-db/save {} db/table-name db/columns sample-readiness values)
        (let [saved (first @saved-records)]
          (is (= sample-readiness (:record saved)))
          (is (= values (:values saved)))))))) 
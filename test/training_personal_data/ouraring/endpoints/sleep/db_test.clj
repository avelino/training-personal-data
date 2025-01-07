(ns training-personal-data.ouraring.endpoints.sleep.db-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.sleep.db :as db]
            [training-personal-data.ouraring.db :as common-db]))

(def sample-sleep
  {:id "123"
   :date "2024-01-07"
   :score 85
   :deep_sleep 80
   :efficiency 90
   :latency 75
   :rem_sleep 85
   :restfulness 88
   :timing 82
   :total_sleep 480
   :timestamp "2024-01-07T08:00:00+00:00"
   :contributors_json "{\"deep_sleep\":80,\"efficiency\":90}"
   :raw_json "{\"id\":\"123\",\"score\":85}"})

(def saved-records (atom []))

(defn mock-save [_ table-name columns record values]
  (swap! saved-records conj {:record record :values values})
  {:success true})

(defn mock-create-table [_ table-name schema]
  {:success true})

(deftest test-extract-values
  (testing "extract values from sleep record"
    (let [values (db/extract-values sample-sleep)]
      (is (= "123" (first values)))
      (is (= "2024-01-07" (second values)))
      (is (= 85 (nth values 2)))
      (is (= 80 (nth values 3)))
      (is (= 90 (nth values 4))))))

(deftest test-db-operations
  (testing "save sleep record"
    (reset! saved-records [])
    (with-redefs [training-personal-data.ouraring.db/save mock-save
                  training-personal-data.ouraring.db/create-table mock-create-table]
      ;; Test save
      (let [values (db/extract-values sample-sleep)]
        (common-db/save {} db/table-name db/columns sample-sleep values)
        (let [saved (first @saved-records)]
          (is (= sample-sleep (:record saved)))
          (is (= values (:values saved)))))))) 
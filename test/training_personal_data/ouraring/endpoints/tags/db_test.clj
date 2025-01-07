(ns training-personal-data.ouraring.endpoints.tags.db-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.ouraring.endpoints.tags.db :as db]
            [training-personal-data.ouraring.db :as common-db]))

(def sample-tags
  {:id "123"
   :date "2024-01-07"
   :text "Test tag"
   :tags ["test" "sample"]
   :timestamp "2024-01-07T08:00:00+00:00"
   :raw_json "{\"id\":\"123\",\"text\":\"Test tag\"}"})

(def saved-records (atom []))

(defn mock-save [_ table-name columns record values]
  (swap! saved-records conj {:record record :values values})
  {:success true})

(defn mock-create-table [_ table-name schema]
  {:success true})

(deftest test-extract-values
  (testing "extract values from tags record"
    (let [values (db/extract-values sample-tags)]
      (is (= "123" (first values)))
      (is (= "2024-01-07" (second values)))
      (is (= "Test tag" (nth values 2)))
      (is (= "{\"test\",\"sample\"}" (nth values 3)))
      (is (= "2024-01-07T08:00:00+00:00" (nth values 4)))
      (is (= (:raw_json sample-tags) (nth values 5))))))

(deftest test-db-operations
  (testing "save tags record"
    (reset! saved-records [])
    (with-redefs [training-personal-data.ouraring.db/save mock-save
                  training-personal-data.ouraring.db/create-table mock-create-table]
      ;; Test save
      (let [values (db/extract-values sample-tags)]
        (common-db/save {} db/table-name db/columns sample-tags values)
        (let [saved (first @saved-records)]
          (is (= sample-tags (:record saved)))
          (is (= values (:values saved)))))))) 
(ns training-personal-data.db-test
  (:require [clojure.test :refer [deftest testing is]]
            [training-personal-data.db :as db]))

(deftest test-normalize-column-name
  (testing "normalize column names"
    (is (= "test_column" (db/normalize-column-name "test-column")))
    (is (= "test_column" (db/normalize-column-name :test-column)))
    (is (= "test_column" (db/normalize-column-name "test_column")))
    (is (= "test" (db/normalize-column-name "test")))))

(deftest test-make-db-spec
  (testing "create database spec with required fields"
    (let [config {:dbname "testdb"
                  :host "localhost"
                  :user "testuser"
                  :password "testpass"}
          spec (db/make-db-spec config)]
      (is (= "postgresql" (:dbtype spec)))
      (is (= "testdb" (:dbname spec)))
      (is (= "localhost" (:host spec)))
      (is (= 5432 (:port spec)))
      (is (= "testuser" (:user spec)))
      (is (= "testpass" (:password spec)))
      (is (= "require" (:sslmode spec)))))

  (testing "missing required fields throws exception"
    (is (thrown? clojure.lang.ExceptionInfo
                 (db/make-db-spec {:dbname "testdb"})))))

(deftest test-build-sql
  (testing "build update SQL"
    (let [table "test_table"
          columns ["id" "name" "date" "timestamp" "tags" "raw_json"]]
      (is (= (str "UPDATE test_table SET "
                  "name = ?, "
                  "date = ?::date, "
                  "timestamp = ?::timestamp, "
                  "tags = ?::text[], "
                  "raw_json = ?::jsonb "
                  "WHERE id = ?")
             (db/build-update-sql table columns)))))

  (testing "build insert SQL"
    (let [table "test_table"
          columns ["id" "name" "date" "timestamp" "tags" "raw_json"]]
      (is (= (str "INSERT INTO test_table "
                  "(id, name, date, timestamp, tags, raw_json) "
                  "VALUES (?, ?, ?::date, ?::timestamp, ?::text[], ?::jsonb)")
             (db/build-insert-sql table columns))))))

(def saved-records (atom []))

(defn mock-execute! [_ _]
  (first [{:exists false}]))

(deftest test-save
  (testing "save record"
    (reset! saved-records [])
    (with-redefs [pod.babashka.postgresql/execute! mock-execute!]
      (let [record {:id "123" :name "test"}
            table "test_table"
            columns ["id" "name"]
            values ["123" "test"]]
        (db/save {} table columns record values)))))

(deftest test-create-table
  (testing "create table with schema"
    (with-redefs [pod.babashka.postgresql/execute! mock-execute!]
      (let [schema {:id [:text :primary-key]
                   :name :text
                   :created_at [:timestamp :default "CURRENT_TIMESTAMP"]}]
        (db/create-table {} "test_table" schema)))))
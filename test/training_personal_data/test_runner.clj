(ns training-personal-data.test-runner
  (:require [clojure.test :as t]
            [clojure.string :as str]
            [babashka.fs :as fs]))

(defn- test-namespaces []
  (->> (fs/glob "test/training_personal_data" "**/*_test.clj")
       (map str)
       (map #(-> %
                 (str/replace #"^test/" "")
                 (str/replace #"\.clj$" "")
                 (str/replace #"/" ".")
                 (str/replace #"_" "-")
                 symbol))
       (sort)))

(defn- require-namespaces [namespaces]
  (doseq [ns namespaces]
    (require ns)))

(defn run-tests []
  (let [test-nses (test-namespaces)]
    (println "\nLoading test namespaces:" (count test-nses))
    (require-namespaces test-nses)
    (let [results (apply t/run-tests test-nses)]
      (when (or (pos? (:fail results))
                (pos? (:error results)))
        (System/exit 1)))))

(defn -main [& args]
  (run-tests)) 
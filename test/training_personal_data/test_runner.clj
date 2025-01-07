(ns training-personal-data.test-runner
  (:require [clojure.test :as t]
            [training-personal-data.ouraring.endpoints.sleep.api-test]
            [training-personal-data.ouraring.endpoints.sleep.db-test]
            [training-personal-data.ouraring.endpoints.sleep.core-test]
            [training-personal-data.ouraring.endpoints.activity.api-test]
            [training-personal-data.ouraring.endpoints.activity.db-test]
            [training-personal-data.ouraring.endpoints.activity.core-test]
            [training-personal-data.ouraring.endpoints.heart-rate.api-test]
            [training-personal-data.ouraring.endpoints.heart-rate.db-test]
            [training-personal-data.ouraring.endpoints.heart-rate.core-test]
            [training-personal-data.ouraring.endpoints.readiness.api-test]
            [training-personal-data.ouraring.endpoints.readiness.db-test]
            [training-personal-data.ouraring.endpoints.readiness.core-test]
            [training-personal-data.ouraring.endpoints.tags.api-test]
            [training-personal-data.ouraring.endpoints.tags.db-test]
            [training-personal-data.ouraring.endpoints.tags.core-test]
            [training-personal-data.ouraring.endpoints.workout.api-test]
            [training-personal-data.ouraring.endpoints.workout.db-test]
            [training-personal-data.ouraring.endpoints.workout.core-test]))

(defn run-tests []
  (let [results (t/run-tests 'training-personal-data.ouraring.endpoints.sleep.api-test
                            'training-personal-data.ouraring.endpoints.sleep.db-test
                            'training-personal-data.ouraring.endpoints.sleep.core-test
                            'training-personal-data.ouraring.endpoints.activity.api-test
                            'training-personal-data.ouraring.endpoints.activity.db-test
                            'training-personal-data.ouraring.endpoints.activity.core-test
                            'training-personal-data.ouraring.endpoints.heart-rate.api-test
                            'training-personal-data.ouraring.endpoints.heart-rate.db-test
                            'training-personal-data.ouraring.endpoints.heart-rate.core-test
                            'training-personal-data.ouraring.endpoints.readiness.api-test
                            'training-personal-data.ouraring.endpoints.readiness.db-test
                            'training-personal-data.ouraring.endpoints.readiness.core-test
                            'training-personal-data.ouraring.endpoints.tags.api-test
                            'training-personal-data.ouraring.endpoints.tags.db-test
                            'training-personal-data.ouraring.endpoints.tags.core-test
                            'training-personal-data.ouraring.endpoints.workout.api-test
                            'training-personal-data.ouraring.endpoints.workout.db-test
                            'training-personal-data.ouraring.endpoints.workout.core-test)]
    (when (or (pos? (:fail results))
              (pos? (:error results)))
      (System/exit 1))))

(defn -main [& args]
  (run-tests)) 
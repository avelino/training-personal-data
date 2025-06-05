(ns training-personal-data.ouraring.config
  (:require [training-personal-data.ouraring.endpoints.activity.api :as activity-api]
            [training-personal-data.ouraring.endpoints.activity.db :as activity-db]
            [training-personal-data.ouraring.endpoints.sleep.api :as sleep-api]
            [training-personal-data.ouraring.endpoints.sleep.db :as sleep-db]
            [training-personal-data.ouraring.endpoints.readiness.api :as readiness-api]
            [training-personal-data.ouraring.endpoints.readiness.db :as readiness-db]
            [training-personal-data.ouraring.endpoints.heart-rate.api :as heart-rate-api]
            [training-personal-data.ouraring.endpoints.heart-rate.db :as heart-rate-db]
            [training-personal-data.ouraring.endpoints.workout.api :as workout-api]
            [training-personal-data.ouraring.endpoints.workout.db :as workout-db]
            [training-personal-data.ouraring.endpoints.tags.api :as tags-api]
            [training-personal-data.ouraring.endpoints.tags.db :as tags-db]
            [training-personal-data.core.pipeline :as pipeline]))

(def endpoint-configs
  "Configuration for all Oura Ring endpoints as data.
   Each endpoint follows the same pattern: fetch → transform → save"
  {:activity
   (pipeline/create-endpoint-config
    "activity"
    activity-db/table-name
    activity-db/columns
    activity-db/schema
    activity-api/fetch
    activity-api/normalize
    activity-db/extract-values)

   :sleep
   (pipeline/create-endpoint-config
    "sleep"
    sleep-db/table-name
    sleep-db/columns
    sleep-db/schema
    sleep-api/fetch
    sleep-api/normalize
    sleep-db/extract-values)

   :readiness
   (pipeline/create-endpoint-config
    "readiness"
    readiness-db/table-name
    readiness-db/columns
    readiness-db/schema
    readiness-api/fetch
    readiness-api/normalize
    readiness-db/extract-values)

   :heart-rate
   (pipeline/create-endpoint-config
    "heart-rate"
    heart-rate-db/table-name
    heart-rate-db/columns
    heart-rate-db/schema
    heart-rate-api/fetch
    heart-rate-api/normalize
    heart-rate-db/extract-values)

   :workout
   (pipeline/create-endpoint-config
    "workout"
    workout-db/table-name
    workout-db/columns
    workout-db/schema
    workout-api/fetch
    workout-api/normalize
    workout-db/extract-values)

   :tags
   (pipeline/create-endpoint-config
    "tags"
    tags-db/table-name
    tags-db/columns
    tags-db/schema
    tags-api/fetch
    tags-api/normalize
    tags-db/extract-values)})

(defn get-endpoint-config
  "Get configuration for a specific endpoint"
  [endpoint-key]
  (get endpoint-configs endpoint-key))

(defn get-all-endpoint-configs
  "Get all endpoint configurations"
  []
  endpoint-configs)

(defn get-enabled-endpoint-configs
  "Get configurations for enabled endpoints only.
   Currently excludes heart-rate due to performance concerns."
  []
  (dissoc endpoint-configs :heart-rate))

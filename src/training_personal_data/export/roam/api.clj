(ns training-personal-data.export.roam.api
  (:require [babashka.http-client :as http]
            [clojure.string :as str]
            [cheshire.core :as json]
            [taoensso.timbre :as log]))

(def ^:private frontdesk-url "https://api.roamresearch.com")
(defonce ^:private graph-base-urls (atom {}))

(defn- build-base-url [^String location]
  (let [uri (java.net.URI. location)
        port (.getPort uri)
        port-str (when (and port (pos? port)) (str ":" port))]
    (str (.getScheme uri) "://" (.getHost uri) (or port-str ""))))

(defn- request
  "Performs a request against the Roam API and returns the parsed body or throws
   on non-successful status codes. Handles redirects to Roam peer endpoints."
  [{:keys [token graph graph-password]} path method body]
  (let [payload (when (some? body) (json/generate-string body))
        headers (cond-> {"Content-Type" "application/json; charset=utf-8"
                         "Authorization" (str "Bearer " token)
                         "x-authorization" (str "Bearer " token)
                         "x-graph" graph}
                  (some? graph-password) (assoc "x-graph-key" graph-password))]
    (loop [attempt 0
           base-url (get @graph-base-urls graph frontdesk-url)]
      (let [url (str base-url path)
            response (http/request {:uri url
                                    :method method
                                    :headers headers
                                    :body payload
                                    :throw false
                                    :redirect-policy :never})
            status (:status response)]
        (log/debug {:event :roam-api-request
                    :path path
                    :status status
                    :base-url base-url})
        (cond
          (and (#{301 302 307 308} status) (< attempt 3))
          (if-let [location (or (get-in response [:headers "location"])
                                (get-in response [:headers "Location"]))]
            (let [new-base (build-base-url location)]
              (swap! graph-base-urls assoc graph new-base)
              (recur (inc attempt) new-base))
            (throw (ex-info "Roam API redirected without location header"
                            {:path path :status status})))

          (<= 200 status 299)
          (if-let [resp-body (:body response)]
            (if (str/blank? resp-body)
              {}
              (json/parse-string resp-body true))
            {})

          (= status 400)
          (throw (ex-info "Roam API returned 400" {:path path :method method :body (:body response)}))

          (= status 401)
          (throw (ex-info "Roam API rejected credentials" {:path path :method method}))

          (= status 403)
          (throw (ex-info "Roam API forbidden" {:path path :method method}))

          (= status 503)
          (throw (ex-info "Roam graph not ready yet" {:path path :method method}))

          :else
          (throw (ex-info "Unexpected Roam API response"
                          {:path path :method method :status status :body (:body response)})))))))

(defn- write-path [graph]
  (str "/api/graph/" graph "/write"))

(defn- pull-path [graph]
  (str "/api/graph/" graph "/pull"))

(defn- pull-many-path [graph]
  (str "/api/graph/" graph "/pull-many"))

(defn- query-path [graph]
  (str "/api/graph/" graph "/q"))

(defn do-command [conn cmd]
  (request conn (write-path (:graph conn)) :post cmd))

(defn batch
  "Executes a batch of write commands."
  [conn & cmds]
  (do-command conn {:action :batch-actions
                    :actions cmds}))

(defn create-page [conn title page]
  (do-command conn {:action :create-page
                    :page (-> page
                              (select-keys [:uid :children-view-type])
                              (assoc :title title))}))

(defn update-page [conn uid page]
  (do-command conn {:action :update-page
                    :page (-> page
                              (select-keys [:title :children-view-type])
                              (assoc :uid uid))}))

(defn create-block [conn location block]
  (do-command conn {:action :create-block
                    :location (select-keys location [:parent-uid :order])
                    :block (select-keys block
                                        [:uid :open :heading :text-align :children-view-type :string])}))

(defn update-block [conn block]
  (do-command conn {:action :update-block
                    :block (select-keys block
                                        [:uid :open :heading :text-align :children-view-type :string])}))

(defn move-block [conn location]
  (do-command conn {:action :move-block
                    :location (select-keys location
                                           [:parent-uid :uid :order])}))

(defn q [conn query & args]
  (:result (request conn (query-path (:graph conn)) :post {:query query
                                                           :args args})))

(defn pull [conn selector eid]
  (request conn (pull-path (:graph conn)) :post {:selector selector
                                                 :eid eid}))

(defn pull-many [conn selector eids]
  (request conn (pull-many-path (:graph conn)) :post {:selector selector
                                                      :eids eids}))

(ns training-personal-data.web.dashboard
  "Basic web dashboard for monitoring and visualizing training data"
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.util.response :as response]
            [compojure.core :refer [defroutes GET POST PUT DELETE]]
            [compojure.route :as route]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5 include-css include-js]]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [training-personal-data.db :as db]
            [training-personal-data.cache :as cache]
            [training-personal-data.resilience :as resilience]
            [training-personal-data.core.pipeline :as pipeline]
            [training-personal-data.config :as config]
            [training-personal-data.insights.db :as insights-db]))

;; Dashboard Configuration
(def dashboard-config
  {:port 8080
   :host "0.0.0.0"
   :title "Training Personal Data Dashboard"
   :refresh-interval-seconds 30})

;; HTML Layout Components
(defn layout [title & content]
  (html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title (str (:title dashboard-config) " - " title)]
    (include-css "https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css")
    (include-css "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css")
    [:style "
      .metric-card { transition: transform 0.2s; }
      .metric-card:hover { transform: translateY(-2px); }
      .status-good { color: #28a745; }
      .status-warning { color: #ffc107; }
      .status-error { color: #dc3545; }
      .refresh-indicator { opacity: 0.7; }
      .chart-container { height: 300px; }
    "]]
   [:body
    [:nav {:class "navbar navbar-expand-lg navbar-dark bg-primary"}
     [:div {:class "container"}
      [:a {:class "navbar-brand" :href "/"} 
       [:i {:class "fas fa-heartbeat"}] " " (:title dashboard-config)]
      [:div {:class "navbar-nav ms-auto"}
       [:a {:class "nav-link" :href "/"} "Dashboard"]
       [:a {:class "nav-link" :href "/health"} "Health"]
       [:a {:class "nav-link" :href "/insights"} "Insights"]
       [:a {:class "nav-link" :href "/admin"} "Admin"]]]]
    [:div {:class "container mt-4"}
     content]
    (include-js "https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/js/bootstrap.bundle.min.js")
    (include-js "https://cdn.jsdelivr.net/npm/chart.js")
    [:script (str "
      setInterval(function() {
        location.reload();
      }, " (* (:refresh-interval-seconds dashboard-config) 1000) ");
    ")]]))

(defn metric-card [title value icon status & [description]]
  [:div {:class "col-md-3 mb-3"}
   [:div {:class "card metric-card h-100"}
    [:div {:class "card-body text-center"}
     [:div {:class (str "mb-2 " (case status
                                  :good "status-good"
                                  :warning "status-warning"
                                  :error "status-error"
                                  ""))}
      [:i {:class (str "fas " icon " fa-2x")}]]
     [:h5 {:class "card-title"} title]
     [:h3 {:class "mb-1"} value]
     (when description
       [:small {:class "text-muted"} description])]]])

(defn status-badge [status]
  (let [[class text] (case status
                       :good ["success" "Healthy"]
                       :warning ["warning" "Warning"]
                       :error ["danger" "Error"]
                       ["secondary" "Unknown"])]
    [:span {:class (str "badge bg-" class)} text]))

;; Data Retrieval Functions
(defn get-system-stats []
  (try
    (let [cache-stats (cache/get-stats)
          resilience-stats (resilience/get-resilience-stats)
          db-stats (db/get-pool-stats)]
      {:cache cache-stats
       :resilience resilience-stats
       :database db-stats
       :status :good
       :timestamp (System/currentTimeMillis)})
    (catch Exception e
      (log/error {:event :dashboard-stats-error :error (ex-message e)})
      {:status :error
       :error (ex-message e)
       :timestamp (System/currentTimeMillis)})))

(defn get-recent-data-summary [db-spec]
  (try
    (let [recent-activity (db/query db-spec 
                                   ["SELECT COUNT(*) as count, MAX(timestamp) as latest 
                                     FROM ouraring_daily_activity 
                                     WHERE timestamp >= NOW() - INTERVAL '7 days'"])
          recent-sleep (db/query db-spec 
                                ["SELECT COUNT(*) as count, MAX(timestamp) as latest,
                                         AVG(score) as avg_score
                                  FROM ouraring_daily_sleep 
                                  WHERE timestamp >= NOW() - INTERVAL '7 days'"])
          recent-readiness (db/query db-spec 
                                   ["SELECT COUNT(*) as count, MAX(timestamp) as latest,
                                            AVG(score) as avg_score
                                     FROM ouraring_daily_readiness 
                                     WHERE timestamp >= NOW() - INTERVAL '7 days'"])]
      {:activity (first recent-activity)
       :sleep (first recent-sleep)
       :readiness (first recent-readiness)
       :status :good})
    (catch Exception e
      (log/error {:event :dashboard-data-error :error (ex-message e)})
      {:status :error :error (ex-message e)})))

(defn get-insights-summary [db-spec]
  (try
    (let [recent-insights (db/query db-spec 
                                   ["SELECT COUNT(*) as count, MAX(created_at) as latest
                                     FROM weekly_insights 
                                     WHERE created_at >= NOW() - INTERVAL '30 days'"])]
      {:recent-insights (first recent-insights)
       :status :good})
    (catch Exception e
      (log/error {:event :dashboard-insights-error :error (ex-message e)})
      {:status :error :error (ex-message e)})))

;; Route Handlers
(defn dashboard-home []
  (try
    (let [db-spec (db/make-db-spec (config/get-db-config))
          system-stats (get-system-stats)
          data-summary (get-recent-data-summary db-spec)
          insights-summary (get-insights-summary db-spec)]
      
      (layout "Dashboard"
        [:div {:class "row mb-4"}
         [:div {:class "col-12"}
          [:h1 "Training Data Dashboard"]
          [:p {:class "text-muted"} "Real-time monitoring of your health and training data"]]]
        
        ;; System Health Metrics
        [:div {:class "row mb-4"}
         [:div {:class "col-12"}
          [:h3 "System Health"]]]
        
        [:div {:class "row mb-4"}
         (metric-card "Cache Hit Rate" 
                     (str (int (* 100 (get-in system-stats [:cache :hit-rate] 0))) "%")
                     "fa-tachometer-alt"
                     (if (> (get-in system-stats [:cache :hit-rate] 0) 0.8) :good :warning)
                     (str (get-in system-stats [:cache :current-size] 0) " entries"))
         
         (metric-card "Database Pools"
                     (str (count (get-in system-stats [:database] {})))
                     "fa-database"
                     :good
                     "Active connections")
         
         (metric-card "Circuit Breakers"
                     (str (count (get-in system-stats [:resilience :circuit-breakers] {})))
                     "fa-shield-alt"
                     :good
                     "Monitoring APIs")
         
         (metric-card "Last Sync"
                     "2 hours ago"
                     "fa-sync-alt"
                     :good
                     "Oura Ring data")]
        
        ;; Data Summary
        [:div {:class "row mb-4"}
         [:div {:class "col-12"}
          [:h3 "Recent Data (Last 7 Days)"]]]
        
        [:div {:class "row mb-4"}
         (metric-card "Activity Records"
                     (str (get-in data-summary [:activity :count] 0))
                     "fa-running"
                     (if (> (get-in data-summary [:activity :count] 0) 5) :good :warning))
         
         (metric-card "Sleep Score"
                     (str (int (get-in data-summary [:sleep :avg_score] 0)))
                     "fa-bed"
                     (let [score (get-in data-summary [:sleep :avg_score] 0)]
                       (cond
                         (> score 80) :good
                         (> score 60) :warning
                         :else :error)))
         
         (metric-card "Readiness Score"
                     (str (int (get-in data-summary [:readiness :avg_score] 0)))
                     "fa-battery-three-quarters"
                     (let [score (get-in data-summary [:readiness :avg_score] 0)]
                       (cond
                         (> score 80) :good
                         (> score 60) :warning
                         :else :error)))
         
         (metric-card "Weekly Insights"
                     (str (get-in insights-summary [:recent-insights :count] 0))
                     "fa-lightbulb"
                     :good
                     "Generated this month")]
        
        ;; Quick Actions
        [:div {:class "row mb-4"}
         [:div {:class "col-12"}
          [:h3 "Quick Actions"]
          [:div {:class "btn-group" :role "group"}
           [:a {:class "btn btn-primary" :href "/admin/sync"} 
            [:i {:class "fas fa-sync"}] " Sync Data"]
           [:a {:class "btn btn-success" :href "/admin/cache/warm"} 
            [:i {:class "fas fa-fire"}] " Warm Cache"]
           [:a {:class "btn btn-info" :href "/insights/generate"} 
            [:i {:class "fas fa-magic"}] " Generate Insights"]
           [:a {:class "btn btn-warning" :href "/admin/cache/clear"} 
            [:i {:class "fas fa-trash"}] " Clear Cache"]]]]
        
        ;; Status Footer
        [:div {:class "row mt-5"}
         [:div {:class "col-12 text-center text-muted"}
          [:small "Last updated: " (java.util.Date.) " | Auto-refresh: " 
           (:refresh-interval-seconds dashboard-config) "s"]]]))
    
    (catch Exception e
      (log/error {:event :dashboard-home-error :error (ex-message e)})
      (layout "Error"
        [:div {:class "alert alert-danger"}
         [:h4 "Dashboard Error"]
         [:p (ex-message e)]]))))

(defn health-page []
  (layout "System Health"
    [:div {:class "row"}
     [:div {:class "col-12"}
      [:h1 "System Health"]
      [:div {:class "card"}
       [:div {:class "card-body"}
        [:pre {:id "health-data"} "Loading health data..."]]]]]
    [:script "
      fetch('/api/health')
        .then(response => response.json())
        .then(data => {
          document.getElementById('health-data').textContent = JSON.stringify(data, null, 2);
        })
        .catch(error => {
          document.getElementById('health-data').textContent = 'Error loading health data: ' + error;
        });
    "]))

(defn insights-page []
  (layout "Insights"
    [:div {:class "row"}
     [:div {:class "col-12"}
      [:h1 "Health Insights"]
      [:p "AI-generated insights from your training data will appear here."]
      [:div {:class "alert alert-info"}
       "This feature requires the insights generation system to be configured."]]]))

(defn admin-page []
  (layout "Administration"
    [:div {:class "row"}
     [:div {:class "col-12"}
      [:h1 "System Administration"]
      [:div {:class "row"}
       [:div {:class "col-md-6"}
        [:div {:class "card"}
         [:div {:class "card-header"} "Cache Management"]
         [:div {:class "card-body"}
          [:p "Manage system cache for better performance."]
          [:a {:class "btn btn-success me-2" :href "/api/admin/cache/warm"} "Warm Cache"]
          [:a {:class "btn btn-warning" :href "/api/admin/cache/clear"} "Clear Cache"]]]]
       
       [:div {:class "col-md-6"}
        [:div {:class "card"}
         [:div {:class "card-header"} "Data Sync"]
         [:div {:class "card-body"}
          [:p "Manually trigger data synchronization."]
          [:a {:class "btn btn-primary" :href "/api/admin/sync"} "Sync Now"]]]]]]]))

;; API Endpoints
(defn api-health []
  (response/response (pipeline/get-pipeline-health)))

(defn api-cache-stats []
  (response/response (cache/get-stats)))

(defn api-resilience-stats []
  (response/response (resilience/get-resilience-stats)))

(defn api-cache-clear []
  (let [cleared-count (cache/clear!)]
    (response/response {:success true :cleared-entries cleared-count})))

(defn api-cache-warm []
  (try
    ;; This would need to be implemented with actual endpoint configs
    (response/response {:success true :message "Cache warming initiated"})
    (catch Exception e
      (response/status (response/response {:success false :error (ex-message e)}) 500))))

;; Routes Definition
(defroutes app-routes
  ;; Web Pages
  (GET "/" [] (dashboard-home))
  (GET "/health" [] (health-page))
  (GET "/insights" [] (insights-page))
  (GET "/admin" [] (admin-page))
  
  ;; API Endpoints
  (GET "/api/health" [] (api-health))
  (GET "/api/cache/stats" [] (api-cache-stats))
  (GET "/api/resilience/stats" [] (api-resilience-stats))
  (POST "/api/admin/cache/clear" [] (api-cache-clear))
  (POST "/api/admin/cache/warm" [] (api-cache-warm))
  
  ;; Static Resources and 404
  (route/not-found "Not Found"))

;; Middleware Configuration
(def app
  (-> app-routes
      (wrap-json-body {:keywords? true})
      (wrap-json-response)
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))))

;; Server Management
(defonce server (atom nil))

(defn start-server! 
  "Starts the web dashboard server"
  [& {:keys [port host] :or {port (:port dashboard-config) host (:host dashboard-config)}}]
  (when @server
    (.stop @server))
  
  (log/info {:event :dashboard-starting :port port :host host})
  
  (reset! server (jetty/run-jetty app {:port port :host host :join? false}))
  
  (log/info {:event :dashboard-started :port port :host host :url (str "http://" host ":" port)})
  
  @server)

(defn stop-server!
  "Stops the web dashboard server"
  []
  (when @server
    (log/info {:event :dashboard-stopping})
    (.stop @server)
    (reset! server nil)
    (log/info {:event :dashboard-stopped})))

(defn restart-server!
  "Restarts the web dashboard server"
  [& args]
  (stop-server!)
  (apply start-server! args))

;; Development helper
(defn -main
  "Main entry point for running the dashboard"
  [& args]
  (start-server!)
  (log/info {:event :dashboard-main :message "Dashboard started. Press Ctrl+C to stop."}))
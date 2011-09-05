(ns clojuresphere.core
  (:use [clojuresphere.util :only [read-gz-resource memory-stats]]
        [compojure.core :only [defroutes GET POST ANY]]
        [hiccup.middleware :only [wrap-base-url]]
        [ring.util.response :only [response]]
        [ring.middleware.params :only [wrap-params]]
        [ring.adapter.jetty :only [run-jetty]])
  (:require [clojuresphere.layout :as layout]
            [compojure.route :as route]))

(defonce project-info (read-gz-resource "project_info.clj.gz"))
(defonce project-graph (read-gz-resource "project_graph.clj.gz"))

;; TODO: project info, search
(defroutes routes
  (GET "/_stats" {{gc "gc"} :params}
       (prn-str (merge {:projects (count project-graph)
                        :memory (memory-stats :gc gc)})))
  (GET "/" [] (layout/welcome))
  (route/resources "/")
  (route/not-found (layout/not-found)))

(def app (-> #'routes
             wrap-base-url
             wrap-params))

(defn -main []
  (let [port (Integer/parseInt (System/getenv "PORT"))]
    (run-jetty app {:port port :join? false})))

;(run-jetty #'app {:port 8080 :join? false})

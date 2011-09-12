(ns clojuresphere.core
  (:use [clojuresphere.util :only [memory-stats parse-int]]
        [compojure.core :only [defroutes GET POST ANY]]
        [ring.util.response :only [response]]
        [ring.middleware.params :only [wrap-params]]
        [ring.adapter.jetty :only [run-jetty]])
  (:require [clojuresphere.layout :as layout]
            [clojuresphere.project-model :as project]
            [compojure.route :as route]))

(defroutes routes

  (GET "/_stats" {{gc "gc"} :params}
       (prn-str (merge {:projects (count project/graph)
                        :memory (memory-stats :gc gc)})))

  (GET "/" {{offset "offset"} :params}
       (layout/top-projects (parse-int offset 0)))
  (GET ["/:pid" :pid #"[a-zA-Z0-9\-\.\_]+"] [pid] (layout/project-detail pid))
  (GET ["/:aid/:gid/:ver"
        :aid #"[a-zA-Z0-9\-\.\_]+"
        :gid #"[a-zA-Z0-9\-\.\_]+"
        :ver #"[a-zA-Z0-9\-\.\_]+"]
       [gid aid ver]
       (layout/project-version-detail gid aid ver))

  (GET "/_search" {{query "query" offset "offset"} :params}
       (layout/search-results query (parse-int offset 0)))

  (route/resources "/")
  (route/not-found (layout/not-found)))

(def ^:dynamic *req* nil)

(defn wrap-request [handler]
  (fn [req]
    (binding [*req* req]
      (handler req))))

(defn wrap-ajax-detect [handler]
  (fn [req]
    (handler (assoc req
               :ajax? (= "XMLHttpRequest"
                         (get-in req [:headers "x-requested-with"]))))))

(def app (-> #'routes
             wrap-request
             wrap-ajax-detect
             wrap-params))

(defn -main []
  (let [port (Integer/parseInt (get (System/getenv) "PORT" "9999"))]
    (run-jetty app {:port port :join? false})))

;(run-jetty #'app {:port 8080 :join? false})

(ns clojuresphere.core
  (:use [clojuresphere.util :only [memory-stats parse-int wrap-request
                                   wrap-ajax-detect]]
        [compojure.core :only [defroutes GET POST ANY]]
        [ring.util.response :only [response redirect]]
        [ring.middleware.params :only [wrap-params]]
        [ring.adapter.jetty :only [run-jetty]])
  (:require [clojuresphere.layout :as layout]
            [clojuresphere.project-model :as project]
            [compojure.route :as route]))

(defroutes routes

  (GET "/_stats" {{gc "gc"} :params}
       (prn-str (merge {:projects (count project/graph)
                        :memory (memory-stats :gc gc)})))

  (GET "/" {{query "query" sort "sort" offset "offset"} :params}
       (layout/projects query sort (parse-int offset 0)))

  (route/resources "/")
  
  (GET ["/:aid" :pid #"[^/]+"]
       [aid]
       (redirect (str aid "/" aid)))
  (GET ["/:gid/:aid" :gid #"[^/]+" :aid #"[^/]+"]
       [gid aid]
       (layout/project-detail (symbol (str gid "/" aid))))
  (GET ["/:gid/:aid/:ver" :gid #"[^/]+" :aid #"[^/]+" :ver #"[^/]+"]
       [gid aid ver]
       (layout/project-version-detail gid aid ver))

  
  (route/not-found (layout/not-found)))

(def app (-> #'routes
             wrap-request
             wrap-ajax-detect
             wrap-params))

(defn -main []
  (let [port (Integer. (get (System/getenv) "PORT" "9999"))]
    (run-jetty app {:port port :join? false})))

;(run-jetty #'app {:port 8080 :join? false})

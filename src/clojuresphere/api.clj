(ns clojuresphere.api
  (:use [clojuresphere.layout :only [page]]
        [clojuresphere.util :only [json-resp clojure-resp]])
  (:require [clojuresphere.project-model :as proj]))

(def api-url "http://www.clojuresphere.com/api/")

(defn overview []
  (page
   "ClojureSphere API"
   [:div.project-detail
    [:div#api-overview.overview.clearfix
     [:h3 "API Format"]
     [:p "REST requests, JSON responses by default. Set the " [:code "output"] " query parameter to " [:code "clojure"] " to return responses as Clojure data."]
     [:h3 "Project Listing"]
     [:dl
      [:dt "Endpoint"]
      (let [endpoint (str api-url "projects")]
        [:dd.endpoint [:pre [:code [:a {:href endpoint} endpoint]]]])
      [:dt "Parameters"]
      [:dd [:dl
            [:dt [:code "query"]]
            [:dd "String to filter projects by. Looks for matching substring within project name or description."]
            [:dt [:code "sort"]]
            [:dd "One of: " [:code "dependents"] ", " [:code "watchers"] ", "
             [:code "updated"] ", " [:code "created"] ", " [:code "random"]
             ". Default: " [:code "dependents"]]
            [:dt [:code "limit"]]
            [:dd "Number of projects to return. Default: " [:code "50"]]
            [:dt [:code "offset"]]
            [:dd "Offset within total results to return. Default: " [:code "0"]]]]
      [:dt "Example"]
      (let [ex-url (str api-url "projects?query=lein&sort=watchers")
            ex-resp (try (slurp ex-url)
                         (catch Exception e "[Failed request]"))]
        [:dd.ex
         [:p "Request: "] [:pre.req [:code [:a {:href ex-url} ex-url]]]
         [:p "Response: "] [:pre.resp [:code ex-resp]]])]
     [:h3 "Project Detail"]
     [:dl
      [:dt "Endpoint"]
      (let [endpoint (str api-url "projects/:group-id/:artifact-id")]
        [:dd.endpoint [:pre [:code endpoint]]])
      [:dt "Parameters"]
      [:dd [:p.none "None"]]
      [:dt "Example"]
      (let [ex-url (str api-url "projects/useful/useful")
            ex-resp (try (slurp ex-url)
                         (catch Exception e "[Failed request]"))]
        [:dd.ex
         [:p "Request: "] [:pre.req [:code [:a {:href ex-url} ex-url]]]
         [:p "Response: "] [:pre.resp [:code ex-resp]]])]
     [:h3 "Project Version Detail"]
     [:dl
      [:dt "Endpoint"]
      (let [endpoint (str api-url "projects/:group-id/:artifact-id/:version")]
        [:dd.endpoint [:pre [:code endpoint]]])
      [:dt "Parameters"]
      [:dd [:p.none "None"]]
      [:dt "Example"]
      (let [ex-url (str api-url "projects/useful/useful/0.8.4")
            ex-resp (try (slurp ex-url)
                         (catch Exception e "[Failed request]"))]
        [:dd.ex
         [:p "Request: "] [:pre.req [:code [:a {:href ex-url} ex-url]]]
         [:p "Response: "] [:pre.resp [:code ex-resp]]])]]]))

(defn prep-project [pid props]
  (let [github (select-keys (:github props)
                            [:name :owner :watchers :forks :description
                             :homepage])
        github (when (seq github)
                 (let [owner (:owner github)]
                   (assoc github
                     :owner (if (map? owner)
                              (:login owner)
                              owner))))
        props (assoc (dissoc props :watchers :dependent-counts)
                :name pid
                :github github)
        props (if (contains? props :versions)
                (assoc props
                  :sorted-versions (proj/sort-versions (keys (:versions props)))
                  :latest-dependents (proj/get-dependents props)
                  :stable-dependents (proj/get-dependents props proj/latest-stable-coord?))
                props)]
    props))

(defn projects [& {:keys [query sort offset limit output] :as opts}]
  (let [sort (or sort "dependents")
        random? (= "random" sort)
        limit (or limit 50)
        pids (cond
              (seq query) (proj/sort-pids (proj/find-pids query) sort)
              random?     (repeatedly limit proj/random)
              :else       (or (proj/sorted-pids (keyword sort))
                              (proj/sorted-pids :dependents)))
        resp-fn (if (= "clojure" output)
                  clojure-resp
                  json-resp)]
    (resp-fn
     {:projects (for [pid (take limit pids)]
                  (let [props (get proj/graph pid)]
                    (prep-project pid (dissoc props :versions))))}
     :pretty true)))

(defn project-detail [pid params]
  (let [resp-fn (if (= "clojure" (get params "output"))
                  clojure-resp
                  json-resp)]
    (resp-fn
     (when-let [props (proj/graph pid)]
       (prep-project pid props))
     :pretty true)))

(defn project-version-detail [pid ver params]
  (let [resp-fn (if (= "clojure" (get params "output"))
                  clojure-resp
                  json-resp)]
    (resp-fn
     (when-let [props (get-in proj/graph [pid :versions ver])]
       (assoc props
         :name pid
         :version ver))
     :pretty true)))
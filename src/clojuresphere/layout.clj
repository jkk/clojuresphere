(ns clojuresphere.layout
  (:use [clojuresphere.core :only [*req*]]
        [clojuresphere.util :only [url-encode qualify-name maven-coord lein-coord
                                   parse-int]]
        [hiccup.page-helpers :only [html5 include-js include-css
                                    javascript-tag link-to url]]
        [hiccup.form-helpers :only [form-to submit-button]]
        [hiccup.core :only [h html]])
  (:require [clojuresphere.project-model :as project]))

(def site-name "ClojureSphere")

(defn page-header []
  [:div#header
   [:div.inner
    [:h1 (link-to "/" site-name)]
    [:p#tagline "Browse the open-source Clojure ecosystem"]
    (form-to [:get "/"]
             [:input {:name "query" :size 30 :id "query"
                      :value (get-in *req* [:query-params "query"])
                      :type "search" :placeholder "Search"}] " "
                      (submit-button "Go"))]])

(defn page-footer []
  [:div#footer
   [:p#links (link-to "http://github.com/jkk/clojuresphere" "GitHub")]
   [:p#copyright "Made by "
    (link-to "http://jkkramer.com" "Justin Kramer") " - "
    (link-to "http://twitter.com/jkkramer" "@jkkramer")]
   [:p#stats (str project/project-count " projects indexed "
                  project/last-updated)]])

(defn page [title & body]
  (let [content [:div#content
                 (when title
                   [:h2#page-title (h title)])
                 [:div#content-body body]]]
    (if (:ajax? *req*)
      (html content)
      (html5
       [:meta {:http-equiv "content-type" :content "text/html; charset=utf-8"}]
       [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
       [:title (str (when title (str (h title) " - ")) site-name)]
       [:meta {:name "description" :content "Browse the open-source Clojure ecosystem"}]
       (include-css "/css/main.css")
       [:body
        [:div#page-shell
         (page-header)
         [:div#content-shell
          content]
         (page-footer)]
        (javascript-tag (str "var Globals = {siteName: \"" site-name "\"};"))
        (include-js "/js/jquery.js"
                    "/js/history.adapter.jquery.js"
                    "/js/history.js"
                    "/js/main.js")]))))

(defn coord-url [coord]
  (if (or (keyword? coord) (string? coord))
    (str "/" (name coord))
    (let [[gid aid ver] (maven-coord coord)]
      (str "/" (url-encode aid)
           "/" (url-encode gid)
           "/" (url-encode (or ver ""))))))

(defn render-coord [coord]
  (if (or (keyword? coord) (string? coord))
    (name coord)
    (let [[name ver] (apply lein-coord coord)]
      (str name " " ver))))

(defn project-overview [node]
  [:div.overview
   [:p.description (node :description)]
   (when (node :github-url)
     [:p.github (link-to (node :github-url) "GitHub")])
   (when (node :clojars-url)
     [:p.clojars (link-to (node :clojars-url) "Clojars")])
   (when (node :watchers)
     [:p.watchers [:span.label "Watchers"] " " [:span.value (node :watchers)]])
   (when (node :forks)
     [:p.forks [:span.label "Forks"] " " [:span.value (node :forks)]])
   [:div.clear]])

(defn project-dep-list [deps]
  (if (zero? (count deps))
    [:p.none "None"]
    (let [dep1 (first deps)
          ul-tag (if (or (keyword? dep1) (string? dep1))
                   :ul.dep-list
                   :ul.dep-list.ver)]
      [ul-tag
       (for [aid deps]
         [:li (link-to (coord-url aid) (h (render-coord aid)))])
       [:span.clear]])))

(defn project-version-list [versions]
  (if (zero? (count versions))
    [:p.none "None"]
    [:ul.version-list
     (for [[[vname vver :as coord] vinfo] versions]
       [:li
        (link-to
         (coord-url coord)
         [:span.version
          [:span.vver (h (or vver "[none]"))]
          [:span.vname (h (str vname))]]
         [:span.count (count (vinfo :dependents))])])
     [:span.clear]]))

(defn project-detail [pid]
  (let [pid (-> pid name keyword)
        node (project/graph pid)]
    (when node
      (page
       (name pid)
       [:div.project-detail
        (project-overview node)
        [:div.dependencies
         [:h3 "Dependencies (current and past) "
          [:span.count (count (node :dependencies))]]
         (project-dep-list (node :dependencies))]
        (let [versions (project/most-used-versions pid)]
          [:div.versions
           [:h3 "Versions " [:span.count (count versions)]]
           (project-version-list versions)])
        [:div.dependents
         [:h3 "Dependents (current and past) "
          [:span.count (count (node :dependents))]]
         (project-dep-list (sort (node :dependents)))]]))))

(defn project-version-detail [gid aid ver]
  (let [coord (lein-coord gid aid ver)
        aid (-> aid name keyword)
        node (get-in project/graph [aid :versions coord])]
    (when node
      (page
       (render-coord coord)
       [:div.project-detail.version-detail
        (project-overview
         (assoc node
           :description (html "Main project: "
                              [:a {:href (str "/" (url-encode (name aid)))
                                   :id "project-link"}
                               (name aid)])))
        [:div.dependencies
         [:h3 "Dependencies " [:span.count (count (node :dependencies))]]
         (project-dep-list (node :dependencies))]
        [:div.dependents
         [:h3 "Dependents " [:span.count (count (node :dependents))]]
         (project-dep-list (sort (node :dependents)))]]))))

(defn project-list [pids]
  [:ul.project-list
   (for [pid pids
         :let [pid (-> pid name keyword)
               node (or (project/graph pid) {})]]
     [:li
      (link-to
       (name pid)
       [:span.name (h (name pid))]
       [:span.stat.dep-count
        [:span.label "Dependents"] " " [:span.value (count (node :dependents))]]
       [:span.stat.versions
        [:span.label "Versions"] " " [:span.value (count (node :versions))]]
       (when (node :watchers)
         [:span.stat.watchers
          [:span.label "Watchers"] " "[:span.value (node :watchers)]])
       (when (node :forks)
         [:span.stat.forks
          [:span.label "Forks"] " " [:span.value (node :forks)]]))])
   [:span.clear]])

(def per-page 40)

(defn neg-guard [x]
  (if (neg? x) 0 x))

(defn paginate [content offset item-count]
  (let [qp (dissoc (:query-params *req* {}) ;pass along all GET params
                   "_")                     ;except cache buster
        next-url (url (:uri *req*)
                      (assoc qp "offset" (+ offset per-page)))
        prev-url (url (:uri *req*)
                      (assoc qp "offset" (neg-guard (- offset per-page))))
        next-tag (if (< item-count per-page)
                   :a.button.next.inactive
                   :a.button.next)
        prev-tag (if (pos? offset)
                   :a.button.prev
                   :a.button.prev.inactive)]
    [:div.paginated
     content
     [:p.nav
      [next-tag {:href next-url} "Next"]
      [prev-tag {:href prev-url} "Previous"]]]))

(defn paginated-list [pids offset]
  (let [window (take per-page (drop offset pids))]
    (paginate
     (project-list window)
     offset
     (count window))))

(defn projects [query sort offset]
  (page
   nil
   (let [pids (cond
                (and (nil? query) (nil? sort)) project/most-used
                (nil? sort) (project/find-projects query))
         title (if query
                 (str "Search: " (h query))
                 "Projects")]
     [:div#projects
      [:h2 title]
      (paginated-list pids offset)])))

(defn not-found []
  (page
   "Page Not Found"
   [:p "Sorry, the page you're looking for could not be found."]))
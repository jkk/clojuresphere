(ns clojuresphere.layout
  (:use [clojuresphere.core :only [*req*]]
        [clojuresphere.util :only [url-encode qualify-name maven-coord lein-coord
                                   parse-int]]
        [hiccup.page-helpers :only [html5 include-js include-css
                                    javascript-tag link-to url]]
        [hiccup.form-helpers :only [form-to submit-button]]
        [hiccup.core :only [h html]])
  (:require [clojuresphere.project-model :as project]
            [clojure.java.io :as io]))

(def site-name "ClojureSphere")

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
         [:div#header
          [:div.inner
           [:h1 (link-to "/" site-name)]
           [:p#tagline "Browse the open-source Clojure ecosystem"]
           (form-to [:get "/_search"]
                    [:input {:name "query" :size 30 :id "query"
                             :value (get-in *req* [:query-params "query"])
                             :type "search" :placeholder "Search"}] " "
                             (submit-button "Go"))]]
         [:div#content-shell
          content]
         [:div#footer
          [:p#links (link-to "http://github.com/jkk/clojuresphere" "GitHub")]
          [:p#copyright "Made by "
           (link-to "http://jkkramer.com" "Justin Kramer") " - "
           (link-to "http://twitter.com/jkkramer" "@jkkramer")]
          [:p#stats (str (count project/graph) " projects indexed "
                         (-> project/graph-data-file
                             io/resource io/file .lastModified (java.util.Date.)))]]]
        (include-js "/js/jquery.js"
                    "/js/history.adapter.jquery.js"
                    "/js/history.js"
                    "/js/main.js")]))))

(defn coord-url [coord]
  (let [[gid aid ver] (maven-coord coord)]
    (str "/" (url-encode aid)
         "/" (url-encode gid)
         "/" (url-encode (or ver "")))))

;; TODO: break this up into manageable pieces
(defn project-detail [pid]
  (let [pid (-> pid name keyword)
        node (project/graph pid)]
    (when node
      (page
       (name pid)
       [:div.project-detail
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
         [:div.clear]]
        [:div.dependencies
         [:h3 "Dependencies (current and past) "
          [:span.count (count (node :dependencies))]]
         (if (zero? (count (node :dependencies)))
           [:p.none "None"]
           [:ul.dep-list
            (for [aid (node :dependencies)]
              [:li (link-to (name aid) (h (name aid)))])
            [:span.clear]])]
        [:div.versions
         [:h3 "Versions " [:span.count (count (node :versions))]]
         (if (zero? (count (node :versions)))
           [:p.none "None"]
           [:ul.version-list
            (for [[[vname vver :as coord] vinfo] (project/most-used-versions pid)]
              [:li
               (link-to
                (coord-url coord)
                [:span.version
                 [:span.vver (h (or vver "[none]"))]
                 [:span.vname (h (str vname))]]
                ;; TODO: count only unique artifact ids?
                [:span.count (count (vinfo :dependents))])])
            [:span.clear]])]
        [:div.dependents
         [:h3 "Dependents (current and past) "
          [:span.count (count (node :dependents))]]
         (if (zero? (count (node :dependents)))
           [:p.none "None"]
           [:ul.dep-list
            (for [aid (sort (node :dependents))]
              [:li (link-to (name aid) (h (name aid)))])
            [:span.clear]])]]))))

(defn render-coord [coord]
  (let [[name ver] (apply lein-coord coord)]
    (str name " " ver)))

(defn project-version-detail [gid aid ver]
  (let [coord (lein-coord gid aid ver)
        aid (-> aid name keyword)
        node (get-in project/graph [aid :versions coord])]
    (when node
      (page
       (render-coord coord)
       [:div.project-detail
        [:div.overview
         [:p.description "Main project: " [:a {:href (str "/" (url-encode (name aid)))
                                               :id "project-link"}
                                           (name aid)]]
         ;; TODO: show github/clojars links
         [:div.clear]]
        [:div.dependencies
         [:h3 "Dependencies " [:span.count (count (node :dependencies))]]
         (if (zero? (count (node :dependencies)))
           [:p.none "None"]
           [:ul.dep-list.ver
            (for [coord (node :dependencies)]
              [:li (link-to (coord-url coord) (h (render-coord coord)))])
            [:span.clear]])]
        [:div.dependents
         [:h3 "Dependents " [:span.count (count (node :dependents))]]
         (if (zero? (count (node :dependents)))
           [:p.none "None"]
           [:ul.dep-list.ver
            (for [coord (sort (node :dependents))]
              [:li (link-to (coord-url coord) (h (render-coord coord)))])
            [:span.clear]])]]))))

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

(defn top-projects [offset]
  (page
   nil
   [:div#top-projects
    [:h2 "Top Projects"]
    (paginated-list project/most-used offset)]))

(defn search-results [query offset]
  (page
   (str "Search Results: " (h query))
   [:div#search-results
    (paginated-list (project/find-projects query) offset)]))

(defn not-found []
  (page
   "Page Not Found"
   [:p "Sorry, the page you're looking for could not be found."]))
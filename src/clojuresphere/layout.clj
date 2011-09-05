(ns clojuresphere.layout
  (:use [clojuresphere.util :only [url-encode qualify-name make-dep]]
        [hiccup.page-helpers :only [html5 include-js include-css
                                    javascript-tag link-to]]
        [hiccup.core :only [h *base-url*]])
  (:require [clojuresphere.project-model :as project]))

(def site-name "ClojureSphere")

;; TODO: search form
(defn page [title & body]
  (html5
   [:meta {:http-equiv "content-type" :content "text/html; charset=utf-8"}]
   [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
   [:title (str (when title (str (h title) " - ")) site-name)]
   [:meta {:name "description" :content "Browse the entire Clojure ecosystem"}]
   (include-css "/css/main.css")
   [:body
    [:div#page-shell
     [:div#header
      [:div.inner
       [:h1 (link-to "/" site-name)]
       [:p#tagline "A browsable dependency graph of the Clojure ecosystem"]]]
     [:div#content-shell
      [:div#content
       (when title
         [:h2#page-title (h title)])
       body]]
     [:div#footer
      [:p#links (link-to "http://github.com/jkk/clojuresphere" "GitHub")]
      [:p#copyright "Made by " (link-to "http://jkkramer.com" "Justin Kramer")]
      ;; TODO: show last updated
      ]]
    (javascript-tag
    (str "var CS = {baseUrl: \"" *base-url* "\"};"))
   (include-js "/js/jquery.js" "/js/main.js")]))

;; TODO: break this up into manageable pieces
(defn project-detail [pid]
  (let [pid (-> pid name keyword)
        node (or (project/graph pid) {})]
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
       [:h3 "Dependencies " [:span.count (count (node :dependencies))]]
       (if (zero? (count (node :dependencies)))
         [:p.none "None"]
         [:ul.dep-list
          (for [dep (node :dependencies)]
            [:li (link-to (name dep) (h (name dep)))])
          [:span.clear]])]
      [:div.versions
       [:h3 "Versions " [:span.count (count (node :versions))]]
       (if (zero? (count (node :dependents)))
         [:p.none "None"]
         [:ul.version-list
          (for [[[vname vver] vinfo] (project/most-used-versions pid)
                :let [[gid aid] (qualify-name vname)]]
            [:li
             (link-to
              (str aid "/" gid "/" (url-encode (or vver "")))
              [:span.version
               [:span.vver (h (or vver "[none]"))]
               [:span.vname (h (str vname))]]
              [:span.count (count (vinfo :dependents))])])
          [:span.clear]])]
      [:div.dependents
       [:h3 "Dependents " [:span.count (count (node :dependents))]]
       (if (zero? (count (node :dependents)))
         [:p.none "None"]
         [:ul.dep-list
          (for [dep (sort (node :dependents))]
            [:li (link-to (name dep) (h (name dep)))])
          [:span.clear]])]])))

(defn render-dep [dep]
  (let [[name ver] (apply make-dep dep)]
    (str name " " ver)))

(defn dep-url [dep]
  (let [[gid aid ver] (qualify-dep dep)]
    (str "/" (url-encode gid) "/" (url-encode aid) "/" (url-encode ver))))

(defn project-version-detail [gid aid ver]
  (let [dep (make-dep gid aid ver)
        aid (-> aid name keyword)
        node (get-in project/graph [aid :versions dep])]
    (when node
      (page
       (render-dep dep)
       [:div.project-detail
        [:div.overview
         [:p.description "Main project: " (link-to (str "/" (url-encode (name aid)))
                                                   (name aid))]
         ;; TODO: show github/clojars links
         [:div.clear]]
        [:div.dependencies
         [:h3 "Dependencies " [:span.count (count (node :dependencies))]]
         (if (zero? (count (node :dependencies)))
           [:p.none "None"]
           [:ul.dep-list.ver
            (for [dep (node :dependencies)]
              [:li (link-to (dep-url dep) (h (render-dep dep)))])
            [:span.clear]])]
        [:div.dependents
         [:h3 "Dependents " [:span.count (count (node :dependents))]]
         (if (zero? (count (node :dependents)))
           [:p.none "None"]
           [:ul.dep-list.ver
            (for [dep (sort (node :dependents))]
              [:li (link-to (dep-url dep) (h (render-dep dep)))])
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

(defn welcome []
  (page
   nil
   [:div#top-projects
    [:h2 "Top Projects"]
    (project-list (take 20 project/most-used))]
   [:div#random-projects
    [:h2 "Random Projects"]
    (project-list (repeatedly 20 project/random))]
   ;; TODO: prev/next nav buttons
   ;; TODO: stats (total # projects)
   ))

(defn not-found []
  (page
   "Page Not Found"
   [:p "Sorry, the page you're looking for could not be found."]))
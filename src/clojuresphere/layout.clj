(ns clojuresphere.layout
  (:use [hiccup.page-helpers :only [html5 include-js include-css
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
   (include-css "css/main.css")
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
   (include-js "js/jquery.js" "js/main.js")]))


(defn project-detail [pid]
  (let [pid (-> pid name keyword)
        node (or (project/graph pid) {})]
    (page
     (name pid)
     [:div.project-detail
      ;; TODO: more accurate description
      [:p.description (node :description)]
      (when (node :github-url)
        [:p.github (link-to (node :github-url) "GitHub")])
      (when (node :clojars-url)
        [:p.github (link-to (node :clojars-url) "Clojars")])
      (when (node :watchers)
        [:p.watchers [:span.label "Watchers"] " " [:span.value (node :watchers)]])
      (when (node :forks)
        [:p.forks [:span.label "Forks"] " " [:span.value (node :forks)]])
      ;; TODO: dependencies
      ;; TODO: dependents
      ;; TODO: most-used versions
      ])))

(defn project-list [pids]
  [:ul.project-list
   (for [pid pids
         :let [pid (-> pid name keyword)
               node (or (project/graph pid) {})]]
     [:li
      (link-to
       (name pid)
       [:span.name (name pid)]
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
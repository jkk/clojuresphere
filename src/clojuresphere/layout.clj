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


(defn project-detail [p]
  (let [p (-> p name keyword)
        info (project/info p)
        node (project/graph p)
        desc (or (get-in info [:github :description])
                 (first (get info :description)))
        dep-count (count (node :in))
        watchers (reduce + (map :watchers (get info :github)))
        forks (reduce + (map :forks (get info :github)))]
    (page
     (name p)
     [:div.project-detail
      ;; TODO: more accurate description
      [:p.description desc]
      (when (seq (get info :url))
        [:p.homepage (link-to (first (get info :url)) "Homepage")])
      (when (seq (get info :github))
        [:p.github (link-to (:url (first (get info :github))) "GitHub")])
      (when-not (zero? watchers)
        [:p.watchers [:span.label "Watchers"] " " [:span.value watchers]])
      (when-not (zero? forks)
        [:p.forks [:span.label "Forks"] " " [:span.value forks]])
      ;; TODO: dependencies
      ;; TODO: dependents
      ;; TODO: most-used versions
      ])))

(defn project-list [projects]
  [:ul.project-list
   (for [p projects
         :let [p (-> p name keyword)
               info (project/info p)
               node (project/graph p)
               dep-count (count (node :in))
               watchers (reduce + (map :watchers (get info :github)))
               forks (reduce + (map :forks (get info :github)))]]
     [:li
      (link-to
       (name p)
       [:span.name (name p)]
       [:span.stat.dep-count
        [:span.label "Dependents"] " " [:span.value dep-count]]
       [:span.stat.versions
        [:span.label "Versions"] " " [:span.value (count (get info :dep))]]
       (when-not (zero? watchers)
         [:span.stat.watchers [:span.label "Watchers"] " " [:span.value watchers]])
       (when-not (zero? forks)
         [:span.stat.forks [:span.label "Forks"] " " [:span.value forks]]))])
     [:span.clear]])

(defn welcome []
  (page
   nil
   [:div#top-projects
    [:h2 "Top Projects"]
    (project-list (take 20 (project/most-used)))]
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
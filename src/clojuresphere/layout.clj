(ns clojuresphere.layout
  (:use [hiccup.page-helpers :only [html5 include-js include-css javascript-tag
                                    link-to mail-to unordered-list]]
        [hiccup.core :only [h *base-url*]]))

(def site-name "ClojureSphere")

;; TODO: search form
(defn page [title & body]
  (html5
   [:meta {:http-equiv "content-type" :content "text/html; charset=utf-8"}]
   [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
   [:title (h title)]
   [:meta {:name "description" :content "Browse the entire Clojure ecosystem"}]
   (include-css "css/main.css")
   [:body
    [:div#page-shell
     [:div#header
      [:h1 (link-to "/" site-name)]]
     [:div#content-shell
      [:div#content
       [:h2#page-title (h title)]
       body]]
     [:div#footer
      [:p#links (link-to "http://github.com/jkk/clojuresphere" "GitHub")]
      [:p#copyright "Made by " (link-to "http://jkkramer.com" "Justin Kramer")]]]
    (javascript-tag
    (str "var CS = {baseUrl: \"" *base-url* "\"};"))
   (include-js "js/jquery.js" "js/main.js")]))

(defn welcome []
  (page
   "Welcome"
   ;; TODO: popular projects (by dependent count, github followers, etc)
   [:p "hello"]))

(defn not-found []
  (page
   "Page Not Found"
   [:p "Sorry, the page you're looking for could not be found."]))
(ns clojuresphere.layout
  (:use [clojuresphere.util :only [url-encode parse-int date->days-ago *req*]]
        [clojure.string :only [capitalize join split]]
        [hiccup.page :only [html5 include-js include-css]]
        [hiccup.element :only [link-to javascript-tag link-to]]
        [hiccup.form :only [form-to submit-button]]
        [hiccup.util :only [url]]
        [hiccup.core :only [h html]])
  (:require [clojuresphere.project-model :as proj]))

(def site-name "ClojureSphere")

(defn page-header []
  [:div#header
   [:div.inner
    [:h1 (link-to "/" site-name)]
    [:p#tagline "Browse the open-source Clojure ecosystem"]
    (form-to [:get "/"]
             [:input {:name "query" :size 30 :id "query"
                      :value (get-in *req* [:query-params "query"])
                      :type "text" :placeholder "Search"}] " "
                      (submit-button "Go"))
    [:div#links
     [:span#api-link (link-to "/api" "API")] " "
     [:span#github-link (link-to "http://github.com/jkk/clojuresphere" "GitHub")] " "
     [:span#built-by "Built by "
      (link-to "http://jkkramer.com" "Justin Kramer") " - "
      (link-to "http://twitter.com/jkkramer" "@jkkramer")]]]])

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
         [:div#footer]]
        (javascript-tag (str "var Globals = {siteName: \"" site-name "\"};"))
        (include-js "/js/jquery.js"
                    "/js/history.adapter.jquery.js"
                    "/js/history.js"
                    "/js/main.js")]))))

(defn get-github-url [props]
  (when-let [gh (:github props)]
    (let [owner (if (map? (:owner gh))
                  (-> gh :owner :login)
                  (:owner gh))]
      (str "https://github.com/" owner "/" (:name gh)))))

(defn project-overview [gid aid props]
  (let [github-url (get-github-url props)
        pid (if (or (empty? gid) (= gid aid))
              aid (str gid "/" aid))
        desc (or (:description props)
                 (-> props :clojars :description)
                 (-> props :github :description))]
    [:div.overview
     (when-not (empty? desc)
       [:p.description desc])
     [:p.latest-versions
      (when (:stable props)
        [:span.ver.stable
         [:span.label "Stable"] " "
         [:span.version (str "[" pid " \"" (:stable props) "\"]")]])
      " "
      [:span.ver.latest
       [:span.label "Latest"] " "
       [:span.version (str "[" pid " \"" (:latest props) "\"]")]]]
     [:div.tidbits
      (when-let [homepage (or (-> props :clojars :homepage not-empty)
                              (-> props :github :homepage not-empty))]
        (when (not= homepage github-url)
          [:p.homepage (link-to homepage "Homepage")]))
      (when github-url
        [:p.github (link-to github-url "GitHub")])
      (when-let [clojars-url (-> props :clojars :url)]
        [:p.clojars (link-to clojars-url "Clojars")])
      (when (:watchers props)
        [:p.watchers [:span.label "Watchers"] " " [:span.value (:watchers props)]])
      (when (-> props :github :forks)
        [:p.forks [:span.label "Forks"] " " [:span.value (-> props :github :forks)]])
      (when (:downloads props)
        [:p.downloads [:span.label "Clojars Downloads"] " " [:span.value (:downloads props)]])
      (when (and (:updated props) (not (zero? (:updated props))))
        [:p.updated
         [:span.label "Updated"] " "
         [:span.value (date->days-ago (:updated props)) " days ago"]])
      [:div.clear]]]))

(defn project-dep-list [deps]
  (if (zero? (count deps))
    [:p.none "None"]
    [:ul.dep-list
     ;; straight hiccup is slow for large lists
     (apply str (mapcat (fn [[dname dver]]
                          ["<li>"
                           "<a href='/" dname "'><span class='name'>" (name dname)
                           "</span> <span class='ver'>" dver "</span></a></li>"])
                        deps))
     [:span.clear]]))

(defn project-version-list [pid props versions]
  (if (zero? (count versions))
    [:p.none "None"]
    [:ul.version-list
     (for [ver versions]
       [:li
        (link-to
         (str "/" pid "/" (url-encode ver))
         [:span.version
          [:span.vver ver]]
         [:span.count (count (get-in props [:versions ver :dependents]))])])
     [:span.clear]]))

(defn project-detail [pid]
  (let [props (proj/graph pid)]
    (when props
      (let [gid (namespace pid)
            aid (name pid)
            latest (:latest props)
            deps (get-in props [:versions latest :dependencies])]
        (page
         aid
         [:div.project-detail
          (project-overview gid aid props)
          [:div.dependencies
           [:h3 "Latest Dependencies "
            [:span.count (count deps)]]
           (project-dep-list deps)]
          (let [versions (proj/sort-versions (keys (:versions props)))]
            [:div.versions
             [:h3 "Versions " [:span.count (count versions)]]
             (project-version-list pid props versions)])
          [:div.dependents
           [:h3 "Dependents (latest versions only)"
            [:span.count (-> props :dependent-counts :latest)]]
           (if (= 'org.clojure/clojure pid)
             [:p.none "Everything!"]
             (project-dep-list (proj/get-dependents props)))]])))))

(defn project-version-detail [gid aid ver]
  (let [coord (proj/lein-coord gid aid ver)
        pid (symbol gid aid)
        props (get-in proj/graph [pid :versions ver])]
    (when props
      (page
       (str gid "/" aid " " ver)
       [:div.project-detail.version-detail
        [:div.overview
         [:p.description
          "Main project: "
          [:a {:href (str "/" pid)
               :id "project-link"}
           pid]]]
        [:div.dependencies
         [:h3 "Dependencies for this version "
          [:span.count (count (:dependencies props))]]
         (project-dep-list (:dependencies props))]
        [:div.dependents
         [:h3 "Dependents on this version "
          [:span.count (count (:dependents props))]]
         (project-dep-list (sort (:dependents props)))]]))))

(defn project-list [pids & [sort-key]]
  [:ul.project-list
   (for [pid pids
         :let [props (or (proj/graph pid) {})]]
     [:li
      (link-to
       (str pid)
       [:span.name (h (name pid))]
       #_[:span.stat.score
        [:span.label "Score"] " " [:span.value (proj/score-project props)]]
       [:span.stat.dep-count
        [:span.label "Used by"] " " [:span.value (-> props :dependent-counts :all)]]
       (when (:watchers props)
         [:span.stat.watchers
          [:span.label "Watchers"] " " [:span.value (:watchers props)]])
       (when (:downloads props)
         [:span.stat.downloads
          [:span.label "Downloads"] " " [:span.value (:downloads props)]])
       #_(when (and (:updated props) (not (zero? (:updated props))))
         [:span.stat.updated
          [:span.label "Updated"] " " [:span.value (date->days-ago (:updated props))
                                       [:span.days-ago-label " days ago"]]]))])
   [:span.clear]])

(def per-page 90)

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

(defn paginated-list [pids offset & [sort-key]]
  (let [window (take per-page (drop offset pids))]
    (if (empty? window)
      [:p.none "No projects found"]
      (paginate
       (project-list window sort-key)
       offset
       (count window)))))

(defn projects-per-quarter-chart []
  (let [yrange (range proj/first-year (inc proj/last-year))]
    [:img#projects-per-quarter
     {:src (url "https://chart.googleapis.com/chart"
                {:cht "lc"
                 :chs "214x100"
                 :chxt "x,y"
                 :chxl (str "0:|" (join "|" yrange) "|")
                 :chxp (str "0," (join "," (map (partial * 4)
                                                (range (count yrange)))))
                 :chxr (str "0,0," (count proj/quarterly-counts))
                 :chd (str "t:" (join "," proj/quarterly-counts))
                 :chco "449966"
                 :chm "B,eeffeebb,0,0,0"
                 :chds "a"})}]))

(defn stats []
  [:div#stats
   [:h3 "Stats"]
   [:dl
    [:dt "Projects"]
    [:dd proj/project-count]
    [:dt "GitHub projects"]
    [:dd proj/github-count]
    [:dt "Clojars projects"]
    [:dd proj/clojars-count]
    [:dt "Projects per quarter (GitHub only)"]
    [:dd (projects-per-quarter-chart)]
    [:dt "Last indexed"]
    [:dd#last-indexed (str proj/last-updated)]]])

(defn projects [query sort offset]
  (page
   nil
   (let [sort (or sort "score")
         random? (= "random" sort)
         pids (cond
               (seq query) (proj/sort-pids (proj/find-pids query) sort)
               random?     (repeatedly per-page proj/random)
               :else       (or (proj/sorted-pids (keyword sort))
                               (proj/sorted-pids :dependents)))
         title (if (seq query)
                 (str "Search: " (h query))
                 "Projects")]
     [:div
      [:div#projects
       [:h2 title]
       [:div.sort-links
        "Sort by"
        (for [s ["score" "dependents" "watchers" "downloads" "updated" #_"random"]]
          (let [a-tag (if (= s sort) :a.active :a)]
            [a-tag {:href (url "/" {:sort s :query (str query)})}
             (capitalize s)]))]
       (paginated-list pids (if random? 0 offset) (keyword sort))]
      (stats)
      [:div.clear]])))

(defn not-found []
  (page
   "Page Not Found"
   [:p "Sorry, the page you're looking for could not be found."]))
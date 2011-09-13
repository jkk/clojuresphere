(ns clojuresphere.layout
  (:use [clojuresphere.util :only [url-encode qualify-name maven-coord lein-coord
                                   parse-int date->days-ago *req*]]
        [clojure.string :only [capitalize join]]
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
                      (submit-button "Go"))
    [:div#links
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
   [:p.description (:description node)]
   [:div.tidbits
    (when (:github-url node)
      [:p.github (link-to (:github-url node) "GitHub")])
    (when (:clojars-url node)
      [:p.clojars (link-to (:clojars-url node) "Clojars")])
    (when (:watchers node)
      [:p.watchers [:span.label "Watchers"] " " [:span.value (:watchers node)]])
    (when (:forks node)
      [:p.forks [:span.label "Forks"] " " [:span.value (:forks node)]])
    (when (and (:updated node) (not (zero? (:updated node))))
      [:p.updated
       [:span.label "Updated"] " "
       [:span.value (date->days-ago (:updated node)) " days ago"]])
    [:div.clear]]])

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
          [:span.count (count (:dependencies node))]]
         (project-dep-list (:dependencies node))]
        (let [versions (project/most-used-versions pid)]
          [:div.versions
           [:h3 "Versions " [:span.count (count versions)]]
           (project-version-list versions)])
        [:div.dependents
         [:h3 "Dependents (current and past) "
          [:span.count (count (:dependents node))]]
         (project-dep-list (sort (:dependents node)))]]))))

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
         [:h3 "Dependencies " [:span.count (count (:dependencies node))]]
         (project-dep-list (:dependencies node))]
        [:div.dependents
         [:h3 "Dependents " [:span.count (count (:dependents node))]]
         (project-dep-list (sort (:dependents node)))]]))))

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
        [:span.label "Used by"] " " [:span.value (count (:dependents node))]]
       (when (:watchers node)
         [:span.stat.watchers
          [:span.label "Watchers"] " "[:span.value (:watchers node)]])
       (when (and (:updated node) (not (zero? (:updated node))))
         [:span.stat.updated
          [:span.label "Updated"] " " [:span.value (date->days-ago (:updated node))
                                       [:span.days-ago-label " days ago"]]]))])
   [:span.clear]])

(def per-page 30)

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

(defn projects-per-quarter-chart []
  (let [yrange (range project/first-year (inc project/last-year))]
    [:img#projects-per-quarter
     {:src (url "https://chart.googleapis.com/chart"
                {:cht "lc"
                 :chs "214x100"
                 :chxt "x,y"
                 :chxl (str "0:|" (join "|" yrange) "|")
                 :chxp (str "0," (join "," (map (partial * 4)
                                                (range (count yrange)))))
                 :chxr (str "0,0," (count project/quarterly-counts))
                 :chd (str "t:" (join "," project/quarterly-counts))
                 :chco "449966"
                 :chm "B,eeffeebb,0,0,0"
                 :chds "a"})}]))

(defn stats []
  [:div#stats
   [:h3 "Stats"]
   [:dl
    [:dt "Projects"]
    [:dd project/project-count]
    [:dt "GitHub projects"]
    [:dd project/github-count]
    [:dt "Clojars projects"]
    [:dd project/clojars-count]
    [:dt "Projects per quarter (GitHub only)"]
    [:dd (projects-per-quarter-chart)]
    [:dt "Last indexed"]
    [:dd#last-indexed (str project/last-updated)]]])

(defn projects [query sort offset]
  (page
   nil
   (let [sort (or sort "dependents")
         random? (= "random" sort)
         pids (cond
               (seq query) (project/sort-pids (project/find-pids query) sort)
               random?     (repeatedly per-page project/random)
               :else       (or (project/sorted-pids (keyword sort))
                               (project/sorted-pids :dependents)))
         title (if (seq query)
                 (str "Search: " (h query))
                 "Projects")]
     [:div
      [:div#projects
       [:h2 title]
       [:div.sort-links
        "Sort by"
        (for [s ["dependents" "watchers" "updated" "random"]]
          (let [a-tag (if (= s sort) :a.active :a)]
            [a-tag {:href (url "/" {:sort s :query query})} (capitalize s)]))]
       (paginated-list pids (if random? 0 offset))]
      (stats)
      [:div.clear]])))

(defn not-found []
  (page
   "Page Not Found"
   [:p "Sorry, the page you're looking for could not be found."]))
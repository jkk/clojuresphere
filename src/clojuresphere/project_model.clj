(ns clojuresphere.project-model
  (:use [clojuresphere.util :only [read-resource all-dependents
                                   latest-coord?]]
        [clojure.java.io :as io]))

;; we don't need no stinkin database

(def graph-data-file "project_graph.clj")
(defonce graph (read-resource graph-data-file))
(def project-count (count (filter #(or (:github %) (:clojars %)) (vals graph))))
(def github-count (count (filter :github (vals graph))))
(def clojars-count (count (filter :clojars (vals graph))))
(def last-updated (-> graph-data-file
                      io/resource io/file .lastModified (java.util.Date.)))

(def sorted-pids
  {:dependents (->> graph (sort-by (comp :all :dependent-counts val) >) keys vec)
   :watchers (->> graph (sort-by (comp #(:watchers % 0) val) >) keys vec)
   :forks (->> graph (sort-by (comp #(:forks (:github %) 0) val) >) keys vec)
   :updated (->> graph (sort-by (comp #(:updated % 0) val)
                                (comp - compare))
                 keys vec)})

;;

(defn year-quarter [date-str]
  (let [date (clojure.instant/read-instant-date date-str)]
    [(+ 1900 (.getYear date))
     (quot (.getMonth date) 3)]))

(def creates-per-quarter
  (->> (vals graph)
       (keep (comp :created :github))
       (group-by year-quarter)
       (into (sorted-map))))

(def first-year (first (key (first creates-per-quarter))))
(def last-year (first (key (last creates-per-quarter))))
(def quarterly-counts
  (reductions + (map count (vals creates-per-quarter))))

;;

(defn random []
  (rand-nth (:dependents sorted-pids)))

(defn find-pids [query]
  (let [query-re (re-pattern (str "(?i)" (or query "")))]
    (for [[pid props] graph
          :when (some #(when % (re-find query-re %))
                      [(name pid)
                       (-> props :github :description)
                       (-> props :clojars :description)])]
      pid)))

(defn sort-pids [pids field]
  (let [field (keyword (name field))
        key-fn (condp = field
                 :watchers :watchers
                 :updated :updated
                 :created :created
                 (comp :all :dependent-counts))]
    (sort-by (comp #(or % 0) key-fn graph) > pids)))


(defn get-dependents [node]
  (sort-by #(get-in graph [(first %) :dependent-counts :all])
           >
           (all-dependents node graph latest-coord?)))
(ns clojuresphere.project-model
  (:use [clojuresphere.util :only [read-gz-resource]]
        [clojure.java.io :as io]))

;; we don't need no stinkin database

(def graph-data-file "project_graph.clj.gz")
(defonce graph (read-gz-resource graph-data-file))
(def project-count (count (filter #(or (:github-url %) (:clojars-url %)) (vals graph))))
(def github-count (count (filter :github-url (vals graph))))
(def clojars-count (count (filter :clojars-url (vals graph))))
(def last-updated (-> graph-data-file
                          io/resource io/file .lastModified (java.util.Date.)))

(def sorted-pids
  {:dependents (->> graph (sort-by (comp count :dependents val) >) keys vec)
   :watchers (->> graph (sort-by (comp #(:watchers % 0) val) >) keys vec)
   :forks (->> graph (sort-by (comp #(:forks % 0) val) >) keys vec)
   :updated (->> graph (sort-by (comp #(:updated % 0) val) >) keys vec)})

;;

(defn year-quarter [stamp]
  (let [date (java.util.Date. (* stamp 1000))]
    [(+ 1900 (.getYear date))
     (quot (.getMonth date) 3)]))

(def creates-per-quarter
  (->> (vals graph)
       (map :created)
       (remove zero?)
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
    (for [[pid pinfo] graph
          :when (some #(when % (re-find query-re %))
                      [(name pid) (pinfo :description)])]
      pid)))

(defn sort-pids [pids field]
  (let [field (keyword (name field))
        key-fn (if (= :dependents field)
                 (comp count :dependents)
                 #(field % 0))]
    (sort-by (comp key-fn graph) > pids)))

;;

(defn most-used-versions [pid]
  (let [versions (get-in graph [pid :versions])]
    (sort-by (comp count :dependents val) > versions)))


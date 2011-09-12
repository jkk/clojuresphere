(ns clojuresphere.project-model
  (:use [clojuresphere.util :only [read-gz-resource]]
        [clojure.java.io :as io]))

;; we don't need no stinkin database

(def graph-data-file "project_graph.clj.gz")
(defonce graph (read-gz-resource graph-data-file))
(def project-count (count graph))
(def last-updated (-> graph-data-file
                          io/resource io/file .lastModified (java.util.Date.)))

(def sorted-pids
  {:dependents (->> graph (sort-by (comp count :dependents val) >) keys vec)
   :watchers (->> graph (sort-by (comp #(:watchers % 0) val) >) keys vec)
   :forks (->> graph (sort-by (comp #(:forks % 0) val) >) keys vec)
   :updated (->> graph (sort-by (comp #(:updated % 0) val) >) keys vec)})

(defn random []
  (rand-nth (:dependents sorted-pids)))

(defn most-used-versions [pid]
  (let [versions (get-in graph [pid :versions])]
    (sort-by (comp count :dependents val) > versions)))

(defn find-projects [query]
  (let [query-re (re-pattern (str "(?i)" (or query "")))]
    (for [[pid pinfo] graph
          :when (some #(when % (re-find query-re %))
                      [(name pid) (pinfo :description)])]
      pid)))

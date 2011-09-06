(ns clojuresphere.project-model
  (:use [clojuresphere.util :only [read-gz-resource]]))

;; we don't need no stinkin database

(def graph-data-file "project_graph.clj.gz")
(defonce graph (read-gz-resource graph-data-file))
(defonce most-used (->> graph
                        (sort-by (comp count :dependents val) >)
                        keys vec))

(defn random []
  (rand-nth most-used))

(defn most-used-versions [pid]
  (let [versions (get-in graph [pid :versions])]
    (sort-by (comp count :dependents val) > versions)))

(defn find-projects [query]
  (let [query-re (re-pattern (str "(?i)" (or query "")))]
    (for [[pid pinfo] graph
          :when (some #(when % (re-find query-re %))
                      [(name pid) (pinfo :description)])]
      pid)))

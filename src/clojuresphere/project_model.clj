(ns clojuresphere.project-model
  (:use [clojuresphere.util :only [read-gz-resource]]))

;; we don't need no stinkin database

(defonce graph (read-gz-resource "project_graph.clj.gz"))
(defonce most-used (->> graph
                        (sort-by (comp count :dependents val) >)
                        keys vec))

(defn random []
  (rand-nth most-used))

(defn most-used-versions [pid]
  (let [versions (get-in graph [pid :versions])]
    (sort-by (comp count :dependents val) > versions)))

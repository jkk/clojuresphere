(ns clojuresphere.project-model
  (:use [clojuresphere.util :only [read-gz-resource]]))

(defonce info (read-gz-resource "project_info.clj.gz"))
(defonce graph (read-gz-resource "project_graph.clj.gz"))
(defonce project-names (vec (keys graph)))

(defn most-used []
  (map key (sort-by (comp count :in val) > project-graph)))

(defn random []
  (rand-nth project-names))

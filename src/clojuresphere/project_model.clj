(ns clojuresphere.project-model
  (:use [clojuresphere.util :only [read-resource]])
  (:import [org.apache.maven.artifact.versioning DefaultArtifactVersion])
  (:require [clojure.java.io :as io]
            [sundry.io :as sio]
            [clj-http.client :as http]))

;; we don't need no stinkin database

(def graph-url "https://s3.amazonaws.com/clojuresphere.com/project_graph.clj.gz")

(let [[g lu] (if-let [res (io/resource "project_graph.clj.gz")]
               (let [f (io/file res)
                     g (sundry.io/read
                         (java.util.zip.GZIPInputStream.
                           (io/input-stream f)))
                     lu (.toGMTString (java.util.Date.
                                        (.lastModified f)))]
                 (println "Loaded resource")
                 [g lu])
               (let [resp (http/get graph-url {:as :stream})
                     g (sundry.io/read
                         (java.util.zip.GZIPInputStream.
                           (:body resp)))
                     lu (get-in resp [:headers "last-modified"])]
                 [g lu]))]
  (defonce graph g)
  (def last-updated lu))

(def project-count (count (filter #(or (:github %) (:clojars %)) (vals graph))))
(def github-count (count (filter :github (vals graph))))
(def clojars-count (count (filter :clojars (vals graph))))

(def avg-depcount
  (quot
    (reduce + (keep (comp :all :dependent-counts) (vals graph)))
    (count graph)))
(def avg-watchers
  (let [pw (keep :watchers (vals graph))]
    (quot (reduce + pw) (count pw))))
(def avg-downloads
  (let [pd (keep :downloads (vals graph))]
    (quot (reduce + pd) (count pd))))

(defn get-clj-version [props]
  (when-let [latest (:latest props)]
    (let [deps (get-in props [:versions latest :dependencies])
          coord (first (filter #(= 'org.clojure/clojure (first %)) deps))
          ver (when (and coord (second coord))
                (re-find #"^\d+\.\d+" (second coord)))]
      (when ver
        (BigDecimal. ver)))))

(defn score-project [props]
  (let [deps (-> props :dependent-counts :all)
        watchers (or (:watchers props) 0)
        downloads (or (:downloads props) 0)
        base-score (+ (/ deps avg-depcount)
                      (/ watchers avg-watchers)
                      #_(/ downloads avg-downloads))
        ;; penalize projects using old clojure, small bonus for cutting-edge
        damp (condp = (get-clj-version props)
               1.5M 1.1
               1.4M 1.1
               1.2M 0.60
               1.1M 0.25
               1.0M 0.1
               1.0)]
    (* base-score damp)))

(def sorted-pids
  {:dependents (->> graph (sort-by (comp :all :dependent-counts val) >) keys vec)
   :watchers (->> graph (sort-by (comp #(:watchers % 0) val) >) keys vec)
   :forks (->> graph (sort-by (comp #(:forks (:github %) 0) val) >) keys vec)
   :updated (->> graph (sort-by (comp #(:updated % 0) val)
                                (comp - compare))
                 keys vec)
   :downloads (->> graph (sort-by (comp #(:downloads % 0) val) >) keys vec)
   :score (->> graph (sort-by (comp score-project val) >) keys vec)})

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

(def first-year (ffirst (first creates-per-quarter)))
(def last-year (ffirst (last creates-per-quarter)))
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
                 :downloads :downloads
                 :score score-project
                 (comp :all :dependent-counts))]
    (sort-by (comp #(or % 0) key-fn graph) > pids)))

;;

(defn qualify-name [name]
  (let [name (str name)
        name-parts (.split name "/")]
    (if (= 2 (count name-parts))
      name-parts
      [name name])))

(defn maven-coord [[name version]]
  "Turn a Leiningen-format [name version] coordinate into Maven
  [group artifact version] format (with strings for each element)"
  (let [[gid aid] (qualify-name name)]
    [gid aid (str version)]))

(defn lein-coord
  "Return a coordinate in a qualified [group/artifact version] format, as
  used by Leingingen, where the first element is a symbol, and the second a
  string"
  ([name version]
     (let [[gid aid] (qualify-name name)]
       (lein-coord gid aid version)))
  ([group-id artifact-id version]
     (let [group-id (if (and group-id (seq group-id))
                      group-id artifact-id)]
       [(symbol (str group-id "/" artifact-id)) (str version)])))

(defn sort-versions [versions]
  (sort-by #(DefaultArtifactVersion. %)
           (comp - compare)
           versions))

(defn latest-stable-coord? [[pid ver] g]
  (= ver (:stable (get g pid))))

(defn latest-coord? [[pid ver] g]
  (= ver (:latest (get g pid))))

(defn all-dependents [props g & [pred]]
  (let [dep-coords (for [[_ {:keys [dependents]}] (:versions props)
                         dep-coord dependents
                         :when (or (nil? pred) (pred dep-coord g))]
                     dep-coord)]
    (distinct dep-coords)))

(defn count-dependents [props g & [pred]]
  (count (all-dependents props g pred)))

(defn get-dependents [props & [pred]]
  (sort-by #(+ (get-in graph [(first %) :dependent-counts :all])
               (get-in graph [(first %) :watchers] 0))
           >
           (all-dependents props graph (or pred latest-coord?))))
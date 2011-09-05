(ns clojuresphere.fetcher
  (:use [clj-github.repos :only [search-repos]]
        [clojure.data.zip.xml :only [xml-> xml1-> text]]
        [clojuresphere.util :only [url-encode]])
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.java.io :as io]))

(defn qualify-name [name]
  (let [name (str name)
        name-parts (.split name "/")]
    (if (= 2 (count name-parts))
      name-parts
      [name name])))
    
(defn qualify-dep [[name version]]
  (let [[gid aid] (qualify-name name)]
    [gid aid version]))

(defn make-dep
  ([name version]
     (let [[gid aid] (qualify-name name)]
       (make-dep gid aid version)))
  ([group-id artifact-id version]
     (let [group-id (or group-id artifact-id)]
       [(symbol (str group-id "/" artifact-id)) version])))

;; TODO: project.clj should actually be eval'd, not just read
(defn parse-project-data [[defproj name version & opts]]
  (when (= defproj 'defproject)
    (let [[gid aid] (qualify-name name)]
      (assoc (apply hash-map opts)
        :name (str name)
        :group-id gid
        :artifact-id aid
        :version version))))

;; github

(def github-auth {})

(defn fetch-repos [start-page]
  (Thread/sleep 250) ;crude rate limit
  (println "Fetching page" start-page) ;FIXME: proper logging
  (search-repos
   github-auth "clojure" :language "clojure" :start-page start-page))

(defn fetch-all-repos []
  (->> (iterate inc 1)
       (map fetch-repos)
       (take-while seq)
       (apply concat)
       vec))

(defn fetch-repo-project [repo]
  (let [url (str "https://raw.github.com/"
                 (:owner repo) "/"
                 (:name repo) "/"
                 "master/project.clj")]
    (Thread/sleep 250) ;more crude rate limiting
    (println "Fetching" url)
    (try
      (-> url slurp read-string parse-project-data)
      (catch Exception _ nil))))

(defn fetch-all-repo-projects [repos]
  (doall
   (for [repo repos
         :let [project (fetch-repo-project repo)]
         :when project]
     (assoc project
       :github {:url (repo :url)
                :owner (repo :owner)
                :name (repo :name)
                :forks (repo :forks)
                :watchers (repo :watchers)
                :size (repo :size)
                :created (repo :created_at)
                :pushed (repo :pushed_at)
                :open-issues (repo :open_issues)}))))

;; TODO: fetch poms from github

;; clojars

;; TODO: pom files have a crazy amount of options and can even depend
;; on other pom files (see poms for official clojure projects). Decide
;; whether it's worth figuring all that out, or if this is good enough.
(defn parse-pom-xml [xml]
  (let [z (zip/xml-zip xml)
        group-id (xml1-> z :groupId text)
        artifact-id (xml1-> z :artifactId text)
        name (if group-id (str group-id "/" artifact-id) artifact-id)
        version (xml1-> z :version text)
        deps (group-by :scope (for [zdep (xml-> z :dependencies :dependency)]
                                (let [gid (xml1-> zdep :groupId text)
                                      aid (xml1-> zdep :artifactId text)
                                      name (if gid (str gid "/" aid) aid)]
                                  {:dep (make-dep name (xml1-> zdep :version text))
                                   :scope (xml1-> zdep :scope text)})))]
    {:group-id group-id
     :artifact-id artifact-id
     :name name
     :version version
     :description (xml1-> z :description text)
     :dependencies (vec (map :dep (deps nil)))
     :dev-dependencies (vec (map :dep (deps "test")))
     :clojars {:dep (make-dep name version)
               :url (str "http://clojars.org/" name)}}))


(defn read-all-pom-projects [clojars-dir]
  (with-open [r (io/reader (str clojars-dir "all-poms.txt"))]
    (doall
     (remove nil?
             (for [pom-file (line-seq r)]
               (try
                 (let [pom-xml (xml/parse (str clojars-dir pom-file))]
                   (parse-pom-xml pom-xml))
                 (catch Exception _ nil)))))))

(comment

  ;; manual fetching process
  
  (def repos (fetch-all-repos))
  (def github-projects (fetch-all-repo-projects repos))

  ;; rsync -av clojars.org::clojars clojars
  (def clojars-projects (read-all-pom-projects
                         "/Users/tin/src/clj/clojuresphere/clojars/"))

  ;; TODO: associate description/url with particular versions (so we can use the
  ;; best description)
  (def project-info
    (reduce
     (fn [m [aid k info]]
       (update-in m [aid k] (fnil conj #{}) info))
     {}
     (for [p (concat github-projects clojars-projects)
           :let [dep (apply make-dep (map p [:group-id :artifact-id :version]))
                 p (assoc p :dep dep)]
           k [:description :url :github :clojars :dep]
           :let [info (p k)]
           :when info]
       [(-> p :artifact-id keyword) k info])))

  (spit (str (System/getProperty "user.dir") "/resources/project_info.clj")
        (prn-str project-info))
  ;; then gzip project_info.clj

  ;; TODO: include dev-dependencies
  (def project-graph
    (reduce
     (fn [m [gid aid ver dep-gid dep-aid dep-ver :as edge-info]]
       (let [gid (or gid aid)
             p-dep (make-dep gid aid ver)
             dep-dep (make-dep dep-gid dep-aid dep-ver)
             aid (keyword aid)
             dep-aid (keyword dep-aid)]
         (-> m
             (update-in [aid :out dep-aid] (fnil conj []) [p-dep dep-dep])
             (update-in [dep-aid :in aid] (fnil conj []) [p-dep dep-dep]))))
     {}
     (for [p (concat github-projects clojars-projects)
           dep (p :dependencies)]
       (concat (map p [:group-id :artifact-id :version])
               (qualify-dep dep)))))

  (spit (str (System/getProperty "user.dir") "/resources/project_graph.clj")
        (prn-str project-graph))
  ;; then gzip project_graph.clj
  
  )

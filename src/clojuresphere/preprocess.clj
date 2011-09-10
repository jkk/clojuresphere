(ns clojuresphere.preprocess
  (:use [clj-github.repos :only [search-repos]]
        [clojure.data.zip.xml :only [xml-> xml1-> text]]
        [clojuresphere.util :only [url-encode qualify-name qualify-dep make-dep
                                   safe-read-string]])
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.java.io :as io]))

;; TODO: project.clj should actually be eval'd, not just read
(defn parse-project-data [[defproj name version & opts]]
  (when (= defproj 'defproject)
    (let [[gid aid] (qualify-name name)]
      (assoc (apply hash-map opts)
        :name (str name)
        :group-id gid
        :artifact-id aid
        :version version))))

;; TODO: pom files have a crazy amount of options and can even depend
;; on other pom files (see poms for official clojure projects). Decide
;; whether it's worth figuring all that out, or if this is good enough.
;; FIXME: some dependencies use $ variables and that breaks things
;; (e.g., clojure-slick-rogue dependencies)
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
     :dev-dependencies (vec (map :dep (deps "test")))}))

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

;; TODO: some repos have multiple project.clj files (e.g., ring)
(defn fetch-repo-project [repo]
  (let [base-url (str "https://raw.github.com/"
                 (:owner repo) "/"
                 (:name repo) "/"
                 "master/")
        project-url (str base-url "project.clj")
        pom-url (str base-url "pom.xml")]
    (Thread/sleep 250) ;more crude rate limiting
    (println "Fetching" project-url)
    (try
      (-> project-url slurp safe-read-string parse-project-data)
      (catch Exception _
        (Thread/sleep 250)
        (println "Fetching" pom-url)
        (try
          (-> pom-url xml/parse parse-pom-xml)
          (catch Exception _ nil))))))

(defn fetch-all-repo-projects [repos]
  (doall
   (for [repo repos
         :let [project (fetch-repo-project repo)]
         :when project]
     (assoc project
       :github {:url (repo :url)
                :owner (repo :owner)
                :name (repo :name)
                :description (repo :description)
                :forks (repo :forks)
                :watchers (repo :watchers)
                :size (repo :size)
                :created (repo :created_at)
                :pushed (repo :pushed_at)
                :open-issues (repo :open_issues)}))))

;; clojars

(defn read-all-pom-projects [clojars-dir]
  (with-open [r (io/reader (str clojars-dir "all-poms.txt"))]
    (doall
     (remove nil?
             (for [pom-file (line-seq r)]
               (try
                 (let [pom-xml (xml/parse (str clojars-dir pom-file))
                       project (parse-pom-xml pom-xml)]
                   (assoc (dissoc project :description)
                     :clojars {:description (project :description)
                               :url (str "http://clojars.org/" (project :name))}))
                 (catch Exception _ nil)))))))

(comment

  ;; manual fetching process
  ;; TODO: clean up and automate this
  
  (def repos (fetch-all-repos))
  
  ;; special exception for clojure itself (written in java)
  (def clojure-repos (search-repos github-auth "clojure"))
  (def repos (cons (first clojure-repos) repos))

  (def github-projects (fetch-all-repo-projects repos))

  ;; Note: had to manually fix clojure-slick-rogue dependencies

  ;; rsync -av clojars.org::clojars clojars
  (def clojars-projects (read-all-pom-projects
                         "/Users/tin/src/clj/clojuresphere/clojars/"))

  ;; establish versions
  (defn step1 [g]
    (reduce
     (fn [m [aid dep k info]]
       (update-in m [aid :versions dep k] (fnil conj #{}) info))
     g
     (for [p (concat github-projects clojars-projects)
           :let [dep (apply make-dep (map p [:group-id :artifact-id :version]))
                 p (assoc p :dep dep)]
           k [:github :clojars]
           :let [info (p k)]
           :when info]
       [(-> p :artifact-id keyword) dep k info])))

  ;; connections among nodes & versions
  (defn step2 [g]
    (reduce
     (fn [g [gid aid ver dep-dep]]
       (let [gid (or gid aid)
             p-dep (make-dep gid aid ver)
             [dep-gid dep-aid dep-ver] (qualify-dep dep-dep)
             dep-dep (make-dep dep-gid dep-aid dep-ver)
             aid (keyword aid)
             dep-aid (keyword dep-aid)]
         (-> g
             (update-in [aid :dependencies] (fnil conj #{}) dep-aid)
             (update-in [aid :versions p-dep :dependencies]
                        (fnil conj #{}) dep-dep)
             (update-in [dep-aid :dependents] (fnil conj #{}) aid)
             (update-in [dep-aid :versions dep-dep :dependents]
                        (fnil conj #{}) p-dep))))
     g
     (for [p (concat github-projects clojars-projects)
           dep (concat (get p :dependencies) (get p :dev-dependencies))]
       (concat (map p [:group-id :artifact-id :version])
               [dep]))))

  ;; total watchers/forks
  (defn step3 [g]
    (reduce
     (fn [g [pid github]]
       (update-in
        g [pid] assoc
        :watchers (+ (get-in g [pid :watchers] 0) (reduce + (map :watchers github)))
        :forks (+ (get-in g [pid :forks] 0) (reduce + (map :forks github)))))
     g
     (for [[pid {:keys [versions]}] g
           [ver {:keys [github]}] versions
           :when (seq github)]
       [pid github])))

  ;; best description/url
  (defn step4 [g]
    (reduce
     (fn [g [pid best-gh clojars]]
       (update-in
        g [pid] assoc
        :description (or (get best-gh :description) (get clojars :description))
        :github-url (get best-gh :url)
        :clojars-url (get clojars :url)))
     g
     (for [[pid {:keys [versions]}] g
           :when (seq versions)]
       (let [githubs (for [[ver {:keys [github]}] versions
                           gh github]
                       gh)
             best-gh (if (seq githubs)
                       (apply max-key :watchers githubs)
                       {})
             [_ best-version] (apply max-key (comp count :dependents val) versions)
             clojars (first (get best-version :clojars))]
         [pid best-gh clojars]))))

  (def project-graph
    (-> {} step1 step2 step3 step4))
  
  (spit (str (System/getProperty "user.dir") "/resources/project_graph.clj")
        (prn-str project-graph))
  ;; then gzip project_graph.clj
  
  )

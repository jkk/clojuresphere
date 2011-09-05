(ns clojuresphere.fetcher
  (:use [clj-github.repos :only [search-repos]]
        [clojure.data.zip.xml :only [xml-> xml1-> text]]
        [clojuresphere.util :only [url-encode]])
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.java.io :as io]))

(defn parse-project-data [[_ proj-name version & opts]]
  (let [proj-name (str proj-name)
        name-parts (.split proj-name "/")
        [group-id artifact-id] (if (= 2 (count name-parts))
                                 name-parts
                                 [proj-name proj-name])]
    (assoc (apply hash-map opts)
      :name proj-name
      :group-id group-id
      :artifact-id artifact-id
      :version version)))

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
       :source :github
       :github {:url (repo :url)
                :owner (repo :owner)
                :name (repo :name)
                :forks (repo :forks)
                :watchers (repo :watchers)
                :size (repo :size)
                :created (repo :created_at)
                :pushed (repo :pushed_at)
                :open-issues (repo :open_issues)}))))

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
                                  {:dep [(symbol name) (xml1-> zdep :version text)]
                                   :scope (xml1-> zdep :scope text)})))]
    {:group-id group-id
     :artifact-id artifact-id
     :name name
     :version version
     :description (xml1-> z :description text)
     :dependencies (vec (map :dep (deps nil)))
     :dev-dependencies (vec (map :dep (deps "test")))
     :source :clojars
     :clojars {:dep [(symbol name) version]
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
  
  ;; TODO: merge projects
  ;; TODO: create project dependency graph
  ;; TODO: consider non-group-id name to have implicit group-id same as artifact-id

  )

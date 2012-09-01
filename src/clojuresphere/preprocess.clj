(ns clojuresphere.preprocess
  (:use [tentacles.repos :only [specific-repo]]
        [clojure.pprint :only [pprint]]
        [clojure.data.zip.xml :only [xml-> xml1-> text]]
        [clojuresphere.util :only [url-encode qualify-name maven-coord lein-coord
                                   safe-read-string fetch-doc select-els]])
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
                                  {:coord (lein-coord name (xml1-> zdep :version text))
                                   :scope (xml1-> zdep :scope text)})))]
    {:group-id group-id
     :artifact-id artifact-id
     :name name
     :version version
     :description (xml1-> z :description text)
     :url (xml1-> z :url text)
     :dependencies (vec (map :coord (deps nil)))
     :dev-dependencies (vec (map :coord (deps "test")))}))

;; github

(def github-auth {})

(defn normalize-github-url [url]
  (when url
    (-> url
        (.replaceAll "^http:" "https:")
        (.replaceAll "/$" ""))))

(defn fetch-repo [owner repo]
  (Thread/sleep 1000) ;crude rate limit
  (println "Fetching repo" owner repo)
  (flush)
  (specific-repo owner repo))

(defn crawl-repos []
  (let [url "https://github.com/languages/Clojure/most_watched"]
    (println "Crawling repos...")
    (loop [page 1 repos []]
      (print page " ") (flush)
      (Thread/sleep 1000) ;crude rate limit
      (let [doc (fetch-doc url :data {:page page})
            new-repos (for [el (select-els doc "#directory td.title a")]
                        (let [[_ owner repo-name] (.split (-> el :attrs :href) "/")]
                          [owner repo-name]))]
        (if (seq new-repos)
          (recur (inc page) (into repos new-repos))
          repos)))))

(defn crawl-and-fetch-repos []
  (let [repos (crawl-repos)]
    (println "\nFound" (count repos) "repos, fetching info for each...")
    (vec
     (remove
      string? ;clj-github quirk
      (for [[owner repo-name] repos]
        (fetch-repo owner repo-name))))))

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
       :github {:url (:url repo)
                :owner (:owner repo)
                :name (:name repo)
                :description (:description repo)
                :homepage (:homepage repo)
                :forks (:forks repo)
                :watchers (:watchers repo)
                :size (:size repo)
                :created (:created_at repo)
                :pushed (:pushed_at repo)
                :open-issues (:open_issues repo)}))))

(defn fetch-github-projects []
  ;; Github V3 API no longer supports searching by language
  #_(let [;; special exception for clojure itself (written in java)
        clojure-repo (first (search-repos github-auth "clojure"))
        repos (cons clojure-repo (crawl-and-fetch-repos))]
    (remove (comp #{"clojure-slick-rogue"} :name :github) ;broken
            (fetch-all-repo-projects repos))))

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
                               :homepage (:url project)
                               :url (str "http://clojars.org/" (:name project))}))
                 (catch Exception _ nil)))))))

;; project graph

(defn build-versions [g projects]
  (reduce
   (fn [m [aid coord k info]]
     (update-in m [aid :versions coord k] (fnil conj #{}) info))
   g
   (for [p projects
         :let [coord (apply lein-coord (map p [:group-id :artifact-id :version]))]
         k [:github :clojars]
         :let [info (k p)]
         :when info]
     [(-> p :artifact-id keyword) coord k info])))

(defn build-deps [g projects]
  (reduce
   (fn [g [gid aid ver dep-coord]]
     (let [gid (or gid aid)
           p-coord (lein-coord gid aid ver)
           [dep-gid dep-aid dep-ver] (maven-coord dep-coord)
           dep-coord (lein-coord dep-gid dep-aid dep-ver)
           aid (keyword aid)
           dep-aid (keyword dep-aid)]
       (-> g
           (update-in [aid :dependencies] (fnil conj #{}) dep-aid)
           (update-in [aid :versions p-coord :dependencies]
                      (fnil conj #{}) dep-coord)
           (update-in [dep-aid :dependents] (fnil conj #{}) aid)
           (update-in [dep-aid :versions dep-coord :dependents]
                      (fnil conj #{}) p-coord))))
   g
   (for [p projects
         coord (concat (:dependencies p) (:dev-dependencies p))]
     (concat (map p [:group-id :artifact-id :version])
             [coord]))))

(defn build-aggregate-info [g]
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

(defn build-best-info [g]
  (reduce
   (fn [g [pid best-gh clojars]]
     (update-in
      g [pid] assoc
      :description (or (:description best-gh) (:description clojars))
      :github-url (:url best-gh)
      :clojars-url (:url clojars)
      :homepage (if (seq (:homepage best-gh))
                  (:homepage best-gh)
                  (:homepage clojars))
      :updated (let [pushed (:pushed best-gh)] ;TODO: clojars?
                 (if (seq pushed)
                   (/ (.getTime (java.util.Date. pushed)) 1000)
                   0))
      :created (let [created (:created best-gh)] ;TODO: clojars?
                 (if (seq created)
                   (/ (.getTime (java.util.Date. created)) 1000)
                   0))))
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

(defn build-project-graph [projects]
  (-> {}
      (build-versions projects)
      (build-deps projects)
      (build-aggregate-info)
      (build-best-info)))

;; look up github projects we haven't fetched yet based on the clojars
;; homepage url, e.g. for swank-clojure, which is a fork and doesn't
;; show up in the repo search
(defn fetch-extra-github-projects [clojars-projects github-projects]
  (let [clojars-github-urls
        (->> clojars-projects
             (map (comp normalize-github-url :homepage :clojars))
             (remove nil?)
             (filter #(re-find #"^https://github.com/" %))
             (distinct)
             (remove (set (map (comp :url :github) github-projects))))
        owner-repos (map #(re-matches #"^https://github.com/([^/]+)/([^/]+)" %)
                         clojars-github-urls)
        extra-github-repos (doall
                            (remove
                             #(or (string? %) (nil? %)) ;quirk of clj-github
                             (for [[_ owner repo] owner-repos
                                   :when (and owner repo)]
                               (try (fetch-repo owner repo)
                                    (catch Exception _ nil)))))]
    (fetch-all-repo-projects extra-github-repos)))

(defn fetch-all-projects [clojars-dir]
  (let [clojars-projects (read-all-pom-projects clojars-dir)
        github-projects (fetch-github-projects)
        github-extra-projects (fetch-extra-github-projects
                               clojars-projects
                               github-projects)]
    (concat github-projects
            github-extra-projects
            clojars-projects)))

(comment
  
  ;; Manual fetching process. This takes a long time (~20 mins)
  ;; TODO: clean up and automate this

  ;; Run this first:
  ;; rsync -av --exclude '*.jar' clojars.org::clojars clojars

  (def clojars-dir "/Users/tin/src/clj/clojuresphere/clojars/")

  ;; all at once:
  (def project-graph (build-project-graph
                      (fetch-all-projects clojars-dir)))

  ;; or, step by step:
  #_(def clojars-projects (read-all-pom-projects clojars-dir))
  #_(def github-projects (fetch-github-projects))
  #_(def github-extra-projects (fetch-extra-github-projects
                              clojars-projects
                              github-projects))
  #_(def project-graph (build-project-graph
                      (concat github-projects
                              github-extra-projects
                              clojars-projects)))

  ;; TODO: make sure it's readable before writing
  
  (spit (str (System/getProperty "user.dir")
             "/resources/project_graph.clj")
        (with-out-str (pprint project-graph)))

  )

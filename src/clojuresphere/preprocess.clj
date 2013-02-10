(ns clojuresphere.preprocess
  (:use [tentacles.repos :only [specific-repo]]
        [tentacles.search :only [search-repos]]
        [clojure.pprint :only [pprint]]
        [clojure.data.zip.xml :only [xml-> xml1-> text]]
        [clojuresphere.util :only [safe-read-string]]
        [clojuresphere.project-model :only [qualify-name lein-coord
                                            sort-versions latest-stable-coord?
                                            latest-coord? count-dependents]])
  (:require [clojuresphere.project-model :as pm]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.java.io :as io]
            [sundry.io :as sio]
            [aws.sdk.s3 :as s3]))

(def config (try
              (sio/read (io/resource "config.clj"))
              (catch Exception _ {})))

(def aws-cred {:access-key (:aws-access-key config)
               :secret-key (:aws-secret-key config)})

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
;; Maybe at least add a hack for parent poms so we can include contrib
;; FIXME: some dependencies use $ variables and that breaks things
;; (e.g., clojure-slick-rogue dependencies)
(defn parse-pom-xml [xml]
  (let [z (zip/xml-zip xml)
        group-id (xml1-> z :groupId text)
        group-id (or group-id (xml1-> z :parent :groupId text)) ;hack
        artifact-id (xml1-> z :artifactId text)
        name (if group-id (str group-id "/" artifact-id) artifact-id)
        version (xml1-> z :version text)
        deps (group-by :scope (for [zdep (xml-> z :dependencies :dependency)
                                    :let [gid (xml1-> zdep :groupId text)
                                          aid (xml1-> zdep :artifactId text)
                                          name (if gid (str gid "/" aid) aid)
                                          ver (xml1-> zdep :version text)]
                                    ;; exclude deps with POM variables for now
                                    :when (and name ver
                                               (not (re-find #"\$\{"
                                                             (str name ver))))]
                                {:coord (lein-coord name ver)
                                 :scope (xml1-> zdep :scope text)}))]
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
  (specific-repo owner repo {:client_id (:github-client-id config)
                             :client_secret (:github-client-secret config)}))

(defn fetch-repos [start-page]
  (Thread/sleep 1000) ;crude rate limit
  (println "Fetching page" start-page) ;FIXME: proper logging
  (let [resp (search-repos
               "clojure" {:language "clojure"
                          :start-page start-page
                          :client_id (:github-client-id config)
                          :client_secret (:github-client-secret config)})]
    (if-not (sequential? resp)
      (throw (ex-info "Bad response" {:response resp}))
      resp)))

(defn fetch-all-repos []
  (->> (iterate inc 1)
       (map fetch-repos)
       (take-while seq)
       (apply concat)
       vec))

;; TODO: some repos have multiple project.clj files (e.g., ring)
(defn fetch-repo-project [repo]
  (let [owner (if (map? (:owner repo))
                (:login (:owner repo))
                (:owner repo))
        base-url (str "https://raw.github.com/"
                      owner "/"
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
  (println "Fetching GitHub repos and projects...")
  (let [;; special exception for clojure itself (written in java)
        clojure-repo (first (search-repos "clojure"))
        repos (cons clojure-repo (fetch-all-repos))]
    (doall
      (remove (comp #{"clojure-slick-rogue"} :name :github) ;broken
              (fetch-all-repo-projects repos)))))

;; clojars

(defn read-all-pom-projects [clojars-dir]
  (println "Parsing Clojars POM files...")
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

;; look up github projects we haven't fetched yet based on the clojars
;; homepage url, e.g. for swank-clojure, which is a fork and doesn't
;; show up in the repo search
(defn fetch-extra-github-projects [clojars-projects github-projects]
  (println "Fetching GitHub projects discovered via Clojars...")
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

(defn fetch-clojars-stats []
  (sio/read "https://clojars.org/stats/all.edn"))

;; project graph

(defn stable? [ver]
  (boolean (re-matches #"\d+(?:\.\d+)+" ver)))

(defn get-latest [versions]
  (first (sort-versions versions)))

(defn get-latest-stable [versions]
  (->> versions sort-versions (filter stable?) first))

(defn project-coord [project]
  (apply lein-coord (map project [:group-id :artifact-id :version])))

(defn build-deps [projects]
  (reduce
    (fn [g [[name ver :as coord] [dname dver :as dep-coord]]]
      (-> g
        (update-in [name :versions ver :dependencies] (fnil conj #{}) dep-coord)
        (update-in [dname :versions dver :dependents] (fnil conj #{}) coord)))
    {}
    (for [p projects
          :let [deps (concat (:dependencies p)
                             (:dev-dependencies p)
                             (get-in p [:profiles :dev :dependencies]))]
          dep deps
          :when (and (vector? dep)
                     (symbol? (first dep))
                     (string? (second dep))
                     (pos? (count (second dep))))]
      (let [[dname dver] dep
            coord (project-coord p)
            dep-coord (lein-coord dname dver)]
        [coord dep-coord]))))

;; For clojars info, we look at the latest stable version, but for
;; github, we look for the most-watched (possibly unstable) version
(defn build-info [g projects]
  (let [pm (group-by project-coord projects)]
    (reduce-kv
     (fn [g name props]
       (let [vers (keys (:versions props))
             stable-ver (get-latest-stable vers)
             clojars-p (first (filter :clojars (get pm [name stable-ver])))
             github-ps (filter :github (mapcat #(get pm [name %])
                                               vers))
             github-p (when (seq github-ps)
                        (apply max-key
                               (comp :watchers :github)
                               github-ps))
             latest-ver (get-latest vers)
             new-props {:latest latest-ver}
             new-props (if stable-ver
                         (assoc new-props :stable stable-ver)
                         new-props)
             new-props (if-let [clojars (:clojars clojars-p)]
                         (assoc new-props :clojars clojars)
                         new-props)
             new-props (if-let [github (:github github-p)]
                         (assoc new-props
                           :github github
                           :watchers (:watchers github)
                           :updated (/ (.getTime (clojure.instant/read-instant-date
                                                  (:pushed github)))
                                       1000)
                           :created (/ (.getTime (clojure.instant/read-instant-date
                                                  (:created github)))
                                       1000))
                         new-props)]
         (assoc g
           name (merge props new-props))))
     g g)))

(defn build-counts [g]
  (reduce-kv
   (fn [g name props]
     (let [latest-ver (:latest props)
           stable-ver (:stable props)
           dep-counts {:latest-stable (count-dependents
                                       props g latest-stable-coord?)
                       :latest (count-dependents
                                props g latest-coord?)}
           dep-counts (assoc dep-counts
                        :all (+ (:latest-stable dep-counts)
                                (:latest dep-counts)))]
       (assoc-in g [name :dependent-counts] dep-counts)))
   g g))

(defn build-stats [g stats]
  (reduce
    (fn [g [pid downloads]]
      (if (get g pid)
        (update-in g [pid] assoc :downloads downloads)
        g))
    g
    (for [[[gid aid] vs] stats]
      [(symbol gid aid) (reduce + (vals vs))])))

(defn build-project-graph [projects & [stats]]
  (-> (build-deps projects)
      (build-info projects)
      (build-counts)
      (build-stats stats)))

(defn upload-project-graph [aws-cred g]
  (s3/copy-object aws-cred "clojuresphere.com"
                  "project_graph.clj.gz" "project_graph_previous.clj.gz")
  (let [tmp (java.io.File/createTempFile "project_graph" ".clj.gz")]
    (with-open [w (io/writer
                    (java.util.zip.GZIPOutputStream.
                      (io/output-stream tmp)))]
      (binding [*out* w]
        (prn g)))
    (s3/put-object
      aws-cred "clojuresphere.com" "project_graph.clj.gz" tmp
      {} (s3/grant :all-users :read))
    (.delete tmp)))

;; See scripts/refresh.sh
(defn -main [& args]
  (let [clojars-dir (first args)
        projects (fetch-all-projects clojars-dir)
        stats (fetch-clojars-stats)
        g (build-project-graph projects)]
    ;; sanity check - ensure nothing got smaller
    (if (or (< (count g) (count pm/graph))
            (< (count (filter :github (vals g)))
               (count (filter :github (vals pm/graph))))
            (< (count (filter :clojars (vals g)))
               (count (filter :clojars (vals pm/graph)))))
      (println "Failed sanity check!")
      (do
        (println "Uploading project graph...")
        (upload-project-graph aws-cred g)
        (println "Done")))))


(comment
  
  ;; Manual fetching process. This takes a long time (like 2 hours)

  ;; Run this first:
  ;; rsync -av --exclude '*.jar' clojars.org::clojars clojars

  (def clojars-dir "/Users/tin/src/clj/clojuresphere/clojars/")

  (def stats (fetch-clojars-stats))
  
  ;; all at once:
  (def project-graph (build-project-graph
                      (fetch-all-projects clojars-dir)
                      stats))

  ;; or, step by step:
  #_(def clojars-projects (read-all-pom-projects clojars-dir))
  #_(def github-projects (fetch-github-projects))
  #_(def github-extra-projects (fetch-extra-github-projects
                              clojars-projects
                              github-projects))
  #_(def project-graph (build-project-graph
                      (concat github-projects
                              github-extra-projects
                              clojars-projects)
                      stats))

  ;; TODO: make sure it's readable before writing
  
  (spit (str (System/getProperty "user.dir")
             "/resources/project_graph.clj")
        (with-out-str (pprint project-graph)))

  )

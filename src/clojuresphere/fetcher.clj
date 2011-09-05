(ns clojuresphere.fetcher
  (:use [clj-github.repos :only [search-repos]]))

(defn parse-project-data
  "Turns data as returned from reading a project.clj file into a meaningful map"
  [data]
  (let [[_ proj-name version & opts] data
        proj-name (str proj-name)
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

;; TODO: track "scope" as dev-dependency




(comment

  ;; manual fetching process
  
  (def repos (fetch-all-repos))
  (def github-projects (fetch-all-repo-projects repos))

  ;; TODO: merge projects
  ;; TODO: create project dependency graph

  )

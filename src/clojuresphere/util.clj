(ns clojuresphere.util
  (:use [clojure.walk :only [keywordize-keys]])
  (:require [clojure.java.io :as io]
            [clj-json.core :as json])
  (:import [org.jsoup Jsoup]))

(def ^:dynamic *req* nil)

(defn wrap-request [handler]
  (fn [req]
    (binding [*req* req]
      (handler req))))

(defn wrap-ajax-detect [handler]
  (fn [req]
    (handler (assoc req
               :ajax? (= "XMLHttpRequest"
                         (get-in req [:headers "x-requested-with"]))))))

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

(defn memory-stats [& {:keys [gc]}]
  "Return stats about memory availability and usage, in MB. Calls
  System/gc before gathering stats when the :gc option is true."
  (when gc
    (System/gc))
  (let [r (Runtime/getRuntime)
        mb #(int (/ % 1024 1024))]
    {:max (mb (.maxMemory r))
     :total (mb (.totalMemory r))
     :used (mb (- (.totalMemory r) (.freeMemory r)))
     :free (mb (.freeMemory r))}))

(defn url-encode [s]
  (java.net.URLEncoder/encode s "UTF-8"))

(defn read-resource [filename]
  (with-open [in (-> filename
                     io/resource
                     io/file
                     io/reader
                     java.io.PushbackReader.)]
    (read in)))

(defn read-gz-resource [filename]
  (with-open [in (-> filename
                     io/resource
                     io/file
                     java.io.FileInputStream.
                     java.util.zip.GZIPInputStream.
                     io/reader
                     java.io.PushbackReader.)]
    (read in)))

(defn json-resp
  [data]
  {:status 200
   :headers {"Content-type" "application/json"}
   :body (json/generate-string data)})

(defn json-parse [str]
  (try
    (json/parse-string str)
    (catch Exception e
      nil)))

(defn parse-int [x & [default]]
  (try (Integer/valueOf x) (catch Exception _ default)))

(defn safe-read [s]
  (binding [*read-eval* false]
    (read s)))

(defn safe-read-string [s]
  (binding [*read-eval* false]
    (read-string s)))

(defn date->seconds [date]
  (cond
   (instance? java.util.Date date)
   (long (/ (.getTime date) 1000))

   (string? date)
   (try
     (long (/ (.getTime (java.util.Date. date)) 1000))
     (catch Exception _ nil))
   
   (and (number? date) (> date (.getTime (java.util.Date. "1/1/3000"))))
   (long (/ date 1000))

   (number? date)
   date))

(defn date->days-ago [date]
  (let [now-days (long (/ (.getTime (java.util.Date.)) 1000 60 60 24))
        date-days (long (/ (date->seconds date) 60 60 24))]
    (- now-days date-days)))

(defn stringify-map [m]
  (reduce
   (fn [m [k v]]
     (assoc m (if (keyword? k) (name k) (str k)) (str v)))
   {} m))

(defn fetch-doc [url & {:keys [data cookies timeout method]
                        :or {timeout 10000 data {} cookies {} method :get}}]
  (let [c (-> (Jsoup/connect url)
              (.userAgent "ClojureSphere")
              (.data (stringify-map data))
              (.timeout 10000))]
    (doseq [[k v] (stringify-map cookies)]
      (.cookie c k v))
    (if (= :post method)
      (.post c)
      (.get c))))

(defn- jsoup->clj [el]
  (let [tag (.tagName el)
        attrs (keywordize-keys (into {} (.attributes el)))
        text (.ownText el)
        children (map jsoup->clj (.children el))
        children (if (seq text)
                  (cons text children)
                  children)]
    {:tag tag :attrs attrs :children (vec children)}))

(defn select-els [doc sel]
  (map jsoup->clj (.select doc sel)))

(ns clojuresphere.util
  (:use [clojure.walk :only [keywordize-keys]])
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]))

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

(defn json-resp [data & {:as opts}]
  {:status 200
   :headers {"Content-type" "application/json"}
   :body (json/generate-string data opts)})

(defn clojure-resp [data & {:as opts}]
  {:status 200
   :headers {"Content-type" "application/clojure"}
   :body (prn-str data)})

;;

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
(ns clojuresphere.util
  (:require [clojure.java.io :as io]
            [clj-json.core :as json]))

(defn qualify-name [name]
  (let [name (str name)
        name-parts (.split name "/")]
    (if (= 2 (count name-parts))
      name-parts
      [name name])))

;; I'm not sure what to call a [group artifact version] vector,
;; so I call it a "dep".
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

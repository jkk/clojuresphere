(ns clojuresphere.util
  (:require [clojure.java.io :as io]
            [clj-json.core :as json]))

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
     (let [group-id (or group-id artifact-id)]
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

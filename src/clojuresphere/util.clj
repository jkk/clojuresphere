(ns clojuresphere.util)

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
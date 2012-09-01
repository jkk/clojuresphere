(defproject clojuresphere "0.0.1-SNAPSHOT"
  :description "Browsable dependency graph of Clojure projects"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/data.zip "0.1.1"]
                 [tentacles "0.2.2"]
                 [compojure "1.1.1"]
                 [hiccup "1.0.1"]
                 [ring/ring-core "1.1.3"]
                 [ring/ring-jetty-adapter "1.1.3"]
                 [clj-http "0.5.3"]
                 [org.jsoup/jsoup "1.6.1"]]
  :min-lein-version "2.0.0")

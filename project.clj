(defproject clojuresphere "0.0.1-SNAPSHOT"
  :description "Browsable dependency graph of Clojure projects"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/data.zip "0.1.0"]
                 [clj-github "1.0.1"]
                 [compojure "0.6.4"]
                 [hiccup "0.3.6"]
                 [clj-json "0.3.2"]
                 [ring/ring-core "0.3.11"]
                 [ring/ring-jetty-adapter "0.3.11"]
                 [org.jsoup/jsoup "1.6.1"]]
  :min-lein-version "2.0.0")

(defproject clojuresphere "0.0.1-SNAPSHOT"
  :description "Browsable dependency graph of Clojure projects"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/data.zip "0.1.1"]
                 [tentacles "0.2.6"]
                 [compojure "1.1.5"]
                 [hiccup "1.0.4"]
                 [ring/ring-core "1.2.0"]
                 [ring/ring-jetty-adapter "1.2.0"]
                 [clj-http "0.7.6"]
                 [org.apache.maven/maven-artifact "3.1.0"]
                 [cheshire "5.2.0"]
                 [amalloy/ring-gzip-middleware "0.1.2"]
                 [clj-aws-s3 "0.3.6"]
                 [sundry "0.4.0"]]
  :min-lein-version "2.0.0")

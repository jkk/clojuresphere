(ns clojuresphere.test.core
  (:use [clojuresphere.core])
  (:use [clojure.test]))

(deftest home-route
  (testing "the '/' route"
    (let [resp (routes {:uri "/" :request-method :get})
          content #"ClojureSphere.*Browse the open-source Clojure ecosystem"]
      (is (= 200 (:status resp)))
      (is (re-find content (:body resp))))))

(deftest stats-route
  (testing "the '/_stats' route"
    (let [resp (routes {:uri "/_stats" :request-method :get})
          stats #"\{:projects \d+, :memory \{:max \d+, :total \d+, :used \d+, :free \d+\}\}"]
      (is (= 200 (:status resp)))
      (is (re-find stats (:body resp))))))

(deftest not-found
  (testing "broken route"
    (let [resp (routes {:uri "/ninja" :request-method :get})
          redirected-page (routes {:uri "/ninja/ninja" :request-method :get})
          message #"Page Not Found"]
      (is (= 302 (:status resp)))
      (is (= "/ninja/ninja" (get-in resp [:headers "Location"])))
      (is (re-find message (:body redirected-page))))))

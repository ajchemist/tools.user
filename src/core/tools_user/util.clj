(ns tools-user.util
  (:require
   [clojure.java.io :as jio]
   [clojure.string :as str]
   ))


(defn tilde-expansion
  [path]
  (if (str/starts-with? path "~/")
    (.getPath (jio/file (System/getProperty "user.home") (subs path 2)))
    path))


(defn absolute-path?
  [path]
  (str/starts-with? path "/"))


(defn strip-left-slash
  [path]
  (if (absolute-path? path)
    (subs path 1)
    path))

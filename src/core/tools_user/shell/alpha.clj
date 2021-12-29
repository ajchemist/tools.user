(ns tools-user.shell.alpha
  (:require
   [clojure.string :as str]
   [clojure.java.shell :as jsh]
   [clojure.java.io :as jio]
   [user.java.io.alpha :as u.jio]
   )
  (:import
   java.io.File
   ))


(defn exit!
  [{:keys [exit out err] :as sh-return}]
  (println (str err out))
  (when-not (zero? exit)
    (throw (ex-info "Non-zero exit." sh-return))))


(defn find-executable
  "Return executable location path if exists."
  [command-name]
  (some
    (fn [dir]
      (let [f (jio/file dir command-name)]
        (and (u.jio/file? f)
             (u.jio/executable? f)
             (.getPath f))))
    (str/split (System/getenv "PATH") (re-pattern File/pathSeparator))))

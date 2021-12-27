(ns tools-user.shell.pass.alpha
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [user.java.io.alpha :as u.jio]
   [clojure.java.shell :as jsh]
   [tools-user.shell.alpha :as shell]
   ))


(defn show
  "Return non-nil only if the secret exists in `pass-name`"
  [pass-name]
  (let [{:keys [exit out] :as sh-return} (jsh/sh "pass" "show" pass-name)]
    (if (zero? exit)
      (if (str/index-of out "└── ")
        nil
        (nth (str/split (:out sh-return) #"\n" 2) 0))
      nil)))


(defn generate
  [pass-name]
  (shell/exit! (jsh/sh "pass" "generate" pass-name)))


(defn fscopy
  [from to]
  (let [from (if (u.jio/exists? from)
               (.getPath (jio/as-file from))
               from)
        to   (if (u.jio/exists? from)
               to
               (.getPath (jio/as-file to)))]
    (println "gopass" "fscopy" from to)
    (shell/exit! (jsh/sh "gopass" "fscopy" from to))))

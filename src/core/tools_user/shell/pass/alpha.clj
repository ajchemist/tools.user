(ns tools-user.shell.pass.alpha
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [clojure.java.shell :as jsh]
   [tools-user.shell.alpha :as shell]
   ))


(defn show
  [pass-name]
  (let [{:keys [exit] :as sh-return} (jsh/sh "pass" "show" pass-name)]
    (if (zero? exit)
      (nth (str/split (:out sh-return) #"\n" 2) 0)
      nil)))


(defn generate
  [pass-name]
  (shell/exit! (jsh/sh "pass" "generate" pass-name)))


(defn fscopy
  [from pass-name]
  (let [from (.getPath (jio/as-file from))]
    (println "gopass" "fscopy" from pass-name)
    (shell/exit! (jsh/sh "gopass" "fscopy" from pass-name))))

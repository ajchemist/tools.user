(ns tools-user.shell.pass.alpha
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [clojure.java.shell :as jsh]
   [tools-user.shell.alpha :as shell]
   ))


(defn show
  "Return non-nil only if the secret exists in `pass-name`"
  [pass-name]
  (let [{:keys [exit out err] :as sh-return} (jsh/sh "pass" "show" pass-name)]
    (tap> [:info err])
    (if (zero? exit)
      (if (str/index-of out (str pass-name "\n├── ")) ; dir entry check
        nil
        (nth (str/split (:out sh-return) #"\n" 2) 0))
      nil)))


(defn generate
  [pass-name]
  (tap> [:info "pass" "generate" pass-name])
  (shell/exit! (jsh/sh "pass" "generate" pass-name)))


(defn fscopy-from-file
  [from to]
  (let [from (.getPath (jio/as-file from))
        to   (str to)]
    (tap> [:info "gopass" "fscopy" from to])
    (shell/exit! (jsh/sh "gopass" "fscopy" from to))))


(defn fscopy-from-vault
  [from to]
  (let [from (str from)
        to   (.getPath (jio/as-file to))]
    (tap> [:info "gopass" "fscopy" from to])
    (shell/exit! (jsh/sh "gopass" "fscopy" from to))))


(defn git-pull
  ([]
   (shell/exit! (jsh/sh "pass" "git" "pull" "origin" "master"))))

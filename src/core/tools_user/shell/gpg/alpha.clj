(ns tools-user.shell.gpg.alpha
  (:refer-clojure :exclude [import])
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [clojure.java.shell :as jsh]
   [tools-user.shell.alpha :as shell]
   )
  (:import
   java.io.File
   ))


;; https://gist.github.com/vrillusions/5484422
;; https://www.gnupg.org/documentation/manuals/gnupg-devel/Unattended-GPG-key-generation.html


(defn render-gpg-target
  [real comment email]
  {:pre [(not (str/blank? real)) (not (str/blank? email))]}
  (str
    real
    (when comment
      (str " (" comment ")"))
    (str " <" email ">")))


(defn render-unattended-genkey-script
  [{:keys [type length sub-type sub-length real email comment expire passphrase no-protection dry-run]
    :or   {type       1
           length     4096
           sub-type   1
           sub-length 4096
           expire     "1y"}}]
  (let [tcoll-0 (transient [])
        tcoll   (transient {"Key-Type" type "Key-Length" length "Subkey-Type" sub-type "Subkey-Length" sub-length})]
    (when dry-run
      (-> tcoll-0 (conj! "%dry-run")))
    (when no-protection
      (-> tcoll-0 (conj! "%no-protection")))
    (when real
      (-> tcoll (assoc! "Name-Real" real)))
    (when email
      (-> tcoll (assoc! "Name-Email" email)))
    (when comment
      (-> tcoll (assoc! "Name-Comment" comment)))
    (when expire
      (-> tcoll (assoc! "Expire-Date" expire)))
    (when (and (not no-protection) passphrase)
      (-> tcoll (assoc! "Passphrase" passphrase)))
    (reduce-kv
      (fn [ret k v] (str ret k ": " v "\n"))
      (str/join
        "\n"
        (persistent! (conj! tcoll-0 "")))
      (persistent! tcoll))))


(defn genkey
  [opts]
  (let [tmp-file (File/createTempFile "gpg-genkey-unattended" "")
        _        (spit tmp-file (render-unattended-genkey-script opts))]
    (shell/exit! (jsh/sh "gpg" "--full-generate-key" "--batch" (str tmp-file)))))


(defn import
  [from]
  (shell/exit! (jsh/sh "gpg" "--import" (str from))))


(defn export
  ([target]
   (shell/exit! (jsh/sh "gpg" "--export" "--armor" target)))
  ([to target]
   (shell/exit! (jsh/sh "gpg" "--batch" "--yes" "--output" (str to) "--export" "--armor" target))))


(defn export-secret-key
  [to target]
  (shell/exit! (jsh/sh "gpg" "--batch" "--yes" "--output" (str to) "--export-secret-key" "--armor" target)))


(defn- first-line
  [s]
  (subs s 0 (str/index-of s "\n")))


(defn extract-key-id
  [s]
  (nth (re-matches #".*([0-9A-Z]{16}).*" (first-line s)) 1))

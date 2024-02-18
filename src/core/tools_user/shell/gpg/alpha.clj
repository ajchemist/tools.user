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
           expire     "1y"}
    :as   opts}]
  (let [coll-0  []
        coll    {}
        coll-0' (cond-> coll-0
                  dry-run       (conj "%dry-run")
                  no-protection (conj "%no-protection")
                  true          (conj ""))
        coll'   (cond-> coll
                  real                                 (assoc "Name-Real" real)
                  email                                (assoc "Name-Email" email)
                  comment                              (assoc "Name-Comment" comment)
                  expire                               (assoc "Expire-Date" expire)
                  (and (not no-protection) passphrase) (assoc "Passphrase" passphrase))]
    (tap> coll')
    (reduce-kv
      (fn [ret k v] (str ret k ": " v "\n"))
      (str
        (str/join "\n" coll-0')
        "Key-Type: " type "\n"
        "Key-Length: " length "\n"
        "Subkey-Type: " sub-type "\n"
        "Subkey-Length: " sub-length "\n")
      coll')))


(defn genkey
  [opts]
  (let [tmp-file       (File/createTempFile "gpg-genkey-unattended" "")
        script-content (render-unattended-genkey-script opts)
        _              (spit tmp-file script-content)]
    (tap> [:gpg/genkey {:script script-content}])
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

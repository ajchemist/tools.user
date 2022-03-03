(ns tools-user.shell.ssh.alpha
  (:require
   [clojure.java.io :as jio]
   [clojure.java.shell :as jsh]
   [tools-user.shell.alpha :as shell]
   ))


(defn keygen-args
  [{:keys [format type bits rounds passphrase nopassphrase comment file overwrite]}]
  (let [tcoll (transient [])]
    (when format
      (-> tcoll (conj! "-m") (conj! (name format))))
    (when type
      (-> tcoll (conj! "-t") (conj! (name type))))
    (when bits
      (-> tcoll (conj! "-b") (conj! (str bits))))
    (when rounds
      (-> tcoll (conj! "-a") (conj! (str rounds))))
    (when (and (not nopassphrase) passphrase)
      (-> tcoll (conj! "-N") (conj! (str passphrase))))
    (when nopassphrase
      (-> tcoll (conj! "-P") (conj! "") (conj! "-N") (conj! "")))
    (when comment
      (-> tcoll (conj! "-C") (conj! (str comment))))
    (when file
      (-> tcoll (conj! "-f") (conj! (.getPath (jio/as-file file)))))
    (when overwrite
      (-> tcoll (conj! :in) (conj! "y")))
    (persistent! tcoll)))


(defn ed25519-opts
  [opts]
  (assoc opts
    :rounds 100
    :type "ed25519"))


(defn rsa4096-opts
  [opts]
  (assoc opts
    :type "rsa"
    :bits 4096))


(defn keygen
  [{:keys [file] :as opts}]
  (jio/make-parents (jio/as-file file))
  (shell/exit!
     (apply jsh/sh "ssh-keygen" (keygen-args opts))))


(defn keygen-ed25519
  [opts]
  (keygen (ed25519-opts opts)))


(defn keygen-rsa4096
  [opts]
  (keygen (rsa4096-opts opts)))


(defn fingerprint
  [file]
  (shell/exit! (jsh/sh "ssh-keygen" "-l" "-f" (.getPath (jio/as-file file)))))

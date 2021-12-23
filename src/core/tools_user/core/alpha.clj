(ns tools-user.core.alpha
  (:require
   [clojure.java.io :as jio]
   [tools-user.shell.pass.alpha :as pass]
   [tools-user.shell.ssh.alpha :as ssh]
   ))


(defn ssh-keypairs-pass-name
  [group name]
  (str "ssh/keypairs/" group "/" name))


(defn ssh-keygen-opts
  [{:keys [group name file keypairs-dir]
    :or   {keypairs-dir (.getPath (jio/file (System/getProperty "user.home") ".ssh" "keypairs"))}
    :as   opts}]
  (let [private-key-file     (or file (jio/file keypairs-dir (str group) (str name)))
        _public-key-file     (jio/file (str (.getPath (jio/as-file private-key-file)) ".pub"))
        pass-name            (ssh-keypairs-pass-name group name)
        passphrase-pass-name (str pass-name "/passphrase")
        passphrase           (pass/show passphrase-pass-name)
        passphrase           (if passphrase
                               passphrase
                               (do
                                 (pass/generate passphrase-pass-name)
                                 (pass/show passphrase-pass-name)))]
    (-> opts
      (dissoc :group :name :keypairs-dir)
      (assoc :file private-key-file)
      (update :comment #(or % (str group "/" name)))
      (update :passphrase #(or % passphrase)))))


(defn ssh-keygen
  "Dependencies:
  - ssh-keygen
  - pass
  - gopass
  🔐"
  [{:keys [group name] :as opts}]
  (let [opts (ssh-keygen-opts opts)]
    (ssh/keygen opts)
    (pass/fscopy (:file opts) (ssh-keypairs-pass-name group name))))


(defn ssh-keygen-ed25519
  [opts]
  (ssh-keygen (ssh/ed25519-opts opts)))


(defn ssh-keygen-rsa4096
  [opts]
  (ssh-keygen (ssh/rsa4096-opts opts)))

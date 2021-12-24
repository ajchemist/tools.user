(ns tools-user.core.alpha
  (:require
   [clojure.java.io :as jio]
   [tools-user.shell.pass.alpha :as pass]
   [tools-user.shell.ssh.alpha :as ssh]
   ))


(defn ssh-keypairs-pass-name
  [id]
  (str "ssh/keypairs/" id))


(defn ssh-keygen-opts
  ""
  [{:keys [id file keypairs-dir]
    :or   {keypairs-dir (.getPath (jio/file (System/getProperty "user.home") ".ssh" "keypairs"))}
    :as   opts}]
  (let [group                (namespace id)
        name                 (name id)
        private-key-file     (or file (jio/file keypairs-dir group name))
        _public-key-file     (jio/file (str (.getPath (jio/as-file private-key-file)) ".pub"))
        pass-name            (ssh-keypairs-pass-name id)
        passphrase-pass-name (str pass-name "/passphrase")
        passphrase           (pass/show passphrase-pass-name)
        passphrase           (if passphrase
                               passphrase
                               (do
                                 (println "pass" "generate" passphrase-pass-name)
                                 (pass/generate passphrase-pass-name)
                                 (pass/show passphrase-pass-name)))]
    (-> opts
      (dissoc :id :keypairs-dir)
      (assoc :file private-key-file)
      (update :comment #(or % (str id)))
      (update :passphrase #(or % passphrase)))))


(defn ssh-keygen
  "Dependencies:
  - ssh-keygen
  - pass
  - gopass
  🔐"
  [{:keys [id] :as opts}]
  {:pre [(qualified-ident? id)]}
  (let [opts (ssh-keygen-opts opts)]
    (ssh/keygen opts)
    (pass/fscopy (:file opts) (ssh-keypairs-pass-name id))))


(defn ssh-keygen-ed25519
  [opts]
  (ssh-keygen (ssh/ed25519-opts opts)))


(defn ssh-keygen-rsa4096
  [opts]
  (ssh-keygen (ssh/rsa4096-opts opts)))

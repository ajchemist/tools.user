(ns tools-user.core.alpha
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [tools-user.shell.pass.alpha :as pass]
   [tools-user.shell.ssh.alpha :as ssh]
   [tools-user.ssh.config.alpha :as ssh.config]
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


(defn generate-ssh-config-file
  [{:keys [hosts-edn-file
           target]
    :or   {hosts-edn-file (jio/file (System/getProperty "user.home") ".ssh" "hosts.edn")
           target         (jio/file (System/getProperty "user.home") ".ssh" "config")}}]
  (ssh.config/generate-config-file
    hosts-edn-file
    target))


(defn fetch-ssh-key-from-pass
  [{:keys [hosts-edn-file
           keypairs-dir]
    :or   {hosts-edn-file (jio/file (System/getProperty "user.home") ".ssh" "hosts.edn")
           keypairs-dir   (.getPath (jio/file (System/getProperty "user.home") ".ssh" "keypairs"))}}]
  (run!
    (fn [[id _]]
      (pass/fscopy
        (ssh-keypairs-pass-name id)
        (jio/file keypairs-dir (namespace id) (name id))))
    (sort-by
      (fn [[id]] id)
      (:ssh.config/hosts (ssh.config/read-hosts-edn-file hosts-edn-file)))))


(defn ssh-keygen-all
  ":ssh.config/hosts $id -> passwordstore existency test

  private-key exists in passwordstore -> do nothing
  not exist in passwordstore -> ssh-keygen -> fscopy key-file ssh/keypairs/$id"
  [{:keys [hosts-edn-file]
    :or   {hosts-edn-file (jio/file (System/getProperty "user.home") ".ssh" "hosts.edn")}}]
  (run!
    (fn [[id {:keys [:ssh.key/type
                    :ssh.key/bits
                    :ssh.key/rounds
                    :ssh.key/format]
             :or   {type   "ed25519"
                    format "PEM"}}]]
      (let [pass-name (ssh-keypairs-pass-name id)]
         (if (nil? (pass/show pass-name))
           (let [opts {:id        id
                       :format    format
                       :type      type
                       :bits      bits
                       :rounds    rounds
                       :overwrite true}
                 opts (case (name type)
                        "ed25519" (ssh/ed25519-opts opts)
                        "rsa"     (ssh/rsa4096-opts opts)
                        opts)]
            (ssh-keygen opts))
          (println "[skip] pass exists:" pass-name))))
    (sort-by
      (fn [[id]] id)
      (:ssh.config/hosts (ssh.config/read-hosts-edn-file hosts-edn-file)))))


(defn setup-ssh
  [{:as opts}]
  (fetch-ssh-key-from-pass opts)
  (ssh-keygen-all opts)
  (generate-ssh-config-file opts))


(comment
  )

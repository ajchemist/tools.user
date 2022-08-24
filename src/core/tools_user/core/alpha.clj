(ns tools-user.core.alpha
  (:require
   [clojure.stacktrace :as stacktrace]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [tools-user.util :as util]
   [tools-user.shell.alpha :as shell]
   [tools-user.shell.pass.alpha :as pass]
   [tools-user.shell.ssh.alpha :as ssh]
   [tools-user.ssh.config.alpha :as ssh.config]
   ))


(def ssh-default-keypairs-dir (jio/file (System/getProperty "user.home") ".ssh" "keypairs"))
(def ^:const +ssh-keypair-pass-name-prefix+ "ssh/keypairs/")


;;
;; - hosts.edn
;; - ssh-keygen command-line
;; - pass, gopass command-line


(defn- ^:private ssh-keypair-pass-name
  [pass-name]
  (if (str/starts-with? pass-name +ssh-keypair-pass-name-prefix+)
    pass-name
    (str +ssh-keypair-pass-name-prefix+ (util/strip-left-slash pass-name))))


(defn ^:private prep-ssh-keypair-options
  [[id {:as opts}]]
  {:pre [(qualified-ident? id)]}
  (-> opts
    (assoc :ssh.key/id id)
    (update :ssh.keygen/file
      (fn [file]
        (if (string? file)
          (if (util/absolute-path? file)
            file
            (.getPath (jio/file ssh-default-keypairs-dir file)))
          (.getPath (apply jio/file ssh-default-keypairs-dir (conj (str/split (namespace id) #"\.")  (name id)))))))
    (update :pass/pass-name
      (fn [pass-name]
        (cond
          (string? pass-name) (ssh-keypair-pass-name pass-name)
          :else               (ssh-keypair-pass-name (str (str/join "/" (str/split (namespace id) #"\.")) "/" (name id))))))))


(defn- ssh-keygen-1
  "Dependencies:
  - ssh-keygen
  - pass
  - gopass
  🔐"
  [{:keys
    [:ssh.key/id
     :ssh.keygen/type
     :ssh.keygen/format
     :ssh.keygen/file
     :ssh.keygen/nopassphrase
     :pass/pass-name]
    :as opts}]
  {:pre [(qualified-ident? id)]}
  (as-> {:type      (or type "ed25519")
         :format    (or format "PEM")
         :rounds    (:ssh.keygen/rounds opts)
         :bits      (:ssh.keygen/bits opts)
         :file      file
         :comment   (:ssh.keygen/comment opts (str id))
         :overwrite true}
    $
    (case (:type $)
      "ed25519" (ssh/ed25519-opts $)
      "rsa"     (ssh/rsa4096-opts $)
      $)
    (if nopassphrase
      (assoc $ :nopassphrase true)
      (let [passphrase-pass-name (str pass-name "/passphrase")
            passphrase           (pass/show passphrase-pass-name)
            passphrase           (if passphrase
                                   passphrase
                                   (do
                                     (println "pass" "generate" passphrase-pass-name)
                                     (pass/generate passphrase-pass-name)
                                     (pass/show passphrase-pass-name)))]
        (assoc $ :passphrase passphrase)))
    (do
      (ssh/keygen $)
      (when-not (:pass/skip? opts)
        (pass/fscopy file pass-name)))))


(defn ssh-keygen
  [{:as opts}]
  (ssh-keygen-1 (prep-ssh-keypair-options [(:ssh.key/id opts) (dissoc opts :ssh.key/id)])))


;; ** Generate ~/.ssh/config


(defn generate-ssh-config-file
  [{:keys [hosts-edn-file
           target]
    :or   {hosts-edn-file (jio/file (System/getProperty "user.home") ".ssh" "hosts.edn")
           target         (jio/file (System/getProperty "user.home") ".ssh" "config")}}]
  (ssh.config/generate-config-file
    hosts-edn-file
    target))


;; ** Fetch keypairs *passwordstore* -> *local(~/.ssh/keypairs)*


(defn fetch-ssh-keypairs-from-pass
  [{:keys [hosts-edn-file]
    :or   {hosts-edn-file (jio/file (System/getProperty "user.home") ".ssh" "hosts.edn")}}]
  {:pre [(shell/find-executable "pass")
         (shell/find-executable "gopass")]}
  (println " * fetch ssh-keypairs from *passwordstore*")
  (pass/git-pull)
  (run!
    (fn [{:keys [:ssh.keygen/file :pass/pass-name]}]
      (try
        (when-let [pass-name' (ssh-keypair-pass-name pass-name)]
          (jio/make-parents (jio/as-file file))
          (pass/fscopy pass-name' (jio/file file)))
        (catch Throwable e (stacktrace/print-stack-trace e))))
    (->> (:ssh/keypairs (ssh.config/read-hosts-edn-file hosts-edn-file))
      (map prep-ssh-keypair-options)
      (sort-by (fn [{:keys [:ssh.key/id]}] id)))))


;; ** Keygen keypairs and push it -> *passwordstore*


(defn ssh-keygen-all
  ":ssh/keypairs passwordstore existency test

  private-key exists in passwordstore -> do nothing
  not exist in passwordstore -> ssh-keygen-1 -> fscopy key-file ssh/keypairs/:pass/pass-name"
  [{:keys [hosts-edn-file]
    :or   {hosts-edn-file (jio/file (System/getProperty "user.home") ".ssh" "hosts.edn")}}]
  {:pre [(shell/find-executable "pass")
         (shell/find-executable "gopass")]}
  (println " * ssh-keygen all :ssh/keypairs from hosts.edn")
  (run!
    (fn [{:keys [:ssh.keygen/overwrite
                :pass/pass-name]
         :as   options}]
      (if (and (pass/show pass-name) (not overwrite))
        (println "[skip] pass exists:" pass-name)
        (do
          (when overwrite
            (println "[overwrite]:" pass-name))
          (ssh-keygen-1 options))))
    (->> (:ssh/keypairs (ssh.config/read-hosts-edn-file hosts-edn-file))
      (map prep-ssh-keypair-options)
      (sort-by (fn [{:keys [:ssh.key/id]}] id)))))


(defn setup-ssh
  [{:as opts}]
  {:pre [(shell/find-executable "pass")
         (shell/find-executable "gopass")]}
  (fetch-ssh-keypairs-from-pass opts)
  (ssh-keygen-all opts)
  (generate-ssh-config-file opts))


(comment
  )

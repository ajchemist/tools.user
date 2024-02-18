(ns tools-user.core.alpha
  (:require
   [clojure.stacktrace :as stacktrace]
   [clojure.spec.alpha :as s]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [tools-user.util :as util]
   [tools-user.shell.alpha :as shell]
   [tools-user.shell.ssh.alpha :as ssh]
   [tools-user.ssh.config.alpha :as ssh.config]
   [tools-user.shell.gpg.alpha :as gpg]
   [ajchemist.passwordstore.core.alpha :as pass]
   )
  (:import
   java.io.PushbackReader
   java.io.File
   ))


;; * GPG


(def ^:const +gpg-keypair-pass-name-prefix+ "gpg/keypairs/")


(defn- gpg-keypair-pass-name
  [pass-name]
  (if (str/starts-with? pass-name +gpg-keypair-pass-name-prefix+)
    pass-name
    (str +gpg-keypair-pass-name-prefix+ (util/strip-left-slash pass-name))))


(defn- prep-gpg-keypair-options
  [[ident {:as opts}]]
  {:pre [(qualified-ident? ident)]}
  (-> opts
    (assoc :gpg.key/ident ident)
    (update :pass/pass-name
      (fn [pass-name]
        (cond
          (string? pass-name) (gpg-keypair-pass-name pass-name)
          :else               (gpg-keypair-pass-name (str (str/join "/" (str/split (namespace ident) #"\.")) "/" (name ident))))))))


(defn read-gpg-keypairs
  [keypairs-edn-file]
  (->> (edn/read (PushbackReader. (jio/reader keypairs-edn-file)))
    (map prep-gpg-keypair-options)))


(defn gpg-import
  [{:keys [:pass/pass-name]}]
  (let [tmp-file (File/createTempFile "gpg-import" "")]
    (pass/fscopy-from-vault (str pass-name ".pub") tmp-file)
    (gpg/import tmp-file)
    (jio/delete-file tmp-file)))


(defn gpg-import-secret
  [{:keys [:pass/pass-name]}]
  (let [tmp-file (File/createTempFile "gpg-import-secret" "")]
    (pass/fscopy-from-vault pass-name tmp-file)
    (gpg/import tmp-file)
    (jio/delete-file tmp-file)))


(defn gpg-export
  [{:keys [:gpg.key/real :gpg.key/comment :gpg.key/email :pass/pass-name]}]
  (let [tmp-file (File/createTempFile "gpg-export" "")]
    (gpg/export tmp-file (gpg/render-gpg-target real comment email))
    (pass/fscopy-from-file tmp-file (str pass-name ".pub"))
    (jio/delete-file tmp-file)))


(defn gpg-export-secret-key
  [{:keys [:gpg.key/real :gpg.key/comment :gpg.key/email :pass/pass-name]}]
  (let [tmp-file (File/createTempFile "gpg-export-secret-key" "")]
    (gpg/export-secret-key tmp-file (gpg/render-gpg-target real comment email))
    (pass/fscopy-from-file tmp-file pass-name)
    (jio/delete-file tmp-file)))


(defn import-gpg-keypairs-from-pass
  [{:keys [keypairs-edn-file]
    :or   {keypairs-edn-file (jio/file (System/getProperty "user.home") ".gnupg" "keypairs.edn")}}]
  {:pre [(shell/find-executable "gpg")
         (shell/find-executable "pass")
         (shell/find-executable "gopass")]}
  (println " * fetch gpg-keypairs from *passwordstore*")
  (pass/git-pull)
  (run!
    (fn [{:keys [:gpg.key/file :pass/pass-name] :as opt}]
      (try
        (when (pass/show pass-name)
          (gpg-import opt)
          (gpg-import-secret opt))
        (catch Throwable e (stacktrace/print-stack-trace e))))
    (sort-by (fn [{:keys [:gpg.key/ident]}] ident) (read-gpg-keypairs keypairs-edn-file))))


(defn- gpg-genkey-1
  "Dependencies:
  - gnupg
  - pass
  - gopass
  🔐"
  [{:keys [:pass/pass-name :gpg.key/ident :gpg.key/email :gpg.key/no-protection :gpg.key/passphrase-pass-name]
    :as   opts}]
  {:pre [(qualified-ident? ident)
         (string? email)]}
  (as-> (-> (reduce-kv
              (fn [m k v]
                ;; remove all keys except gpg.key/*
                ;; make gpg key options
                (if (= (namespace k) "gpg.key")
                  (assoc m k v)
                  m))
              {}
              opts)
          (update :gpg.key/passphrase
            (fn [passphrase]
              (or
                passphrase
                (when-not no-protection
                  (let [passphrase-pass-name (or passphrase-pass-name (str pass-name "/passphrase"))
                        passphrase           (pass/show passphrase-pass-name)]
                    (cond
                      (nil? passphrase)
                      (do
                        (pass/generate passphrase-pass-name)
                        (pass/show passphrase-pass-name))

                      :else passphrase))))))
          (update :gpg.key/real #(or % (str (namespace ident) "/" (name ident)))))
    {:keys [:gpg.key/passphrase] :as opts'}
    (cond-> opts'
      (and (not no-protection)
           (string? passphrase)
           (not (str/blank? passphrase))) (assoc :gpg.key/passphrase passphrase))
    (do
      (-> opts'
        (update-keys #(keyword (name %)))
        (gpg/genkey))
      (let [opts'' (merge opts opts')]
        ;; recover original options e.g. :pass/*
        (when-not (:pass/skip? opts)
          (gpg-export opts'')
          (gpg-export-secret-key opts''))))))


(defn gpg-genkey
  [{:as opts}]
  (gpg-genkey-1 (prep-gpg-keypair-options [(:gpg.key/ident opts) (dissoc opts :gpg.key/ident)])))


(defn gpg-genkey-all
  "passwordstore existency test

  keypair exists in passwordstore -> do nothing
  not exist in passwordstore -> gpg-genkey-1 -> fscopy key gpg/keypairs/:pass/pass-name"
  [{:keys [keypairs-edn-file]
    :or   {keypairs-edn-file (jio/file (System/getProperty "user.home") ".gnupg" "keypairs.edn")}}]
  {:pre [(shell/find-executable "gpg")
         (shell/find-executable "pass")
         (shell/find-executable "gopass")]}
  (println " * gpg-keygen all :gpg/keypairs from keypairs.edn")
  (run!
    (fn [{:keys [:gpg.key/overwrite
                :pass/pass-name]
         :as   options}]
      (let [query-ret (pass/show pass-name)]
        (cond
          (nil? query-ret)
          (gpg-genkey-1 options)

          (and (some? query-ret) overwrite)
          (do
            (println "[overwrite]:" pass-name)
            (gpg-genkey-1 options))

          :else
          (println "[skip] pass exists:" pass-name))))
    (sort-by (fn [{:keys [:gpg.genkey/ident]}] ident) (read-gpg-keypairs keypairs-edn-file))))


;; * SSH


(def ssh-default-keypairs-dir (jio/file (System/getProperty "user.home") ".ssh" "keypairs"))
(def ^:const +ssh-keypair-pass-name-prefix+ "ssh/keypairs/")


;;
;; - hosts.edn
;; - ssh-keygen command-line
;; - pass, gopass command-line


(defn- ssh-keypair-pass-name
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
            passphrase           (cond
                                   (nil? passphrase)
                                   (do
                                     (println "pass" "generate" passphrase-pass-name)
                                     (pass/generate passphrase-pass-name)
                                     (pass/show passphrase-pass-name))

                                   :else passphrase)]
        (assoc $ :passphrase passphrase)))
    (do
      (ssh/keygen $)
      (when-not (:pass/skip? opts)
        (pass/fscopy-from-file file pass-name)
        (pass/fscopy-from-file (str file ".pub") (str pass-name ".pub"))))))


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


(defn read-ssh-keypairs
  [keypairs-edn-file]
  (->> (edn/read (PushbackReader. (jio/reader keypairs-edn-file)))
    (map prep-ssh-keypair-options)))


(defn fetch-ssh-keypairs-from-pass
  [{:keys [keypairs-edn-file]
    :or   {keypairs-edn-file (jio/file (System/getProperty "user.home") ".ssh" "keypairs.edn")}}]
  {:pre [(shell/find-executable "pass")
         (shell/find-executable "gopass")]}
  (println " * fetch ssh-keypairs from *passwordstore*")
  (pass/git-pull)
  (run!
    (fn [{:keys [:ssh.keygen/file :pass/pass-name]}]
      (try
        (when-some [pass-name' (ssh-keypair-pass-name pass-name)]
          (let [file (jio/as-file file)]
            (jio/make-parents file)
            (pass/fscopy-from-vault pass-name' file) ; private key
            (pass/fscopy-from-vault (str pass-name' ".pub") (jio/file (str file ".pub")))) ; pub key
          )
        (catch Throwable e (stacktrace/print-stack-trace e))))
    (sort-by (fn [{:keys [:ssh.key/id]}] id) (read-ssh-keypairs keypairs-edn-file))))


;; ** Keygen keypairs and push it -> *passwordstore*


(defn ssh-keygen-all
  ":ssh/keypairs passwordstore existency test

  private-key exists in passwordstore -> do nothing
  not exist in passwordstore -> ssh-keygen-1 -> fscopy key-file ssh/keypairs/:pass/pass-name"
  [{:keys [keypairs-edn-file]
    :or   {keypairs-edn-file (jio/file (System/getProperty "user.home") ".ssh" "keypairs.edn")}}]
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
    (->> (read-ssh-keypairs keypairs-edn-file)
      (sort-by (fn [{:keys [:ssh.key/id]}] id)))))


(defn setup-ssh
  [{:as opts}]
  {:pre [(shell/find-executable "pass")
         (shell/find-executable "gopass")]}
  (fetch-ssh-keypairs-from-pass opts)
  (ssh-keygen-all opts)
  (generate-ssh-config-file opts))


;; * main


(def ^:privates verbose-levels
  {:trace 0
   :debug 1
   :info  2
   :warn  3
   :error 4
   :fatal 5
   :off   6})


(defn println-tap
  [o]
  (try
    (when (vector? o)
      (let [[level & message] o

            prop  (System/getProperty "verbose.level")
            pivot (cond
                    (nil? prop) 2
                    :else       (parse-long prop))]
        (when (<= pivot (get verbose-levels level) )
          (apply println message))))
    (catch Throwable _)))


#_:clj-kondo/ignore
(defonce ^:private setup-tap (add-tap println-tap))


(comment
  (tap> [:info :hello])
  (tap> [:trace :hello])
  (remove-tap println-tap)
  )

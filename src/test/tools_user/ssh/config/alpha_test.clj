(ns tools-user.ssh.config.alpha-test
  (:require
   [clojure.test :as test :refer [deftest is are testing]]
   [clojure.spec.alpha :as s]
   [tools-user.ssh.config.alpha :as ssh.config]
   [tools-user.util :as util]
   ))


(def hosts-edn
  '{:ssh.config/preamble  []
    :ssh.config/postamble [{:Host                     "*"
                            :PreferredAuthentications "publickey"
                            :AddKeysToAgent           "yes"
                            :UseKeychain              "yes"
                            :StrictHostKeyChecking    "ask"
                            :ForwardAgent             "no"}]
    :ssh.config/hosts
    {github.com/user {:ssh/config {:Host     "github.com"
                                   :user     "git"
                                   :HostName "github.com"}}}})


(deftest main
  (s/explain
    :ssh.hosts/edn
    hosts-edn)
  (ssh.config/print-config-file hosts-edn))


(comment
  (let [key-file (:IdentityFile config)
        key-file (util/tilde-expansion key-file)]
   (try
     (ssh/fingerprint key-file)
     (catch Throwable _
       ;; when not valid key file
       (println "🔴 Invalid key-file:" (.getPath (jio/as-file key-file)))
       (let [opts {:id        id
                   :overwrite true}
             opts (case (name type)
                    "rsa4096" (ssh/rsa4096-opts opts)
                    (ssh/ed25519-opts opts))]
         (ssh-keygen opts)))))
  )

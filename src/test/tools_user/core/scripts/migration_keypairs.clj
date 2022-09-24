(ns tools-user.core.scripts.migration-keypairs
  (:require
   [clojure.java.io :as jio]
   [tools-user.core.alpha :as tools-user]
   [tools-user.shell.pass.alpha :as pass]
   ))


(time
  (run!
    (fn [opt]
      (tools-user/gpg-export opt)
      (tools-user/gpg-export-secret-key opt))
    (tools-user/read-gpg-keypairs (jio/file (System/getProperty "user.home") ".gnupg" "keypairs.edn"))))


(time
  (run!
    (fn [[file pass-name]]
      (pass/fscopy-from-file file pass-name))
    (into {}
      (map (fn [{:keys [:ssh.keygen/file :pass/pass-name]}] [(str file ".pub") (str pass-name ".pub")]))
      (tools-user/read-ssh-keypairs (jio/file (System/getProperty "user.home") ".ssh" "hosts.edn")))))

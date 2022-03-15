(ns tools-user.core.alpha-test
  (:require
   [clojure.java.io :as jio]
   [clojure.test :as test :refer [deftest is are testing]]
   [tools-user.core.alpha :as core]
   ))


(def prepared-keypair-options-1
  (#'core/prep-ssh-keypair-options
    ['test.test-group/test-name
     {:ssh.keygen/file         "/tmp/id_ed25519"
      :ssh.keygen/type         "ed25519"
      :ssh.keygen/nopassphrase true}]))


(def prepared-keypair-options-2
  (#'core/prep-ssh-keypair-options
    ['test.test-group/test-name
     {:ssh.keygen/type         "ed25519"
      :ssh.keygen/nopassphrase true}]))


(deftest ssh
  (is (= (:pass/pass-name prepared-keypair-options-1) "ssh/keypairs/test/test-group/test-name"))
  (is (= (:ssh.keygen/file prepared-keypair-options-1) "/tmp/id_ed25519"))
  (is (= (:ssh.keygen/file prepared-keypair-options-2) (.getPath (jio/file @#'core/ssh-default-keypairs-dir "test/test-group/test-name"))))
  (core/ssh-keygen prepared-keypair-options-1)
  )

(ns tools-user.shell.ssh.alpha-test
  (:require
   [clojure.test :as test :refer [deftest is are testing]]
   [clojure.java.shell :as jsh]
   [tools-user.shell.ssh.alpha :as ssh]
   )
  (:import
   java.io.File
   ))


(deftest main
  (is
    (=
      (ssh/keygen-args
        {:format  "PEM"
         :type    "rsa"
         :bits    4096
         :comment "COMMENT"})
      ["-m" "PEM" "-t" "rsa" "-b" 4096 "-C" "COMMENT"]))


  (ssh/keygen-ed25519
    {:file         (File/createTempFile "key" "")
     :overwrite    true
     :nopassphrase true})


  (ssh/keygen-rsa4096
    {:file         (File/createTempFile "key" "")
     :overwrite    true
     :nopassphrase true}))


(comment
  (apply jsh/sh
    "ssh-keygen"
    (ssh/keygen-args
      (ssh/ed25519-opts
        {:file         (File/createTempFile "key" "")
         :overwrite    true
         :nopassphrase true}))


    )
  )

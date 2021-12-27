(ns tools-user.shell.ssh.alpha-test
  (:require
   [clojure.test :as test :refer [deftest is are testing]]
   [clojure.java.shell :as jsh]
   [tools-user.shell.ssh.alpha :as ssh]
   )
  (:import
   java.io.File
   ))


(def key-file (File/createTempFile "key" ""))
(def key-file-1 (File/createTempFile "key" ""))


(deftest main
  (is
    (=
      (ssh/keygen-args
        {:format  "PEM"
         :type    "rsa"
         :bits    4096
         :comment "COMMENT"})
      ["-m" "PEM" "-t" "rsa" "-b" "4096" "-C" "COMMENT"]))


  (locking key-file
    (ssh/keygen-ed25519
      {:file         key-file
       :overwrite    true
       :nopassphrase true})
    (ssh/fingerprint key-file))


  (locking key-file-1
    (ssh/keygen-rsa4096
      {:file         key-file-1
       :overwrite    true
       :nopassphrase true})
    (ssh/fingerprint key-file-1)))


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

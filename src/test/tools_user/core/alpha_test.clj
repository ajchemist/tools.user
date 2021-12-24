(ns tools-user.core.alpha-test
  (:require
   [clojure.test :as test :refer [deftest is are testing]]
   [tools-user.core.alpha :as core]
   ))


(comment
  (core/ssh-keygen
    {:id         'test-group/test-name
     :file       "/tmp/id_ed25519"
     :type       "ed25519"
     :passphrase "passphrase"
     :overwrite  true})


  (core/ssh-keygen
    {:id        'github.com/magneto-core
     :type      "ed25519"
     :overwrite true})
  )

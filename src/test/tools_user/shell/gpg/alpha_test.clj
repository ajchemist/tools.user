(ns tools-user.shell.gpg.alpha-test
  (:require
   [clojure.test :as test :refer [deftest is are testing]]
   [clojure.java.shell :as jsh]
   [tools-user.shell.gpg.alpha :as gpg]
   ))


(deftest main
  (is (= (gpg/render-gpg-target "real" nil "a@b.c") "real <a@b.c>"))
  (is (= (gpg/render-gpg-target "real" "comment" "a@b.c") "real (comment) <a@b.c>")))


(comment
  (gpg/genkey {:dry-run true})
  (gpg/genkey {:dry-run true :real "real-test" :email "a@b.c"})
  (gpg/genkey {:real "tools-user.shell.gpg.alpha-test" :email "a@b.c"}) ; prompt passphrase
  (gpg/genkey {:real "tools-user.shell.gpg.alpha-test" :email "a@b.c" :no-protection true}) ; no-prompt passphrase


  (gpg/import "/Users/ajchemist/private.key"){:exit 0, :out "", :err "gpg: key 5B66C9165AF6E9E1: public key \"tools-user.shell.gpg.alpha-test <a@b.c>\" imported\ngpg: key 5B66C9165AF6E9E1: secret key imported\ngpg: Total number processed: 1\ngpg:               imported: 1\ngpg:       secret keys read: 1\ngpg:   secret keys imported: 1\n"}
  (gpg/import "/Users/ajchemist/public.key"){:exit 0, :out "", :err "gpg: key 5B66C9165AF6E9E1: \"tools-user.shell.gpg.alpha-test <a@b.c>\" not changed\ngpg: Total number processed: 1\ngpg:              unchanged: 1\n"}


  (jsh/sh "cat" :in (:out (jsh/sh "gpg" "--export-secret-keys" "--armor" "github.com/november-young/bt-friends")))
  )

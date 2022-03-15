---
author: ajchemist
---


# Installation


``` shell
clojure -Ttools install io.github.ajchemist/tools.user '{:git/sha "cad94937514505a451cce78fbf8ac3aaae54d4c8"}' :as tools.user
```


# SSH


1. *passwordstore* is ssh private key store.
   - fetch from *passwordstore*
   - keygen and push to *passwordstore*
   - `gopass fscopy`
   - private key exists in =~/.ssh/keypairs= is just local cache


2. =~/.ssh/keypairs= is ssh public key store.
   - separated git repository


## Fetch keypairs from *passwordstore*


Read `~/.ssh/hosts.edn` to fetch keypairs from *passwordstore*


``` shell
clojure -Ttools.user fetch-ssh-keypairs-from-pass
```


## Keygen keypairs


``` shell
clojure -Ttools.user ssh-keygen-all
```


Autogenerated key `passphrase`


``` shell
clojure -Ttools.user ssh-keygen :ssh.key/id $id
```


key `passphrase` 직접입력 방법


``` shell
pass insert ssh/keypairs/$id/passphrase
clojure -Ttools.user ssh-keygen :ssh.key/id $id
```



## Generate ~/.ssh/config


Read `~/.ssh/hosts.edn` to generate ssh configuration file.


``` shell
clojure -Ttools.user generate-ssh-config-file
```


## Do all


``` shell
clojure -Ttools.user setup-ssh
```


##


``` shell
cat $public_key | ssh $host "cat - >> ~/.ssh/authorized_keys"
ssh $host "chmod 600 ~/.ssh/authorized_keys"
```

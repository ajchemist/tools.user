(ns tools-user.ssh.config.alpha
  (:require
   [clojure.spec.alpha :as s]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.java.io :as jio]
   )
  (:import
   java.io.PushbackReader
   ))


(set! *warn-on-reflection* true)


(s/def ::Host string?)


(s/def :ssh/config
  (s/keys :req-un [::Host]))


(s/def :ssh.config/preamble (s/coll-of :ssh/config))
(s/def :ssh.config/postamble (s/coll-of :ssh/config))
(s/def :ssh.config/hosts (s/map-of qualified-ident? (s/keys :opt [:ssh/config])))
(s/def :ssh.hosts/edn (s/keys :opt [:ssh.config/preamble
                                    :ssh.config/postamble
                                    :ssh.config/hosts
                                    :ssh/keypairs]))


(def ^:dynamic *block-separator* (str (System/getProperty "line.separator") (System/getProperty "line.separator")))


(defn print-config-block
  [{:as opts}]
  (print "Host" (:Host opts)) (newline)
  (run!
    (fn [[k v]]
      (print (str "  " (name k)) v) (newline))
    (dissoc opts :Host))
  (flush))


(defn print-config-blocks
  [ssh-config-blocks]
  (run!
    (fn [opts]
      (print-config-block opts)
      (print *block-separator*))
    ssh-config-blocks)
  (flush))


(defn print-config-file
  [hosts]
  {:pre [(s/valid? :ssh.hosts/edn hosts)]}
  (println (str "# AUTOGENERATED FILE STARTS HERE." *block-separator*))
  (print-config-blocks
    (:ssh.config/preamble hosts))
  (print-config-blocks
    (into []
      (comp
        (map
          (fn [[_ {:keys [:ssh/config]}]]
            config))
        (remove nil?))
      (sort-by
        (fn [[id]] id)
        (:ssh.config/hosts hosts))))
  (print-config-blocks
    (:ssh.config/postamble hosts))
  (println "# AUTOGENERATED FILE ENDS HERE."))


(defn read-hosts-edn-file
  [file]
  (edn/read (PushbackReader. (jio/reader file))))


(defn generate-config-file
  [edn-file to]
  (println "Writing ssh/config file ->" (.getPath (jio/as-file to)))
  (binding [*out* (jio/writer to)]
    (print-config-file
      (read-hosts-edn-file edn-file))))


(set! *warn-on-reflection* false)

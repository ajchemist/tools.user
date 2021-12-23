(ns tools-user.shell.alpha
  (:require
   [clojure.java.shell :as jsh]
   ))


(defn exit!
  [{:keys [exit out err] :as sh-return}]
  (println (str err out))
  (when-not (zero? exit)
    (throw (ex-info "Non-zero exit." sh-return))))

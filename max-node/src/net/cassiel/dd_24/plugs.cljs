(ns net.cassiel.dd-24.plugs
  "Dynamic loading of plug-ins, labelling etc."
  (:require [net.cassiel.dd-24.core :as c]))

(def ^:private initial (vec (conj (repeat 5 "---") "IO")))
(def ^:private PLUG-STATE (atom initial))

(defn- flush [data]
  (apply c/xmit :crosspatch :inlabels data)
  (apply c/xmit :crosspatch :outlabels data)

  (doseq [x [["setdirection" "down"]
             ["setalignment" "right"]
             ["setsize" 6]
             ["clear"]]]
    (apply c/xmit :krellmixer x))

  (doseq [lab (reverse data)]
    (c/xmit :krellmixer :append lab)))

(defn label
  "`i` starts at 1; label 0 is always 'IO'"
  [i text]
  (flush (swap! PLUG-STATE assoc i (name text))))

(defn reset []
  (flush (reset! PLUG-STATE initial)))

(ns net.cassiel.dd-24.plugs
  "Dynamic loading of plug-ins, labelling etc."
  (:require [net.cassiel.dd-24.core :as c]))

(def ^:private initial (vec (conj (repeat 5 "---") "IO")))
(def ^:private PLUG-STATE (atom initial))

(defn- setup-max [state]
  (doseq [[i n] (rest (map list (range) state))]
    (c/xmit :now :set-plug i n))

  (apply c/xmit :crosspatch :inlabels state)
  (apply c/xmit :crosspatch :outlabels state)

  (doseq [x [["setdirection" "down"]
             ["setalignment" "right"]
             ["setsize" 6]
             ["clear"]]]
    (apply c/xmit :krellmixer x))

  (doseq [lab (reverse state)]
    (c/xmit :krellmixer :append lab)))

(defn plug
  "`i` starts at 1; label 0 is always 'IO'."
  [i plug-name]
  (setup-max (swap! PLUG-STATE assoc i (name plug-name))))


(defn reset []
  (setup-max (reset! PLUG-STATE initial)))

(ns net.cassiel.dd-24.plugs
  "Dynamic loading of plug-ins, labelling etc.
   Maintain index of loaded plug-ins,"
  (:require [net.cassiel.dd-24.core :as c]
            [cljs.core.async :as async :refer [<! >! go]]))

(def ^:private initial (vec (conj (repeat 5 "---") "IO")))
(def ^:private PLUG-STATE (atom {:previous initial
                                 :current  initial}))

(defn- update-labels [{:keys [current]}]
  (apply c/xmit :crosspatch :inlabels current)
  (apply c/xmit :crosspatch :outlabels current)

  (doseq [x [["setdirection" "down"]
             ["setalignment" "right"]
             ["setsize" 6]
             ["clear"]]]
    (apply c/xmit :krellmixer x))

  (doseq [lab (reverse current)]
    (c/xmit :krellmixer :append lab)))

(defn- update-max
  "Update state of Max: loaded plug-ins and labels on mixer panels."
  [{:keys [previous current] :as state}]
  ;; Set up routing: (new) plug name gets routed to the right VST holder:
  (let [ch (go
             (doseq [[i prev curr] (rest (map list (range) previous current))]
               (when-not (= prev curr)
                 (c/xmit :now :plug i :load curr)
                 (<! (async/timeout 1000))
                 (c/xmit :now :plug i :params)
                 (<! (async/timeout 1000))))

             :done)])

  (update-labels state)
  ch)

(defn plug
  "`i` starts at 1; label 0 is always 'IO'."
  [i plug-name]
  (update-max (swap! PLUG-STATE
                     (fn [{:keys [previous current]}]
                       {:previous current
                        :current  (assoc current i plug-name)}))))

(defn index-of-plug [plug-name]
  (letfn [(p [i names]
            (when names
              (if (= plug-name (first names))
                i
                (p (inc i) (next names)))))]
    (p 0 (:current (deref PLUG-STATE)))))

(defn window
  [plug-name how]
  (when-let [i (index-of-plug plug-name)]
    (c/xmit :win :plug i how)))

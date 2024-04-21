(ns user
  (:require [clojure.spec.alpha :as s]
            [cljs.core.async :as async :refer [go chan <! >!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [net.cassiel.dd-24.core :as c]
            [net.cassiel.dd-24.params :as px]
            [net.cassiel.dd-24.sequencing :as seq]
            [net.cassiel.dd-24.control :as ctrl]
            [net.cassiel.dd-24.plugs :as plugs]
            [net.cassiel.dd-24.conformer :as cx]
            [goog.string :as gstring]
            [goog.string.format]))

(def SEQ (atom {:sequences {} :messages nil}))
(def PARAMS (atom {}))

;; Messages out to Max:
;; - :show: update dictionary view
;; - :win [name] [1/0]: open or close named window ("seq", "main", "audio", "plug 1"-"plug 5")
;; - :now [...m...]: output message
;; - :seq :add :main [pos] [...m...]: schedule message
;; - :krellmixer [...xxx...]: set krell mixer labels, alignment etc.
;; - :crosspatch [...xxx...]: set crosspatch labels

;; Messages [...m...]:
;; - :mix :set [col] [row] [level] [secs]: matrix mix
;; - :to-plug [1..5] [name]: load plug-in
;; - [plug N] "note" [p] [v] [d]: MIDI note with duration
;; - [plug N] "param" [name/id] [val]: parameter change
;; - [plug N] "params": request parameter names
;; - [plug N] "get" [name-or-id]: request parameter value
;; - [plug N] :plugged [0/1]: unplug/replug device to reset to INIT

;; Incoming:
;; "request" [beat]: request for messages for this beat (via "seq add")
;; "pname" [device] [param-name]: incoming parameter name
;; "pvalue" [device] [param-id] [value] [value-str]: incoming parameter value (such as change)

;; Generic handler:

(ctrl/handle :request
             (fn [pos]
               (let [{:keys [messages]} (swap! SEQ seq/process-request pos)]
                 (doseq [x messages]
                   (apply c/xmit :seq :add :main x))))

             :pname
             (fn [& args] (apply px/pname-in PARAMS args))

             :pvalue
             (fn [& args] (apply px/pvalue-in PARAMS args)))

(c/xmit :master 0 0)

(c/xmit :now :mix :set 2 2 -40 3)

(c/xmit :now :to-plug 1 )


(plugs/label 1 :HELLO)

(plugs/reset)

(ns user
  (:require [clojure.spec.alpha :as s]
            [cljs.core.async :as async :refer [go chan <! >!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [oops.core :refer [oget ocall]]
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
;; - :win [name] [1/0]: open or close named window ("seq", "main" (open only!), "audio", "plug" 1-5)
;; - :now [...m...]: output message
;; - :seq :add :main [pos] [...m...]: schedule message
;; - :krellmixer [...xxx...]: set krell mixer labels, alignment etc.
;; - :crosspatch [...xxx...]: set crosspatch labels

;; Messages [...m...]:
;; - :mix :set [col] [row] [level] [secs]: matrix mix
;; - :plug [1-5] :load [name]: plug device
;; - :plug [1-5] :note [p] [v] [d]: MIDI note with duration
;; - :plug [1-5] :param [name/id] [val]: parameter change
;; - :plug [1-5] :params: request parameter names
;; - :plug [1-5] :get [name-or-id]: request parameter value

;; Incoming:
;; "request" [beat]: request for messages for this beat (via "seq add")
;; "pname" [1-5] [param-name]: incoming parameter name
;; "pvalue" [1-5] [param-id] [value] [value-str]: incoming parameter value (such as change)

;; Generic handler:

(ctrl/handle :request
             ;; Process sequence step: populate this bar (fired one bar in advance):
             (fn [pos]
               (let [{:keys [messages]} (swap! SEQ seq/process-request pos)]
                 (doseq [x messages]
                   (apply c/xmit :seq :add :main x))))

             :pname
             ;; Parameter name in: swap into PARAMS
             (fn [& args] (apply px/pname-in PARAMS args))

             ;; Parameter value in: swap into params
             :pvalue
             (fn [& args] (apply px/pvalue-in PARAMS args)))

;; APIs:

(ctrl/window :seq 0)
(ctrl/window :main 1)
(ctrl/window :audio 0)

;; (ctrl/window <Loaded VST Name> 0/1)

;; (px/request-params PARAMS <Loaded-VST-Name>)
;; (px/get-matching PARAMS <Loaded-VST-Name> #"LFO")
;; (px/get-matching-to-dict PARAMS <Loaded-VST-Name> #"LFO")


;; Simple mixing:

(c/xmit :master 0 0)
(c/xmit :now :mix :set 2 2 -40 3)

;; Manual test of plug-in control:

(c/xmit :now :plug 1 :load "Rift")
(c/xmit :now :plug 1 :load "Enso")

(c/xmit :win :plug 1 0)

(c/xmit :now :plug 1 :params)


(deref PARAMS)

(plugs/plug 1 "Rift")
(plugs/plug 1 "Other Desert Cities")
(plugs/index-of-plug "Other Desert Cities")

(plugs/win "Other Desert Cities" 0)

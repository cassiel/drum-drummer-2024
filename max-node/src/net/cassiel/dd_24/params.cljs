(ns net.cassiel.dd-24.params
  (:require [clojure.spec.alpha :as s]
            [cljs.core.async :as async :refer [<! >! go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [net.cassiel.dd-24.core :as c]
            [net.cassiel.dd-24.conformer :as cx]
            [goog.string :as gstring]
            [goog.string.format]))

;; Map each parameter name to [ID value value-str] - value[-str] may be nil to start.
(s/def ::params (s/map-of keyword? (s/tuple integer?
                                            (s/nilable float?)
                                            (s/nilable keyword?))))
(s/def ::counter integer?)
(s/def ::device (s/keys :opt-un [::params ::counter]))
(s/def ::Basic ::device)
(s/def ::Enso ::device)
(s/def ::Replika_XT ::device)
(s/def ::Filter_MINI ::device)
(s/def ::param-tracking (s/keys :opt-un [::Basic ::Enso ::Replika_XT ::Filter_MINI ::Axon]))

;; For now we'll preset some enums for some parameters. (We could
;; potentially automate this data gathering.)

(defn symbolic-range
  "Inclusive range. `0` is `:0`, all others are signed `+n` or `-n`."
  [f t]
  (letfn [(as-str [n] (if (> n 0)
                        (gstring/format "+%d" n)
                        (gstring/format "%d" n)))]
    (vec (map (comp keyword as-str) (range f (inc t))))))

(defn notes-on-octave [octave]
  (map (fn [s] (goog.string/format "%s%d" s octave))
       ["C" "C#" "D" "D#" "E" "F" "F#" "G" "G#" "A" "A#" "B"]))

(def named-notes (->> (map notes-on-octave (range -2 7))
                      flatten
                      (take (+ (* 8 12) 5))
                      (map keyword)))

(def param-enums {:Replika_XT {:Modulation_Mode [:No_FX :Phaser :Flanger
                                                 :Chorus :Freq_Shifter
                                                 :Filter :Pitch_Shifter
                                                 :Micro_Pitcher]
                               :Delay_Mode [:Modern :Analogue :Tape_Echo
                                            :Vintage_Digital :Diffusion]
                               :PS_Shift_L (symbolic-range -12 12)
                               :PS_Shift_R (symbolic-range -12 12)
                               :Time_Mode [:Straight :Dotted :Triplets :Milliseconds]}
                  :Enso (let [speeds {:-2.0 0.574349164962769
                                      :-1.0 0.675480008125305
                                      :-0.5 0.718441188335419
                                      :+0.5 0.794417858123779
                                      :+1.0 0.828613519668579
                                      :+2.0 0.891301214694977}]
                          {:Mode [:Record :Overdub :Play :Stop]
                           :Link_Speeds [:Off :On]
                           :Play_Speed speeds
                           :Rec_Speed speeds})
                  :Basic (let [shapes [:Triangle :Sawtooth :DigiGrit
                                       :20%Pulse :Square :80%Pulse]]
                           {:Osc1Shap shapes
                            :Osc2Shap shapes
                            :Osc3Shap shapes})
                  :Axon (->> (map (fn [i] [(keyword (gstring/format  "V%d_Pitch" i))
                                           named-notes])
                                  (range 7))
                             (into {:ClockRat [:32 :16 :8]}))})

(defn positions
  [pred coll]
  (seq (keep-indexed (fn [idx x] (when (pred x) idx))
                     coll)))

(defn map-value [device pname value-str]
  (when-let [vals (get-in param-enums [device pname])]
    (if (map? vals)
      (get vals value-str)
      (when-let [idx (positions #{value-str} vals)]
        (/ (first idx) (dec (count vals)))))))

(defn pitch [note-name]
  (when-some [idx (positions #{note-name} named-notes)]
    (first idx)))

(defn pname-in
  "Parameter message in. Clean up the possibly multi-part name, map to param ID
   with nil for numeric and string value."
  [PARAMS device & name-parts]
  (let [device-k (keyword device)
        pname (keyword (clojure.string/join "_" name-parts))
        P'
        (swap! PARAMS
               (fn [P]
                 (let [counter (or (get-in P [device-k :counter]) 0)
                       params (get-in P [device-k :params])]
                   (-> P
                       (assoc-in [device-k :counter] (inc counter))
                       (assoc-in [device-k :params pname] [(inc counter) nil nil])
                       #_ (as-> X
                           (cx/conformer ::param-tracking X))))))]

    ;; Filter_MINI has a huge number of "MIDI CC [...]" params. Don't try to get the
    ;; initial values, it'll cause trouble.
    (when-not (= (first name-parts) "MIDI")
      (.outlet c/max-api "now" device "get" (dec (get-in P' [device-k :counter]))))))

(defn lookup-param-name [param-map id]
  (let [map-seq (seq param-map)
        matches (filter (fn [[_ [id' _]]] (= id id')) map-seq)]
    (when-let [candidate (first matches)]
      (first candidate))))

(defn pvalue-in
  "Parameter value in by ID: update the parameter tracking data.
   Ignore if device or ID not (yet) known."
  [PARAMS device id value value-str]
  (println id value value-str)
  (let [kdevice (keyword device)]
    (swap! PARAMS
           (fn [P]
             (if-let [param-map (get-in P [kdevice :params])]
               (if-let [pname (lookup-param-name param-map id)]
                 (-> P
                     (assoc-in [kdevice :params pname] [id value (keyword value-str)])
                     #_ (as-> X (cx/conformer ::param-tracking X)))
                 P)
               P)))))

(defn request-params [PARAMS device]
  (swap! PARAMS dissoc device)
  (.outlet c/max-api "now" (name device) "params")
  (async/timeout 500))

(defn get-matching
  "All parameter names matching a regexp."
  [PARAMS device re]
  (let [data (deref PARAMS)
        params (get-in data [device :params])]
    (->> (keys params)
         (filter (comp (partial re-find re) name))
         (map (fn [p] [p (get params p)])))))

(defn get-matching-to-dict [PARAMS device re]
  (let [items (get-matching PARAMS device re)
        data (deref PARAMS)
        json-obj (clj->js (into {} (map
                                    (fn [x]
                                      (let [pvec (get-in data [device :params x])]
                                        [x (str (nth pvec 1)
                                                " ["
                                                (nth pvec 2) "]")]))
                                    items)))]
    (go
      (<p! (.setDict c/max-api "X" json-obj))
      (.outlet c/max-api "show"))))

(defn xmit-program [dev i]
  (c/xmit :now (name dev) i)
  (async/timeout 1000))

(defn xmit-some-params-now [dev & args]
  (doseq [[pname v] args]
    (let [v' (if (keyword? v) (map-value dev pname v) v)]
      (c/xmit :now dev :param pname v')))
  (async/timeout 500))

(defn axon-pitch-set [& values]
  (apply xmit-some-params-now
         :Axon
         (map-indexed (fn [i x] [(keyword (gstring/format "V%d_Pitch" i)) x])
                      values)))

(ns net.cassiel.dd-24.sequencing
  (:require [net.cassiel.dd-24.params :as px]
            [net.cassiel.dd-24.conformer :as cx]
            [clojure.spec.alpha :as s]))

;; Sequencer prompts just come in as repeated counts of 1..4. For each
;; one, return a list (doesn't need to be ordered) of
;; [pos & tokens] where pos is floating point from count (incl.) to
;; count+1 (excl.) - this goes straight into seq~.

(s/def ::token (s/or :symbol keyword?
                     :number number?))
(s/def ::offset (s/and number? #(>= % 0.0) #(< % 1.0)))

(s/def ::seq-item (s/cat :offset ::offset
                         :message (s/+ ::token)))


;; A sequence of seq-item is filed against a beat:

(s/def ::items-in-beat (s/coll-of ::seq-item))

;; A map of these indexed by beat number is a sequence block:

(s/def ::beat (s/and integer? #(>= % 1) #(<= % 4)))
(s/def ::seq-block (s/map-of ::beat ::items-in-beat))

;; Overall sequencer content: map from names to sequence block. (The
;; names have no significance other than to allow
;; sequences to be individually manipulated.)

(s/def ::sequences (s/map-of keyword? ::seq-block))

;; Actually, overall sequencer state is a map with :sequences and :messages,
;; so that we can do a swap! and get out the incremental output items
;; afterwards.

(s/def ::messages (s/* ::token))

(s/def ::sequencer-state (s/keys :req-un [::sequences ::messages]))

;; Transmit messages for a particular request position (1..4).

(defn process-request
  "Takes state * pos, returns state' incorporating messages."
  [{:keys [sequences] :as state} pos]
  (let [seq-maps-for-names (map fnext (seq sequences))
        ;; Each of those is a map: int -> seq-block

        items-at-this-pos (reduce concat (map #(get % pos) seq-maps-for-names))
        process-item #(update (vec %) 0 + pos)
        patch-note (fn [item]
                     (let [item' (vec item)]
                       (if (and (= (nth item' 2) :note)
                                (keyword? (nth item' 3)))
                         (update item' 3 px/pitch)
                         item)))
        ]
    (assoc state :messages (map (comp patch-note process-item) items-at-this-pos))))

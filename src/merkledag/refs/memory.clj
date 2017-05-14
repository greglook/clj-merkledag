(ns merkledag.refs.memory
  "Ref storage backed by a map in an atom."
  (:require
    [merkledag.refs :as refs])
  (:import
    java.time.Instant
    multihash.core.Multihash))


;; Multihash references in a memory tracker are held in a map in an atom.
;; `memory` is a map from ref name to a sequence of historical values for the
;; ref.
(defrecord MemoryRefTracker
  [memory]

  refs/RefTracker

  (list-refs
    [this opts]
    (->> (vals @memory)
         (map first)
         (filter #(or (:value %) (:include-nil opts)))))


  (get-ref
    [this ref-name]
    (refs/get-ref this ref-name nil))


  (get-ref
    [this ref-name version]
    (if version
      (some #(when (= version (:version %)) %)
            (get @memory ref-name))
      (first (get @memory ref-name))))


  (get-history
    [this ref-name]
    (get @memory ref-name))


  (set-ref!
    [this ref-name value]
    {:pre [(instance? Multihash value)]}
    (-> memory
        (swap!
          (fn record-ref
            [db]
            (let [versions (get db ref-name [])
                  current (first versions)]
              (if (= value (:value current))
                db
                (let [new-version {:name ref-name
                                   :value value
                                   :version (inc (:version current 0))
                                   :time (Instant/now)}]
                  (assoc db ref-name (list* new-version versions)))))))
        (get ref-name)
        (first)))


  (delete-ref!
    [this ref-name]
    (let [existed? (contains? @memory ref-name)]
      (swap! memory dissoc ref-name)
      existed?)))


(alter-meta! #'->MemoryRefTracker assoc :private true)
(alter-meta! #'map->MemoryRefTracker assoc :private true)


(defn memory-ref-tracker
  "Creates a new in-memory ref tracker."
  []
  (->MemoryRefTracker (atom (sorted-map))))

(ns merkledag.refs
  "Mutable references stored with a repository."
  (:require
    [multihash.core :as multihash])
  (:import
    multihash.core.Multihash))


;; ## Ref Schemas

#_
(defschema RefName
  (s/constrained s/Str (partial re-matches #"[a-zA-Z][a-zA-Z0-9-]*")))


#_
(defschema RefVersion
  {:name RefName
   :value (s/maybe Multihash)
   :version s/Int
   :time DateTime})


#_
(defschema RefHistory
  (s/constrained
    [RefVersion]
    #(every? (fn [[a b]]
               (and (pos? (compare (:version a) (:version b)))
                    (= (:name a) (:name b))))
             (partition 2 1 %))))


#_
(defschema RefsMap
  {RefName RefHistory})



;; ## Tracker Protocol

(defprotocol RefTracker
  "Protocol for a mutable tracker for reference pointers."

  (list-refs
    [tracker opts]
    "List all references in the tracker. Returns a sequence of `RefVersion`
    values. Opts may include:

    - `:include-nil` if true, refs with history but currently nil will be
      returned.")

  (get-ref
    [tracker ref-name]
    [tracker ref-name version]
    "Retrieve the given reference. If version is not given, the latest version
    is returned. Returns a `RefVersion`.")

  (get-history
    [tracker ref-name]
    "Returns a lazy sequence of the known versions of a ref.")

  (set-ref!
    [tracker ref-name value]
    "Stores the given multihash value for a reference, creating a new
    version. Returns the created `RefVersion`.")

  (delete-ref!
    [tracker ref-name]
    "Deletes all history for a reference from the tracker."))

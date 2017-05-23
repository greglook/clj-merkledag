(ns merkledag.node
  "Functions to serialize and operate on merkledag nodes."
  (:require
    [blocks.core]
    [clojure.future :refer [pos-int?]]
    [clojure.spec :as s]
    [merkledag.link :as link]
    [merkledag.store.core :as store]
    [multihash.core])
  (:import
    blocks.data.Block
    merkledag.link.MerkleLink
    multihash.core.Multihash))


(s/def ::id #(instance? Multihash %))
(s/def ::size pos-int?)
(s/def ::encoding (s/nilable (s/coll-of string? :kind vector?)))
(s/def ::links (s/coll-of link/merkle-link? :kind vector?))
(s/def ::data coll?)

(s/def :merkledag/node
  (s/keys :req-un [::id ::size ::encoding]
          :opt-un [::links ::data]))


(def node-keys
  [::id ::size ::encoding ::links ::data])



;; ## Identity Protocol

(defprotocol Identifiable
  "Protocol for values which can be resolved to a multihash value in order to
  uniquely identify a node in the graph."

  (identify
    [value]
    "Return the multihash identifying the value."))


(extend-protocol Identifiable

  nil
  (identify [_] nil)

  multihash.core.Multihash
  (identify [m] m)

  blocks.data.Block
  (identify [b] (:id b))

  merkledag.link.MerkleLink
  (identify [l] (:target l)))



;; ## Node Storage API

(defn get-node
  "Retrieve the identified node from the store. Returns a node map.

  The id may be any `Identifiable` value."
  [store id]
  (when-let [id (identify id)]
    (store/-get-node store id)))


(defn get-links
  "Retrieve the links for the identified node. Returns the node's link table as
  a vector of `MerkleLink` values.

  The id may be any `Identifiable` value."
  [store id]
  (let [id (identify id)]
    (when-let [node (and id (store/-get-node store id))]
      (some->
        (:links node)
        (vary-meta
          assoc
          ::id id
          ::size (::size node))))))


(defn get-data
  "Retrieve the data for the identified node. Returns the node's data body.

  The id may be any `Identifiable` value."
  [store id]
  (let [id (identify id)]
    (when-let [node (and id (store/-get-node store id))]
      (some->
        (:data node)
        (vary-meta
          assoc
          ::id id
          ::size (::size node)
          ::links (:links node))))))


(defn store-node!
  "Create a new node by serializing the links and data. Returns the stored node
  map, or nil if both links and data are nil."
  ([store data]
   (store-node! store nil data))
  ([store links data]
   (when (or links data)
     (if-let [id (or (::id (meta links)) (::id (meta data)))]
       ; See if we can re-use an already-stored node.
       (let [node (store/-get-node store id)]
         (if (and (= links (::links node))
                  (= data (::data node)))
           ; Links and data match, re-use stored node.
           node
           ; Missing or some value mismatch, store a new node.
           (store/-store-node! store links data)))
       ; No id metadata, store new node.
       (store/-store-node! store links data)))))


(defn delete-node!
  "Remove a node from the store.

  The id may be any `Identifiable` value."
  [store id]
  (when-let [id (identify id)]
    (store/-delete-node! store id)))



;; ## Utility Functions

(defn reachable-size
  "Calculates the total size of data reachable from the given node. Expects a
  node map with `::size` and `::links` entries.

  Raw blocks and nodes with no links have a total size equal to their `::size`.
  Each link in the node's link table adds its `::link/tsize` to the total.
  Returns `nil` if no node is given."
  [node]
  (when-let [size (::size node)]
    (->> (::links node)
         (keep ::link/tsize)
         (reduce + size))))

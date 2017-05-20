(ns merkledag.store.cache
  "Cache implementation which stores parsed node values, limited based on the
  size of the blocks cached."
  (:require
    [clojure.core.cache :as cache :refer [defcache]]
    [clojure.data.priority-map :refer [priority-map]]
    [merkledag.link :as link]
    [merkledag.node :as node]
    [multihash.digest :as digest]))


;; ## Cache Type

;; Nodes may be cached in memory to make frequently-accessed nodes quick to
;; fetch. In particular, this avoids re-parsing the same immutable node data on
;; each access.
;;
;; - `cache`
;;   Associative data structure mapping node multihash ids to node maps.
;; - `stats`
;;   Priority map from node ids to a vector of the tick and the node size.
;; - `tick`
;;   Clock value for the most recent operation, used to prioritize data in the
;;   cache.
;; - `total-size`
;;   Current total size in bytes of the blocks backing the nodes in the cache.
;; - `total-size-limit`
;;   Maximum total size of nodes to keep in the cache.
;; - `node-size-limit`
;;   Optional setting to restrict the size of individual cached nodes. If set,
;;   the cache will always miss nodes larger than the limit.
(defcache NodeCache
  [cache
   stats
   tick
   total-size
   total-size-limit
   node-size-limit])


(alter-meta! #'->NodeCache assoc :private true)


(defn node-cache
  "Create a new empty cache which will store nodes in memory up to the given
  total size limit given. Note that nodes don't actually take up the same
  amount of memory as their backing blocks, but it is a useful proxy for their
  data size.

  Options may include:

  - `:total-size-limit` limit the total size of nodes stored in the cache
  - `:node-size-limit` if set, prevent the caching of nodes larger than this"
  [base & {:as opts}]
  (->NodeCache
    base
    (into (priority-map)
          (map #(vector (first %) [0 (::node/size (second %))]))
          base)
    0
    (reduce + 0 (map ::node/size (vals base)))
    (:total-size-limit opts (* 32 1024 1024))
    (:node-size-limit opts)))



;; ## Cache Logic

(defn cacheable?
  "Determine whether the given node should be cached."
  [^NodeCache cache node]
  (and node
       (<= (::node/size node) (.total-size-limit cache))
       (or (nil? (.node-size-limit cache))
           (<= (::node/size node) (.node-size-limit cache)))))


(defn- touch
  "Updates a node in the cache to have the most recent tick value. If the cache
  does not contain the id, it is returned unchanged."
  [^NodeCache cache id]
  (let [tick+ (inc (.tick cache))]
    (->NodeCache
      (.cache cache)
      (if-let [node (get (.cache cache) id)]
        (assoc (.stats cache) id [tick+ (::node/size node)])
        (.stats cache))
      tick+
      (.total-size cache)
      (.total-size-limit cache)
      (.node-size-limit cache))))


(defn- cache-node
  "Updates the cache by inserting the given node. This function does not
  perform eviction logic."
  [^NodeCache cache node]
  (let [id (::node/id node)
        node-size (::node/size node)
        total-size+ (+ (.total-size cache) node-size)
        tick+ (inc (.tick cache))]
    (->NodeCache
      (assoc (.cache cache) id node)
      (assoc (.stats cache) id [tick+ node-size])
      tick+
      total-size+
      (.total-size-limit cache)
      (.node-size-limit cache))))


(defn- evict
  "Evict the identified node from the cache, if present."
  [^NodeCache cache id]
  (if (contains? (.cache cache) id)
    (->NodeCache
      (dissoc (.cache cache) id)
      (dissoc (.stats cache) id)
      (.tick cache)
      (- (.total-size cache) (second (get (.stats cache) id)))
      (.total-size-limit cache)
      (.node-size-limit cache))
    cache))


(defn- evict-least
  "Evict the lowest-priority node from the cache."
  [^NodeCache cache]
  (if-let [[id [tick size]] (peek (.stats cache))]
    (->NodeCache
      (dissoc (.cache cache) id)
      (pop (.stats cache))
      (.tick cache)
      (- (.total-size cache) size)
      (.total-size-limit cache)
      (.node-size-limit cache))
    cache))


(defn reap
  "Given a target amount of space to free and a node cache, evicts nodes from
  the cache to free up the desired amount of space. Returns the updated cache."
  [^NodeCache cache target-free]
  (->>
    (iterate evict-least cache)
    (drop-while
      (fn [^NodeCache cache]
        (and (< (- (.total-size-limit cache)
                   (.total-size cache))
                target-free)
             (seq (.stats cache)))))
    (first)))


(defn- maybe-cache?
  "Update the cache by including the given node and performing eviction logic
  to keep the cache under the configured limits. If the given node is not
  cacheable, the cache is returned unmodified."
  [^NodeCache cache node]
  (if (cacheable? cache node)
    (cache-node (reap cache (::node/size node)) node)
    cache))



;; ## Cache Protocol

(extend-type NodeCache

  cache/CacheProtocol

  (lookup
    [this id]
    (get (.cache this) id))


  (lookup
    [this id not-found]
    (get (.cache this) id not-found))


  (has?
    [this id]
    (contains? (.cache this) id))


  (hit
    [this id]
    (touch this id))


  (miss
    [this id node]
    (maybe-cache? this node))


  (evict
    [this id]
    (evict this id))


  (seed
    [this base]
    (node-cache
      base
      :total-size-limit (.total-size-limit this)
      :node-size-limit (.node-size-limit this))))

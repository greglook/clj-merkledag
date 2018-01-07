(ns merkledag.codec.cbor
  "Functions to handle structured data formatted as CBOR.

  Special types are handled by plugins which define three important attributes
  to support serializing a type to and from CBOR. A plugin map should contain:

  - `:reader` a function which converts a literal form into a value of this type.
  - `:writers` a map of classes or other interfaces to functions which return
    the literal form for a value of that type.
  - `:description` human-readable text about the type.

  The collection of data type plugins maps the symbol _tag_ for each type to
  the plugin for that type."
  (:require
    [clj-cbor.core :as cbor]
    [clj-cbor.data.core :as data]
    [multicodec.core :as codec :refer [defdecoder defencoder]])
  (:import
    clj_cbor.codec.CBORCodec
    (java.io
      DataOutputStream
      InputStream
      OutputStream)))


(defn- types->write-handlers
  "Converts a map of type definitions into a map of CBOR write handlers."
  [types]
  (into cbor/default-write-handlers
        (comp
          (map (juxt :cbor/tag (some-fn :cbor/writers :writers)))
          (map (fn [[tag writers]]
                 (map (fn [[cls former]]
                        [cls #(data/tagged-value tag (former %))])
                      writers)))
          cat)
        (vals types)))


(defn- types->read-handlers
  "Converts a map of type definitions into a map of CBOR read handlers."
  [types]
  (into cbor/default-read-handlers
        (map (juxt :cbor/tag (some-fn :cbor/reader :reader)))
        (vals types)))


(defencoder CBOREncoderStream
  [^DataOutputStream output
   codec]

  (write!
    [this value]
    (let [size (cbor/encode codec output value)]
      (.flush output)
      size)))


(defdecoder CBORDecoderStream
  [^InputStream input
   codec]

  (read!
    [this]
    (cbor/decode codec input)))


(extend-type CBORCodec

  codec/Codec

  (processable?
    [this header]
    (= header (:header this)))


  (encode-byte-stream
    [this selector output-stream]
    (codec/write-header! output-stream (:header this))
    (->CBOREncoderStream
      (DataOutputStream. ^OutputStream output-stream)
      this))


  (encode-value-stream
    [this selector encoder-stream]
    encoder-stream)


  (decode-byte-stream
    [this selector input-stream]
    (->CBORDecoderStream input-stream this))


  (decode-value-stream
    [this selector decoder-stream]
    decoder-stream))


(defn cbor-codec
  "Constructs a new CBOR codec."
  [types & {:as opts}]
  (cbor/cbor-codec
    :header (:cbor codec/headers)
    :write-handlers (types->write-handlers types)
    :read-handlers (types->read-handlers types)))

(defproject mvxcvi/merkledag-repo "0.1.0-SNAPSHOT"
  :description "Merkle-DAG object repository"
  :url "http://github.com/greglook/clj-mdag-repo"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :plugins
  [[lein-protobuf "0.4.3"]]

  :dependencies
  [[byte-streams "0.2.0"]
   [clj-time "0.11.0"]
   [mvxcvi/blobble "0.1.0-SNAPSHOT"]
   [mvxcvi/multihash "1.0.0"]
   [mvxcvi/puget "1.0.0"]
   [org.clojure/clojure "1.7.0"]
   [org.clojure/tools.logging "0.3.1"]
   [org.flatland/protobuf "0.8.1"]]

  :hiera {:path "target/ns-hiera.png"
          :vertical false
          :cluster-depth 2
          :ignore-ns #{clj-time}}

  :profiles
  {:repl {:source-paths ["dev"]
          :dependencies [[org.clojure/tools.namespace "0.2.10"]]}})

(ns clojurewerkz.titanium.graph-test
  (:require [clojurewerkz.titanium.graph    :as tg]
            [clojurewerkz.titanium.elements :as te]
            [clojurewerkz.titanium.edges    :as ted]
            [clojurewerkz.support.io        :as sio])
  (:use clojure.test)
  (:import java.io.File
           [java.util.concurrent CountDownLatch TimeUnit]))


(deftest test-open-and-close-a-ram-graph
  (let [g (tg/open-in-memory-graph)]
    (is (tg/open? g))
    (tg/close g)))

(deftest test-open-and-close-a-local-graph-with-a-directory-path
  (let [p (sio/create-temp-dir)
        d (do (.deleteOnExit p)
              p)
        g (tg/open d)]
    (is (tg/open? g))
    (tg/close g)))

(deftest test-open-and-close-a-local-graph-with-a-configuration-map
  (let [p (sio/create-temp-dir)
        d (do (.deleteOnExit p)
              (.getPath p))
        g (tg/open {"storage.directory" d
                    "storage.backend"   "berkeleyje"})]
    (is (tg/open? g))
    (tg/close g)))



;;
;; Vertices
;;

(deftest test-adding-a-vertex
  (let [g (tg/open-in-memory-graph)
        v (tg/add-vertex g {:name "Titanium" :language "Clojure"})]
    (is (.getId v))
    (is (= "Titanium" (.getProperty v "name")))))

(deftest test-getting-property-names
  (let [g  (tg/open-in-memory-graph)
        v  (tg/add-vertex g {:station "Boston Manor" :lines #{"Piccadilly"}})
        xs (te/property-names v)]
    (is (= #{"station" "lines"} xs))))

(deftest test-getting-properties-map
  (let [g  (tg/open-in-memory-graph)
        m  {"station" "Boston Manor" "lines" #{"Piccadilly"}}
        v  (tg/add-vertex g m)
        m' (te/properties-of v)]
    (is (= m m'))))

(deftest test-getting-vertex-id
  (let [g  (tg/open-in-memory-graph)
        m  {"station" "Boston Manor" "lines" #{"Piccadilly"}}
        v  (tg/add-vertex g m)]
    (is (te/id-of v))))

(deftest test-associng-properties-map
  (let [g  (tg/open-in-memory-graph)
        m  {"station" "Boston Manor" "lines" #{"Piccadilly"}}
        v  (tg/add-vertex g m)
        _  (te/assoc! v "opened-in" 1883 "has-wifi?" false)
        m' (te/properties-of v)]
    (is (= (assoc m "opened-in" 1883 "has-wifi?" false) m'))))

(deftest test-dissocing-properties-map
  (let [g  (tg/open-in-memory-graph)
        m  {"station" "Boston Manor" "lines" #{"Piccadilly"}}
        v  (tg/add-vertex g m)
        _  (te/dissoc! v "lines")
        m' (te/properties-of v)]
    (is (= {"station" "Boston Manor"} m'))))

(deftest test-adding-vertices-with-the-same-id-twice
  (let [g   (tg/open-in-memory-graph)
        m   {"station" "Boston Manor" "lines" #{"Piccadilly"}}
        v1  (tg/add-vertex g 50 m)
        v2  (tg/add-vertex g 50 m)]
    ;; Titan seems to be ignoring provided ids, which the Blueprints API
    ;; implementations are allowed to ignore according to the docs. MK.
    (is (not (= (te/id-of v1) (te/id-of v2))))))

(deftest test-get-all-vertices
  (let [g  (tg/open-in-memory-graph)
        m1 {:age 28 :name "Michael"}
        m2 {:age 26 :name "Alex"}
        v1 (tg/add-vertex g m1)
        v2 (tg/add-vertex g m2)
        xs (set (tg/get-vertices g))]
    (is (= #{v1 v2} xs))))

(deftest test-get-vertices-by-kv
  (let [g  (tg/open-in-memory-graph)
        m1 {:age 28 :name "Michael"}
        m2 {:age 26 :name "Alex"}
        v1 (tg/add-vertex g m1)
        v2 (tg/add-vertex g m2)
        xs (set (tg/get-vertices g :name "Michael"))]
    (is (= #{v1} xs))))

;;
;; Edges
;;

(deftest test-getting-edge-head-and-tail
  (let [g  (tg/open-in-memory-graph)
        m1 {"station" "Boston Manor" "lines" #{"Piccadilly"}}
        m2 {"station" "Northfields"  "lines" #{"Piccadilly"}}
        v1 (tg/add-vertex g m1)
        v2 (tg/add-vertex g m2)
        e  (tg/add-edge g v1 v2 "links")]
    (is (= "links" (ted/label-of e)))
    (is (= v2 (ted/head-vertex e)))
    (is (= v1 (ted/tail-vertex e)))))

#_ (deftest test-getting-edge-head-and-tail-via-fancy-macro
     (let [g  (tg/open-in-memory-graph)
           m1 {"station" "Boston Manor" "lines" #{"Piccadilly"}}
           m2 {"station" "Northfields"  "lines" #{"Piccadilly"}}]
       (tg/populate g
                    (m1 -links-> m2))))


;;
;; Transactions
;;

(deftest test-very-basic-transaction-that-is-committed
  (let [g   (tg/open-in-memory-graph)
        v1  (tg/add-vertex g {})
        v2  (tg/add-vertex g {})]
    (tg/commit-tx! g)
    (tg/close g)))

(deftest test-very-basic-transaction-that-is-rolled-back
  (let [g   (tg/open (System/getProperty "java.io.tmpdir"))
        v1  (tg/add-vertex g {})
        v2  (tg/add-vertex g {})]
    (tg/rollback-tx! g)
    (tg/close g)))

(deftest test-supports-transaction
  (let [d   (let [p (sio/create-temp-dir)]
              (.deleteOnExit p)
              p)
        g   (tg/open d)
        mg  (tg/open-in-memory-graph)]
    (is (tg/supports-transactions? g))
    (is (not (tg/supports-transactions? mg)))
    (tg/close g)
    (tg/close mg)))

(deftest test-explicitly-started-single-threaded-transaction
  (let [d   (let [p (sio/create-temp-dir)]
              (.deleteOnExit p)
              p)
        g   (tg/open d)
        tx  (tg/start-tx g)]
    (tg/add-vertex tx {})
    (tg/commit-tx! tx)
    (tg/close g)))

(deftest test-run-transactionally-with-just-one-thread
  (let [d   (let [p (sio/create-temp-dir)]
              (.deleteOnExit p)
              p)
        g   (tg/open d)]
    (tg/run-transactionally g (fn [tx]
                                (tg/add-vertex tx {})))
    (tg/close g)))

(deftest test-run-transactionally-with-multiple-threads
  (let [d   (let [p (sio/create-temp-dir)]
              (.deleteOnExit p)
              p)
        g   (tg/open d)
        n   10
        l   (CountDownLatch. n)]
    (tg/run-transactionally g (fn [tx]
                                (dotimes [i n]
                                  (.start (Thread. (fn []
                                                     (tg/add-vertex tx {})
                                                     (.countDown l)))))
                                (.await l 2 TimeUnit/SECONDS)))
    (tg/close g)))

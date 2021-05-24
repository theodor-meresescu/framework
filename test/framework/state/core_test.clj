(ns framework.state.core-test
  (:require
   [xiana.core :as xiana]
   [clojure.test :refer :all]
   [framework.state.core :as state]))

(def state-initial-map
  {:deps     {:auth nil}
   :request  {}
   :response {}})

;; test empty state creation
(deftest initial-state
  (let [result (state/make {})
        expected (xiana/map->State state-initial-map)]
    ;; verify if the response and expected value are equal
    (is (= result expected))))

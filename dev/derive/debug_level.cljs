(ns derive.debug-level
  (:require [derive.tools :as tools]))

(defn set-level!
  ([] (set-level! 2))
  ([lvl] (tools/set-debug-level! lvl)))

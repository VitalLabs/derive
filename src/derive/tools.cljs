(ns derive.tools)

(def debug-level (atom nil))

(defn set-debug-level! [lvl]
  (declare info)
  (reset! debug-level lvl)
  (info "(tools/set-debug-level)" lvl))

(defn echo [& args]
  (.log js/console (pr-str-with-opts args (assoc (pr-opts) :readably false))))

(defn print-at-level [level args]
(when (>= @debug-level level)
  (apply echo args)))

(defn log [& args]
  (print-at-level 3 args))

(defn info [& args]
  (print-at-level 2 args))

(defn warn [& args]
  (print-at-level 1 args))

(defn error [& args]
  (print-at-level 0 args))

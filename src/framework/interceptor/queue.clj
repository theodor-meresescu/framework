(ns framework.interceptor.queue
  "Interceptor executor.
  Collects and executes interceptors and the given action in between.")

(defn- -concat
  "Concatenate routes interceptors with the defaults ones,
  or override it if its type isn't a map."
  [{except-interceptors :except
    around-interceptors :around
    inside-interceptors :inside
    :as                 interceptors}
   default-interceptors]
  (if (map? interceptors)
    ;; get around/inside interceptors
    (remove (into #{} except-interceptors)
            (concat around-interceptors
                    default-interceptors
                    inside-interceptors))
    ;; else override
    (or interceptors default-interceptors)))

(defn action->try
  [action]
  (fn [state]
    (try (action state)
         (catch Exception e
           (assoc state
                  :response
                  (or (ex-data e)
                      {:status 500 :body (Throwable->map e)}))))))

(defn looper
  ([state interceptors action]
   (looper state interceptors '() action :enter))
  ([state interceptors backwards action direction]
   (cond
     (seq interceptors)
     (try (looper ((get (first interceptors) direction identity) state)
                  (rest interceptors)
                  (when (= :enter direction) (conj backwards (first interceptors)))
                  action
                  direction)
          (catch Exception e
            (looper (assoc state
                           :exception e
                           :response (or (ex-data e) {:status 500 :body (Throwable->map e)}))
                    (conj backwards (first interceptors))
                    '()
                    action
                    :error)))
     (= :enter direction)
     (looper (action state)
             backwards
             '()
             identity
             :leave)
     :else state)))

(defn execute
  "Execute the interceptors queue and invoke the
  action procedure between its enter-leave stacks."
  [state default-interceptors]
  (let [interceptors (-concat
                       (get-in state [:request-data :interceptors])
                       default-interceptors)
        action (action->try (get-in state [:request-data :action] identity))]
    ;; execute the interceptors queue calling the action
    ;; between its enter/leave stacks
    (looper state interceptors action)))


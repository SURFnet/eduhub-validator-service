(ns nl.surf.eduhub.validator.service.redis-monitor
  (:require [taoensso.carmine :as car]
            [clojure.tools.logging :as log]
            [nl.jomco.resources :refer [closeable]]))

(defn run-with-retries
  [attempts f]
  ;; a bit messy, since you can't `recur` from a `catch`
  (let [result (try (f)
                    (catch Exception e
                      e))]
    (if (instance? Exception result)
      ;; we call `.interrupt` to halt the monitor thread, so when an
      ;; InterruptedException is caught, we should not retry
      (if (and (not (instance? InterruptedException result))
               (> attempts 1))
        (do (Thread/sleep 1000)
            (recur (dec attempts) f))
        (throw result))
      result)))

(defn check-redis-connection
  [{:keys [redis-conn] :as _config}]
  (log/debug "Testing redis connection")
  (let [response (car/wcar redis-conn (car/ping))]
    (when-not (= "PONG" response)
      (throw (ex-info "Unexpected PING response from redis"
                      {:reponse response})))))

(defn halt-redis-monitor
  [{:keys [thread]}]
  (.interrupt thread)
  (.join thread))

(defn run-redis-monitor
  [config error-callback]
  (let [monitor (fn monitor []
                  (try
                    (loop []
                      (run-with-retries 60 #(check-redis-connection config))
                      (Thread/sleep 60000)
                      (recur))
                    (catch InterruptedException ie
                      ie)
                    (catch Exception e
                      (log/error e "Redis connection failed")
                      (error-callback)
                      e)))
        thread (Thread. monitor)]
    (.setName thread "redis-connection-monitor")
    (.setDaemon thread true)
    (.run thread)
    (closeable {:thread thread} halt-redis-monitor)))

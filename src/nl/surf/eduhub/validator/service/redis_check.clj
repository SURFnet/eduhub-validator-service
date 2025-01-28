(ns nl.surf.eduhub.validator.service.redis-check
  (:require [taoensso.carmine :as car]
            [clojure.tools.logging :as log]))

(defn check-redis-connection
  [{:keys [redis-conn] :as _config}]
  (log/debug "Testing redis connection")
  (let [response (car/wcar redis-conn (car/ping))]
    (when-not (= "PONG" response)
      (throw (ex-info "Unexpected PING response from redis"
                      {:reponse response})))))

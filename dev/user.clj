(ns user
  (:require [nl.surf.eduhub.validator.service.main :as main]
            [nl.surf.eduhub.validator.service.config :as config]
            [nl.jomco.resources :refer [defresource close]]
            [nl.surf.eduhub.validator.service.jobs.client :as client]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [goose.client :as gc])
  (:import (org.slf4j LoggerFactory)
           (ch.qos.logback.classic Level)))

(log/info "Loading dev/user.clj, setting application log level to DEBUG")

;; The provided logback.xml sets the root log level to INFO, and does
;; not set log levels for any namespace.
;;
;; We change the log level for application-specific namespaces to
;; DEBUG when when dev/user.clj is loaded

(.setLevel (LoggerFactory/getLogger "nl.surf.eduhub.validator") Level/DEBUG)


(def config
  (let [[c err] (config/load-config-from-env env)]
    (when err (prn err))
    c))

(def broker
  (:broker (:goose-client-opts config)))

(defresource system)

(defn start!
  []
  (defresource system (main/run-system config)))

(defn stop!
  []
  (close system))


(comment
  (start!)

  (client/enqueue-validation "demo04.test.surfeduhub.nl" "rio" config))

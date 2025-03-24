;; SPDX-FileCopyrightText: 2024, 2025 SURF B.V.
;; SPDX-License-Identifier: AGPL-3.0-or-later
;; SPDX-FileContributor: Joost Diepenmaat
;; SPDX-FileContributor: Michiel de Mare
;; SPDX-FileContributor: Remco van 't Veer

(ns nl.surf.eduhub.validator.service.config-test
  (:require [clojure.test :refer [deftest is]]
            [nl.surf.eduhub.validator.service.config :as config]
            [goose.brokers.redis.broker :as broker])
  (:import [clojure.lang ExceptionInfo]
           [java.io File]))

(def default-env {:allowed-client-ids                 "default",
                  :gateway-basic-auth-user            "default",
                  :gateway-basic-auth-pass            "default",
                  :gateway-url                        "https://gateway.test.surfeduhub.nl/",
                  :max-total-requests                 "5",
                  :ooapi-version                      "default",
                  :redis-uri                          "redis://example.com"
                  :surf-conext-client-id              "default",
                  :surf-conext-client-secret          "default",
                  :surf-conext-introspection-endpoint "default"
                  :server-port                        "3002"
                  :validator-service-root-url         "http://localhost:3002"
                  :spider-timeout-millis              "3600000"})

(def default-expected-value
  {:allowed-client-ids         "default",
   :gateway-url                "https://gateway.test.surfeduhub.nl/",
   :ooapi-version              "default",
   :gateway-basic-auth         {:pass "default", :user "john200"},
   :introspection-basic-auth   {:pass "default", :user "default"},
   :introspection-endpoint-url "default"
   :max-total-requests         5,
   :server-port                3002
   :redis-conn                 {:spec {:uri "redis://example.com"}}
   :expiry-seconds             1209600
   :root-url                   "http://localhost:3002"
   :spider-timeout-millis      3600000
   :goose-worker-opts          {:threads               5,
                                :queue                 "default",
                                :graceful-shutdown-sec 30,
                                :broker
                                (broker/map->Redis
                                 {:redis-conn nil,
                                  :opts
                                  {:url "redis://example.com", :scheduler-polling-interval-sec 5}})},
   :goose-client-opts          {:queue "default",
                                :retry-opts
                                {:max-retries            27,
                                 :retry-delay-sec-fn-sym `goose.retry/default-retry-delay-sec,
                                 :retry-queue            nil,
                                 :error-handler-fn-sym
                                 `nl.surf.eduhub.validator.service.jobs.client/job-error-handler,
                                 :death-handler-fn-sym   `goose.retry/default-death-handler,
                                 :skip-dead-queue        false},
                                :broker
                                (broker/map->Redis {:redis-conn
                                                    {:spec {:uri "redis://example.com"},
                                                     :pool
                                                     {:max-total-per-key 5, :max-idle-per-key 5, :min-idle-per-key 1}},
                                                    :opts nil})}})

(defn- test-env [env]
  (-> default-env
      (dissoc :gateway-basic-auth-user)
      (merge env)
      config/load-config-from-env))

(deftest missing-secret
  (is (= {:gateway-basic-auth-user "missing"}
         (last (test-env {})))))

(deftest only-value-secret
  (let [env {:gateway-basic-auth-user "john200"}]
    (is (= [default-expected-value]
           (test-env env)))))

(deftest only-file-secret
  (let [path (.getAbsolutePath (File/createTempFile "test-secret" ".txt"))
        env {:gateway-basic-auth-user-file path}]
    (spit path "john200")
    (is (= [default-expected-value]
           (test-env env)))))

(deftest only-file-secret-file-missing
  (let [env {:gateway-basic-auth-user-file "missing-file"}]
    (is (thrown? ExceptionInfo (test-env env)))))

(deftest both-types-of-secret-specified
    (let [path (.getAbsolutePath (File/createTempFile "test-secret" ".txt"))
          env {:gateway-basic-auth-user "john200"
               :gateway-basic-auth-user-file path}]
      (spit path "john201")
      (is (= [default-expected-value]
             (test-env env)))))

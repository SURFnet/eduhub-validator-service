;; SPDX-FileCopyrightText: 2024, 2025 SURF B.V.
;; SPDX-License-Identifier: AGPL-3.0-or-later
;; SPDX-FileContributor: Joost Diepenmaat
;; SPDX-FileContributor: Michiel de Mare
;; SPDX-FileContributor: Remco van 't Veer

(ns nl.surf.eduhub.validator.service.checker-test
  (:require [babashka.http-client :as http]
            [babashka.json :as json]
            [nl.jomco.http-status-codes :as status]
            [nl.surf.eduhub.validator.service.checker :as checker]
            [clojure.test :refer [deftest is]]))


(defn- result [endpoint-id gateway-response]
  (with-redefs [http/request (fn [_]
                               (update gateway-response
                                       :body json/write-str))]
    (checker/check-endpoint endpoint-id {:gateway-url "http://localhost"})))

(deftest test-validate-correct
  (is (= {:status status/ok :body {:valid true}}
         (result "google.com"
                 {:status status/ok
                  :body   {:gateway {:endpoints {:google.com {:responseCode status/ok}}}}}))))

(deftest test-validate-failed-endpoint
  (is (= {:status status/ok
          :body  {:valid false
                  :message "Endpoint validation failed with status: 500"}}
         (result
          "google.com"
          {:status status/ok
           :body   {:gateway {:endpoints {:google.com {:responseCode status/internal-server-error}}}}}))))

(deftest test-unexpected-gateway-status
  (is (= {:status status/internal-server-error :body {}}
         (result "google.com"
                 {:status status/internal-server-error :body {:message "mocked response"}}))))

(deftest test-validate-fails
  (is (= {:status status/internal-server-error :body {}}
         (result "google.com"
                 {:status status/unauthorized :body "mocked response"}))))

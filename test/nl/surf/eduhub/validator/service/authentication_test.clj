;; This file is part of eduhub-validator-service
;;
;; Copyright (C) 2024 SURFnet B.V.
;;
;; This program is free software: you can redistribute it and/or
;; modify it under the terms of the GNU Affero General Public License
;; as published by the Free Software Foundation, either version 3 of
;; the License, or (at your option) any later version.
;;
;; This program is distributed in the hope that it will be useful, but
;; WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; Affero General Public License for more details.
;;
;; You should have received a copy of the GNU Affero General Public
;; License along with this program.  If not, see
;; <https://www.gnu.org/licenses/>.

(ns nl.surf.eduhub.validator.service.authentication-test
  (:require [babashka.http-client :as http]
            [clojure.test :refer [deftest is]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.surf.eduhub.validator.service.authentication :as authentication]
            [nl.surf.eduhub.validator.service.test-helper :as test-helper]))

(deftest test-bearer-token
  (is (nil?
    (authentication/bearer-token {:headers {"authorization" "Bearerfoobar"}})))
  (is (= "foobar"
    (authentication/bearer-token {:headers {"authorization" "Bearer foobar"}}))))

;; Copy client-id to body so we know the middleware works
(defn- wrap-observe-client-id [app]
  (fn [req]
    (let [{:keys [client-id] :as resp} (app req)]
      (cond-> (dissoc resp :client-id)
              client-id (assoc :body {:client client-id})))))

(defn- make-handler [introspection-endpoint basic-auth allowed-client-id-set]
  (-> (fn auth-handler [_req]
        {:status http-status/ok
         :body   {}})
      (authentication/wrap-authentication introspection-endpoint basic-auth allowed-client-id-set)
      wrap-observe-client-id))

(deftest token-validator
  ;; This binds the *dynamic* http client in clj-http.client
  (with-redefs [http/request test-helper/mock-introspection]
     (let [introspection-endpoint "https://example.com"
          basic-auth {:user "foo" :pass "bar"}]
      (reset! test-helper/mock-introspection-count-calls 0)
      (let [handler (make-handler introspection-endpoint basic-auth #{test-helper/allowed-client-id})]
        (is (= {:status    http-status/ok
                :body      {}}
               (handler {:headers {"authorization" (str "Bearer " test-helper/valid-token)}}))
            "Ok when valid token provided")

        (is (= {:status 403, :body "No client-id found"}
               (handler {}))
            "Not authorized when no token provided")

        (is (= http-status/forbidden
               (:status (handler {:headers {"authorization" "Bearer invalid-token"}})))
            "Forbidden with invalid token")

        (is (= 2 @test-helper/mock-introspection-count-calls)
            "Correct number of calls to introspection-endpoint")

        (reset! test-helper/mock-introspection-count-calls 0)
        (is (= {:status    http-status/ok
                :body      {}}
               (handler {:headers {"authorization" (str "Bearer " test-helper/valid-token)}}))
            "CACHED: Ok when valid token provided")

        (is (= 0 @test-helper/mock-introspection-count-calls)
            "No more calls to introspection-endpoint"))

      (let [handler (make-handler introspection-endpoint basic-auth #{"wrong_client_id"})]
        (reset! test-helper/mock-introspection-count-calls 0)
        (is (= {:status http-status/forbidden
                :body   "Unknown client id"}
               (handler {:headers {"authorization" (str "Bearer " test-helper/valid-token)}}))
            "Forbidden when valid token provided but client id is unknown")

        (is (= {:status 403, :body "No client-id found"}
               (handler {}))
            "Not authorized when no token provided")

        (is (= http-status/forbidden
               (:status (handler {:headers {"authorization" "Bearer invalid-token"}})))
            "Forbidden with invalid token")

        (is (= 2 @test-helper/mock-introspection-count-calls)
            "Correct number of calls to introspection-endpoint")

        (reset! test-helper/mock-introspection-count-calls 0)
        (is (= {:status    http-status/forbidden
                :body      "Unknown client id"}
               (handler {:headers {"authorization" (str "Bearer " test-helper/valid-token)}}))
            "CACHED: Forbidden when valid token provided but client id is unknown")

        (is (= 0 @test-helper/mock-introspection-count-calls)
            "No more calls to introspection-endpoint")))))

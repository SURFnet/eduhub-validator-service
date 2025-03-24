;; SPDX-FileCopyrightText: 2024, 2025 SURF B.V.
;; SPDX-License-Identifier: AGPL-3.0-or-later
;; SPDX-FileContributor: Joost Diepenmaat
;; SPDX-FileContributor: Michiel de Mare
;; SPDX-FileContributor: Remco van 't Veer

(ns nl.surf.eduhub.validator.service.authentication-test
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.surf.eduhub.validator.service.authentication :as authentication]))

(deftest test-bearer-token
  (is (nil?
    (authentication/bearer-token {:headers {"authorization" "Bearerfoobar"}})))
  (is (= "foobar"
    (authentication/bearer-token {:headers {"authorization" "Bearer foobar"}}))))

(def valid-token
  (str "eyJraWQiOiJrZXlfMjAyMl8xMF8wNF8wMF8wMF8wMF8wMDEiLCJ0eXAiOiJK"
       "V1QiLCJhbGciOiJSUzI1NiJ9.eyJhdWQiOiJwbGF5Z3JvdW5kX2NsaWVudCI"
       "sInN1YiI6InBsYXlncm91bmRfY2xpZW50IiwibmJmIjoxNjY0ODkyNTg0LCJ"
       "pc3MiOiJodHRwczpcL1wvY29ubmVjdC50ZXN0LnN1cmZjb25leHQubmwiLCJ"
       "leHAiOjE2NjU3NTY1ODQsImlhdCI6MTY2NDg5MjU4NCwianRpIjoiNTlkNGY"
       "yZDQtZmRhOC00MTBjLWE2MzItY2QzMzllMTliNTQ2In0.nkQqZK02SamkNI2"
       "ICDrE1LxN6kBBDOwQd5zU9BsPxNIfOwP1qnCwNQELo5xX0R2cJJJqCgmq8nw"
       "BjZ4xNba4lTS8dii4Fmy-8u7fN427mx-_G-GoCGQSKQD6OdVKjDsRMJX4rHN"
       "DSg5HhtDz5or-2Xp_H0Vi0mWMOBgQGjfbjLjJJZ1T0rlaZbq-_ZAatb2dFcr"
       "WliqbFrous_fSPo4jrbPVHYunF-wZZoLZFlOaCyJM24A_3Mrv4JPw78WRnyu"
       "ZG0H7aS2v_KLe5Xh2lUkSa0lkO_xP2uhQQ_69bnmF0RQiKe9vVDi7mhi0aGE"
       "do2f-iJ8JQj4EwPzZkSvdJt569w"))

(def count-calls (atom 0))

;; Mock out the introspection endpoint. Pretend token is active if
;; it's equal to `valid-token`, invalid otherwise.
(defn mock-introspection
  [{:keys [form-params]}]
  (swap! count-calls inc)
  (let [valid? (= valid-token (:token form-params))]
    {:status http-status/ok
     :body   (json/encode
               (cond-> {:active valid?}
                       valid?
                       (assoc :client_id "institution_client_id")))}))

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
      (authentication/wrap-authentication introspection-endpoint basic-auth allowed-client-id-set {:auth-disabled false})
      wrap-observe-client-id))

(deftest token-validator
  ;; This binds the *dynamic* http client in clj-http.client
  (with-redefs [http/request mock-introspection]
    (let [introspection-endpoint "https://example.com"
          basic-auth {:user "foo" :pass "bar"}]
      (reset! count-calls 0)
      (let [handler (make-handler introspection-endpoint basic-auth #{"institution_client_id"})]
        (is (= {:status    http-status/ok
                :body      {}}
               (handler {:headers {"authorization" (str "Bearer " valid-token)}}))
            "Ok when valid token provided")

        (is (= {:status 403, :body "No client-id found"}
               (handler {}))
            "Not authorized when no token provided")

        (is (= http-status/forbidden
               (:status (handler {:headers {"authorization" "Bearer invalid-token"}})))
            "Forbidden with invalid token")

        (is (= 2 @count-calls)
            "Correct number of calls to introspection-endpoint")

        (reset! count-calls 0)
        (is (= {:status    http-status/ok
                :body      {}}
               (handler {:headers {"authorization" (str "Bearer " valid-token)}}))
            "CACHED: Ok when valid token provided")

        (is (= 0 @count-calls)
            "No more calls to introspection-endpoint"))

      (let [handler (make-handler introspection-endpoint basic-auth #{"wrong_client_id"})]
        (reset! count-calls 0)
        (is (= {:status http-status/forbidden
                :body   "Unknown client id"}
               (handler {:headers {"authorization" (str "Bearer " valid-token)}}))
            "Forbidden when valid token provided but client id is unknown")

        (is (= {:status 403, :body "No client-id found"}
               (handler {}))
            "Not authorized when no token provided")

        (is (= http-status/forbidden
               (:status (handler {:headers {"authorization" "Bearer invalid-token"}})))
            "Forbidden with invalid token")

        (is (= 2 @count-calls)
            "Correct number of calls to introspection-endpoint")

        (reset! count-calls 0)
        (is (= {:status    http-status/forbidden
                :body      "Unknown client id"}
               (handler {:headers {"authorization" (str "Bearer " valid-token)}}))
            "CACHED: Forbidden when valid token provided but client id is unknown")

        (is (= 0 @count-calls)
            "No more calls to introspection-endpoint")))))

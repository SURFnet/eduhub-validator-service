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

(ns nl.surf.eduhub.validator.service.checker-test
  (:require [babashka.http-client :as http]
            [babashka.json :as json]
            [clojure.test :refer [deftest is]]
            [environ.core :refer [env]]
            [nl.surf.eduhub.validator.service.api :as api]
            [nl.surf.eduhub.validator.service.config :as config]
            [nl.surf.eduhub.validator.service.config-test :as config-test]))

(def test-config
  (first (config/load-config-from-env (merge config-test/default-env env))))

(def app (api/compose-app test-config :auth-disabled))

(defn- response-match [actual req]
  (is (= actual
         (-> (app req)
             (select-keys [:status :body])                    ; don't test headers
             (update :body json/read-str)))))                 ; json easier to test after parsing

(deftest test-validate-correct
         (with-redefs [http/request (fn [_] {:status 200
                                             :body   (json/write-str (assoc-in {} [:gateway :endpoints :google.com :responseCode] 200))})]
           (response-match {:status 200 :body {:valid true}}
                           {:uri "/endpoints/google.com/config" :request-method :post})))

(deftest test-validate-failed-endpoint
         (with-redefs [http/request (fn [_] {:status 200
                                             :body   (json/write-str (assoc-in {} [:gateway :endpoints :google.com :responseCode] 500))})]
           (response-match {:status 200
                             :body  {:valid false :message "Endpoint validation failed with status: 500"}}
                           {:uri "/endpoints/google.com/config" :request-method :post})))

(deftest test-unexpected-gateway-status
         (with-redefs [http/request (fn [_] {:status 500 :body {:message "mocked response"}})]
           (response-match {:status 500 :body {}}
                           {:uri "/endpoints/google.com/config" :request-method :post})))

(deftest test-validate-fails
         (with-redefs [http/request (fn [_] {:status 401 :body "mocked response"})]
           (response-match {:status 500 :body {}}
                           {:uri "/endpoints/google.com/config" :request-method :post})))

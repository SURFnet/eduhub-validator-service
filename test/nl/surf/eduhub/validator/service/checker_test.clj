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
            [nl.jomco.http-status-codes :as status]
            [nl.surf.eduhub.validator.service.checker :as checker]
            [nl.surf.eduhub.validator.service.validate :as validate]
            [clojure.test :refer [deftest is]]))


(defn- result [endpoint-id gateway-response]
  (with-redefs [http/request (fn [_]
                               (update gateway-response
                                       :body json/write-str))]
    (checker/check-endpoint endpoint-id nil {:gateway-url "http://localhost"})))

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

(deftest check-endpoint-forwards-path
  (let [captured (atom nil)
        endpoint-id "google.com"]
    (with-redefs [validate/check-endpoint (fn [eid path _config]
                                            (reset! captured {:endpoint eid :path path})
                                            {:status status/ok
                                             :body   (json/write-str {:gateway {:endpoints {(keyword eid) {:responseCode status/ok}}}})})]
      (checker/check-endpoint endpoint-id "/programs" {:gateway-url "http://localhost"})
      (is (= {:endpoint endpoint-id :path "/programs"} @captured)))))

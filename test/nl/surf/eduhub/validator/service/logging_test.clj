;; This file is part of eduhub-validator-service
;;
;; Copyright (C) 2025 SURFnet B.V.
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

(ns nl.surf.eduhub.validator.service.logging-test
  (:require [clojure.test :refer [deftest is testing]]
            [nl.surf.eduhub.validator.service.logging :as logging]))

(deftest redact-sensitive-test
  (testing "Redacts password from top-level map"
    (is (= {:password "XXX-REDACTED"}
           (logging/redact-sensitive {:password "secret123"}))))

  (testing "Redacts pass from top-level map"
    (is (= {:pass "XXX-REDACTED"}
           (logging/redact-sensitive {:pass "secret123"}))))

  (testing "Redacts token from top-level map"
    (is (= {:proxy-options "XXX-REDACTED"}
           (logging/redact-sensitive {:proxy-options "abc123"}))))

  (testing "Case-insensitive redaction"
    (is (= {:Password "XXX-REDACTED"}
           (logging/redact-sensitive {:Password "secret123"})))
    (is (= {:PASS "XXX-REDACTED"}
           (logging/redact-sensitive {:PASS "secret123"}))))

  (testing "Redacts nested passwords in maps"
    (is (= {:basic-auth {:user "admin" :pass "XXX-REDACTED"}}
           (logging/redact-sensitive {:basic-auth {:user "admin" :pass "secret123"}}))))

  (testing "Redacts deeply nested credentials"
    (is (= {:config {:auth {:credentials {:password "XXX-REDACTED"}}}}
           (logging/redact-sensitive {:config {:auth {:credentials {:password "secret123"}}}}))))

  (testing "Redacts credentials in vectors"
    (is (= [{:password "XXX-REDACTED"} {:pass "XXX-REDACTED"}]
           (logging/redact-sensitive [{:password "secret1"} {:pass "secret2"}]))))

  (testing "Redacts credentials in nested vectors"
    (is (= {:users [{:password "XXX-REDACTED"} {:pass "XXX-REDACTED"}]}
           (logging/redact-sensitive {:users [{:password "secret1"} {:pass "secret2"}]}))))

  (testing "Preserves non-sensitive data"
    (is (= {:user "admin" :url "https://example.com"}
           (logging/redact-sensitive {:user "admin" :url "https://example.com"}))))

  (testing "Redacts client id"
    (is (= {:headers {"client-id" "XXX-REDACTED"}}
           (logging/redact-sensitive {:headers {"client-id" "client-X"}}))))

  (testing "Handles mixed sensitive and non-sensitive data"
    (is (= {:user "admin" :password "XXX-REDACTED" :status 200}
           (logging/redact-sensitive {:user "admin" :password "secret" :status 200}))))

  (testing "Real-world HTTP request with basic-auth"
    (let [http-opts {:headers    {"x-route"             "endpoint=test.example.com"
                                  "accept"              "application/json; version=5"
                                  "x-envelope-response" "true"}
                     :basic-auth {:pass "super-secret-password-123"
                                  :user "surf646993"}
                     :throw      false}
          redacted (logging/redact-sensitive http-opts)]
      (is (= "XXX-REDACTED" (get-in redacted [:basic-auth :pass])))
      (is (= "surf646993" (get-in redacted [:basic-auth :user])))
      (is (= {"x-route" "endpoint=test.example.com"
              "accept" "application/json; version=5"
              "x-envelope-response" "true"}
             (:headers redacted))))))

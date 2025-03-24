;; SPDX-FileCopyrightText: 2024, 2025 SURF B.V.
;; SPDX-License-Identifier: AGPL-3.0-or-later
;; SPDX-FileContributor: Joost Diepenmaat
;; SPDX-FileContributor: Michiel de Mare
;; SPDX-FileContributor: Remco van 't Veer

(ns nl.surf.eduhub.validator.service.jobs.client-test
  (:require [babashka.http-client :as http]
            [babashka.json :as json]
            [clojure.test :refer [deftest is testing]]
            [goose.client :as c]
            [nl.jomco.http-status-codes :as http-status]
            [nl.surf.eduhub.validator.service.jobs.status :as status]
            [nl.surf.eduhub.validator.service.jobs.worker :as worker]
            [nl.surf.eduhub.validator.service.api :as api]
            [nl.surf.eduhub.validator.service.config :as config]
            [nl.surf.eduhub.validator.service.config-test :as config-test]
            [nl.surf.eduhub.validator.service.test-helper :as test-helper]))

(def test-config
  (first (config/load-config-from-env config-test/default-env)))

(def app (api/compose-app test-config :auth-disabled))

(defn- make-status-call [uuid]
  (let [{:keys [status body]}
        (-> (app {:uri (str "/status/" uuid) :request-method :get})
            (update :body json/read-str)
            (select-keys [:status :body]))]
    (is (= http-status/ok status))
    body))

(defn- pop-queue!
  "Remove and return first item from `queue-atom`.

  Returns `nil` if queue-atom is empty."
  [queue-atom]
  (let [old-val @queue-atom]
    (when-not (empty? old-val)
      (let [item    (peek old-val)
            new-val (pop old-val)]
        (if (compare-and-set! queue-atom old-val new-val)
          item
          (pop-queue! queue-atom))))))

(deftest test-queue
  (testing "initial call to api"
    ;; mock c/perform-async
    (let [jobs-atom (atom [])
          status-atom (atom {})
          dirname   "test/fixtures/validate_correct"
          vcr       (test-helper/make-playbacker dirname)]
      (with-redefs [c/perform-async (fn [_job-opts & args]
                                      (swap! jobs-atom conj args))
                    status/set-status-fields (fn [_ id status m _]
                                               (swap! status-atom update id merge (assoc m
                                                                                         (keyword (str status "-at")) "2025-01-01T01:01:01.001Z"
                                                                                         :job-status status))
                                               (prn status-atom))
                    status/load-status (fn [_ id]
                                         (get @status-atom id))]
        ;; make endpoint call
        (let [resp (app {:uri "/jobs/paths/google.com" :request-method :post})]
          (is (= {:headers {"Content-Type" "application/json; charset=utf-8"}, :status 200}
                 (select-keys resp [:headers :status])))
          ;; assert status OK
          (is (= http-status/ok (:status resp)))
          ;; assert job queued
          (is (= 1 (count @jobs-atom)))
          ;; assert json response with uuid
          (let [{:keys [job-status uuid]} (-> resp :body (json/read-str))]
            ;; assert job status pending
            (is (= job-status "pending"))
            ;; make http request to status
            (is (= {:job-status "pending" :profile "ooapi" :endpoint-id "google.com"}
                   (-> (make-status-call uuid)
                       (test-helper/validate-timestamp :pending-at))))

            ;; run the first job in the queue
            (testing "run worker"
              ;; mock http/request
              (with-redefs [http/request (fn wrap-vcr [req] (vcr req))]
                ;; run worker
                (let [[fname & args] (pop-queue! jobs-atom)]
                  (binding [worker/*config* test-config]
                    (apply (resolve fname) args)))

                (let [body (-> (make-status-call uuid)
                               (test-helper/validate-timestamp :pending-at)
                               (test-helper/validate-timestamp :finished-at))]

                  ;; assert status response with status finished and html report
                  (is (= {:job-status "finished" :profile "ooapi" :endpoint-id "google.com"}
                         (dissoc body :html-report))))))))))))

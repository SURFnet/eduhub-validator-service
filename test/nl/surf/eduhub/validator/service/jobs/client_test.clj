(ns nl.surf.eduhub.validator.service.jobs.client-test
  (:require [babashka.http-client :as http]
            [babashka.json :as json]
            [clojure.test :refer [deftest is testing]]
            [environ.core :refer [env]]
            [goose.client :as c]
            [nl.jomco.http-status-codes :as http-status]
            [nl.surf.eduhub.validator.service.api :as api]
            [nl.surf.eduhub.validator.service.config :as config]
            [nl.surf.eduhub.validator.service.config-test :as config-test]
            [nl.surf.eduhub.validator.service.test-helper :as test-helper]))

(def test-config
  (first (config/load-config-from-env (merge config-test/default-env env))))

(def app (api/compose-app test-config :auth-disabled))

(defn- make-status-call [uuid]
  (let [{:keys [status body]}
        (-> (app {:uri (str "/status/" uuid) :request-method :get})
            (update :body json/read-str)
            (select-keys [:status :body]))]
    (is (= http-status/ok status))
    body))

(defn- pop-queue! [atm]
  (let [old-val @atm]
    (when-not (empty? old-val)
      (let [item    (peek old-val)
            new-val (pop old-val)]
        (if (compare-and-set! atm old-val new-val)
          item
          (pop-queue! atm))))))

(deftest test-queue
  (testing "initial call to api"
    ;; mock c/perform-async
    (let [jobs-atom (atom [])
          dirname   "test/fixtures/validate_correct"
          vcr       (test-helper/make-playbacker dirname)]
      (with-redefs [c/perform-async (fn [_job-opts & args]
                                      (swap! jobs-atom conj args))]
        ;; make endpoint call
        (let [resp (app {:uri "/endpoints/google.com/paths" :request-method :post})]
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
            (is (= {:job-status "pending" :profile "rio" :endpoint-id "google.com"}
                   (-> (make-status-call uuid)
                       (test-helper/validate-timestamp :pending-at))))

            ;; run the first job in the queue
            (testing "run worker"
              ;; mock http/request
              (with-redefs [http/request (fn wrap-vcr [req] (vcr req))]
                ;; run worker
                (let [[fname & args] (pop-queue! jobs-atom)]
                  (apply (resolve fname) (assoc-in (vec args) [2 :config] test-config)))

                (let [body (-> (make-status-call uuid)
                               (test-helper/validate-timestamp :pending-at)
                               (test-helper/validate-timestamp :finished-at))]

                  ;; assert status response with status finished and html report
                  (is (= {:job-status "finished" :profile "rio" :endpoint-id "google.com"}
                         (dissoc body :html-report))))))))))))

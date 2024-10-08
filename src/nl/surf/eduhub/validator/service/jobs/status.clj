(ns nl.surf.eduhub.validator.service.jobs.status
  (:require [taoensso.carmine :as car])
  (:import [java.time Instant]))

(defn status-key [uuid] (str "validation:" uuid))

(def job-status "job-status")

;; Updates the status of the job status entry of job `uuid` to `new-status`.
;; Also sets a timestamp named after the new status (e.g. finished-at)
;; Also sets any values in `key-value-map` in the redis hash.
;; If `expires-in-seconds` is set, (re)sets the expiry.
(defn set-status-fields [redis-conn uuid new-status key-value-map expires-in-seconds]
  (let [v (assoc key-value-map
            job-status new-status
            (str new-status "-at") (-> (System/currentTimeMillis)
                                       Instant/ofEpochMilli
                                       str))
        key (status-key uuid)]
    (car/wcar redis-conn (car/hmset* key v))
    (when expires-in-seconds
      (car/wcar redis-conn (car/expire key expires-in-seconds)))))

;; Loads the job status as a clojure map for the job with given uuid.
(defn load-status [redis-conn uuid]
  (let [result (car/wcar redis-conn (car/hgetall (status-key uuid)))]
    (when-not (empty? result)
      (let [m (into {} (apply hash-map result))
            m (into {} (for [[k v] m] [(keyword k) v]))]
        (assert (every? keyword? (keys m)))
        m))))

(defn delete-status [redis-conn uuid]
  (car/wcar redis-conn (car/del (status-key uuid))))

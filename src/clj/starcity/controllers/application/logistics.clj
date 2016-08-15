(ns starcity.controllers.application.logistics
  (:require [clj-time
             [coerce :as c]
             [format :as f]]
            [ring.util.response :as response]
            [starcity.controllers.application.common :as common]
            [starcity.controllers.application.logistics
             [data :as data]
             [params :as p]]
            [starcity.controllers.utils :refer :all]
            [starcity.models.application :as application]
            [starcity.views.application.logistics :as view]))

;; =============================================================================
;; Helpers
;; =============================================================================

(def ^:private basic-date-formatter (f/formatter :basic-date))

(defn- show-logistics*
  [{:keys [params identity] :as req} & {:keys [errors] :or {errors []}}]
  (let [account-id  (:db/id identity)]
    (view/logistics
     req
     (application/current-steps account-id)
     (data/pull-properties)
     (data/pull-application account-id)
     (data/pull-licenses)
     :errors errors)))

;; =============================================================================
;; API
;; =============================================================================

(defn show-logistics
  [req]
  (ok (show-logistics* req)))

(defn save! [{:keys [params form-params identity] :as req}]
  (let [vresult    (-> params p/clean p/validate)
        account-id (:db/id identity)]
    (if-let [{:keys [selected-license pet availability properties]} (valid? vresult)]
      (let [desired-availability (c/to-date (f/parse basic-date-formatter availability))]
        ;; If there is an existing rental application for this user, we're
        ;; dealing with an update.
        (if-let [{application-id :db/id} (application/by-account-id account-id)]
          (application/update! application-id
                               {:desired-license      selected-license
                                :desired-availability desired-availability
                                :desired-properties   properties
                                :pet                  pet})
          ;; otherwise, we're creating a new rental application for this user.
          (application/create! account-id properties selected-license desired-availability :pet pet))
        ;; afterwards, redirect to the next step
        (response/redirect "/application/personal"))
      ;; results aren't valid! indicate errors
      (malformed (show-logistics* req :errors (errors-from vresult))))))

(def restrictions
  {:handler {:and [common/not-locked]}
   :on-error common/on-error})

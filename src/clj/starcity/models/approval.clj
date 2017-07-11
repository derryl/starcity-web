(ns starcity.models.approval
  (:require [clojure.spec :as s]
            [starcity.datomic.partition :refer [tempid]]
            [starcity.models
             [application :as app]
             [security-deposit :as deposit]
             [unit :as unit]]
            [toolbelt.predicates :as p]
            [starcity.models.onboard :as onboard]
            [starcity.models.property :as property]
            [toolbelt.date :as date]
            [clj-time.core :as t]
            [reactor.events :as events]))

;; =============================================================================
;; Lookups
;; =============================================================================

(def by-account
  "Look up the `approval` entity by `account`."
  (comp first :approval/_account))

(s/fdef by-account
        :args (s/cat :account p/entity?)
        :ret p/entity?)

;; =============================================================================
;; Selectors
;; =============================================================================

(def approver
  "The admin `account` that did the approving."
  :approval/approver)

(s/fdef approver
        :args (s/cat :approval p/entity?)
        :ret p/entity?)

(def move-in
  "The move-in date."
  :approval/move-in)

(s/fdef move-in
        :args (s/cat :approval p/entity?)
        :ret inst?)

(def unit
  "The `unit` that `account` is approved to live in."
  :approval/unit)

(s/fdef unit
        :args (s/cat :approval p/entity?)
        :ret p/entity?)

(def license
  "The `license` (term) that `account` was approved for."
  :approval/license)

(s/fdef license
        :args (s/cat :approval p/entity?)
        :ret p/entity?)

(def property
  "The property that approval is for."
  (comp unit/property unit))

(s/fdef property
        :args (s/cat :approval p/entity?)
        :ret p/entity?)

;; =============================================================================
;; Transactions
;; =============================================================================

(defn create
  "Produce transaction data required to create an approval entity."
  [approver approvee unit license move-in]
  (let [tz (-> unit unit/property property/time-zone)]
    {:db/id             (tempid)
     :approval/account  (:db/id approvee)
     :approval/approver (:db/id approver)
     :approval/unit     (:db/id unit)
     :approval/license  (:db/id license)
     :approval/move-in  (date/beginning-of-day move-in tz) ; ensure beginning of day
     :approval/status   :approval.status/pending}))

(s/fdef create
        :args (s/cat :approver p/entity?
                     :approvee p/entity?
                     :unit p/entity?
                     :license p/entity?
                     :move-in inst?)
        :ret (s/keys :req [:db/id
                           :approval/account
                           :approval/approver
                           :approval/unit
                           :approval/license
                           :approval/move-in
                           :approval/status]))

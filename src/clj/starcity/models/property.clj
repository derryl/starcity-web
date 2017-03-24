(ns starcity.models.property
  (:refer-clojure :exclude [name])
  (:require [clojure.spec :as s]
            [starcity.models.unit :as unit]
            starcity.spec
            [datomic.api :as d]
            [toolbelt.predicates :as p]))

;; =============================================================================
;; Spec
;; =============================================================================

(s/def :property/name string?)
(s/def :property/available-on :starcity.spec/date)
(s/def :property/cover-image-url string?)
(s/def :property/description string?)

;; =============================================================================
;; Selectors
;; =============================================================================

(def name :property/name)
(def internal-name :property/internal-name)
(def managed-account-id :property/managed-account-id)
(def ops-fee :property/ops-fee)
(def units :property/units)

(def accepting-tours?
  "Is this property currently accepting tours?"
  :property/tours)

(s/fdef accepting-tours?
        :args (s/cat :property p/entity?)
        :ret boolean?)

;; =============================================================================
;; Lookups
;; =============================================================================

(defn by-internal-name
  [db internal-name]
  (d/entity db [:property/internal-name internal-name]))

(s/fdef by-internal-name
        :args (s/cat :db p/db? :internal-name string?)
        :ret p/entity?)

;; =============================================================================
;; Queries
;; =============================================================================

(defn occupied-units
  "Produce all units that are currently occupied."
  [conn property]
  (filter (partial unit/occupied? (d/db conn)) (units property)))

(defn available-units
  "Produces all available units in `property`.

  (A unit is considered available if there is no active member license that
  references it.)"
  [conn property]
  (remove (partial unit/occupied? (d/db conn)) (units property)))

(defn total-rent
  "The total rent that can be collected from the current active member
  licenses."
  [conn property]
  (->> (d/q '[:find ?m (sum ?rate)
              :in $ ?p
              :where
              [?p :property/units ?u]
              [?m :member-license/unit ?u]
              [?m :member-license/status :member-license.status/active]
              [?m :member-license/rate ?rate]]
            (d/db conn) (:db/id property))
       (map second)
       (reduce + 0)))

(defn- amount-query
  [conn property date status]
  (->> (d/q '[:find ?py ?amount
             :in $ ?p ?now ?status
             :where
             [?p :property/units ?u]
             [?m :member-license/unit ?u]
             [?m :member-license/status :member-license.status/active]
             [?m :member-license/rent-payments ?py]
             [?py :rent-payment/amount ?amount]
             [?py :rent-payment/status ?status]
             [?py :rent-payment/period-start ?start]
             [?py :rent-payment/period-end ?end]
             [(.after ^java.util.Date ?end ?now)]
             [(.before ^java.util.Date ?start ?now)]]
           (d/db conn) (:db/id property) date status)
       (reduce #(+ %1 (second %2)) 0)))

(defn amount-paid
  "The amount in dollars that has been collected in `property` for the month
  present within `date`."
  [conn property date]
  (amount-query conn property date :rent-payment.status/paid))

(defn amount-due
  "The amount in dollars that is still due in `property` for the month present
  within `date`."
  [conn property date]
  (amount-query conn property date :rent-payment.status/due))

(defn amount-pending
  "The amount in dollars that is pending in `property` for the month present
  within `date`."
  [conn property date]
  (amount-query conn property date :rent-payment.status/pending))

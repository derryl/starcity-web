(ns starcity.api.admin.properties
  (:require
   [clojure.spec :as s]
   [compojure.core :refer [defroutes GET POST DELETE]]
   [datomic.api :as d]
   [ring.util.response :as response]
   [starcity.datomic :refer [conn]]
   [starcity.models
    [account :as account]
    [member-license :as member-license]
    [property :as property]
    [unit :as unit]]
   [starcity.services.stripe.subscription :as sub]
   [toolbelt
    [core :as tb :refer [str->int]]
    [predicates :as p]]
   [starcity.models.license :as license]
   [clj-time.coerce :as c]))

;; =============================================================================
;; Internal
;; =============================================================================

(def transit "application/transit+json")

;; =============================================================================
;; Overview

(defn clientize-property [conn property]
  (let [now (java.util.Date.)]
    {:db/id                   (:db/id property)
     :property/name           (property/name property)
     :property/total-units    (count (property/units property))
     :property/total-occupied (count (property/occupied-units conn property))
     :property/amount-total   (property/total-rent conn property)
     :property/amount-due     (tb/round (property/amount-due conn property now) 2)
     :property/amount-pending (tb/round (property/amount-pending conn property now) 2)
     :property/amount-paid    (tb/round (property/amount-paid conn property now) 2)}))

(defn overview [conn]
  {:result
   (->> (d/q '[:find [?p ...] :where [?p :property/name _]] (d/db conn))
        (map (comp (partial clientize-property conn) (partial d/entity (d/db conn)))))})

(comment

  (property/total-rent
   conn
   (d/entity (d/db conn) [:property/internal-name "52gilbert"]))

  (d/q '[:find ?m (?rate)
         :in $ ?p
         :where
         [?p :property/units ?u]
         [?m :member-license/unit ?u]
         [?m :member-license/status :member-license.status/active]
         [?m :member-license/rate ?rate]]
       (d/db conn) [:property/internal-name "52gilbert"])

  (overview conn)
  )

;; =============================================================================
;; Entry

(defn- clientize-license-price [license-price]
  (-> (select-keys license-price [:db/id :license-price/price])
      (assoc :license-price/term (-> license-price :license-price/license :license/term))))

(defn- clientize-unit
  [conn unit]
  (let [occupant (unit/occupied-by conn unit)
        license  (when occupant (member-license/active conn occupant))]
    (merge
     (select-keys unit [:db/id :unit/name])
     {:unit/account   (when occupant
                        {:db/id        (:db/id occupant)
                         :account/name (account/full-name occupant)})
      :unit/rate      (when license (member-license/rate license))
      :unit/term      (when license (member-license/term license))})))

(defn- units [conn property]
  (->> (property/units property)
       (map (partial clientize-unit conn))
       (sort-by :unit/name)))

(defn- licenses [property]
  (->> (:property/licenses property)
       (map clientize-license-price)
       (sort-by :license-price/term)))

(def dashboard-url "https://dashboard.stripe.com/")

(defn entry [conn property-id]
  (let [property (d/entity (d/db conn) property-id)]
    {:result
     (merge
      (clientize-property conn property)
      (select-keys property [:property/ops-fee :property/available-on])
      {:property/licenses   (licenses property)
       :property/units      (units conn property)
       :property/stripe-url (str dashboard-url
                                 (property/managed-account-id property)
                                 "/payments")})}))

(comment
  (let [property (d/entity (d/db conn) [:property/internal-name "2072mission"])]
    (units conn property))

  (let [unit (d/entity (d/db conn) [:unit/name "2072mission-1"])]
    (unit/occupied-by conn unit))

  )

;; =============================================================================
;; Update

(defn- subscriptions [conn property]
  (d/q '[:find [?sub-id ...]
         :in $ ?p
         :where
         [?l :member-license/subscription-id ?sub-id]
         [?l :member-license/unit ?u]
         [?l :member-license/status :member-license.status/active]
         [?p :property/units ?u]]
       (d/db conn) (:db/id property)))

(defn- update-ops-fee!
  "Whenever the `:property/ops-fee` value is updated, all subscriptions need to
  be updated with the new fee."
  [conn property new-ops-fee]
  (doseq [sub-id (subscriptions conn property)]
    (sub/update! sub-id
                 :fee-percent new-ops-fee
                 :managed (property/managed-account-id property))))

(s/fdef update-ops-fee!
        :args (s/cat :connection p/conn? :property p/entity? :ops-fee float?))

(defmulti update-tx (fn [e k v] k))

(defmethod update-tx :property/ops-fee [e _ new-fee]
  {:db/id e :property/ops-fee (float new-fee)})

(defmethod update-tx :property/available-on [e _ new-available-on]
  {:db/id e :property/available-on new-available-on})

(defmethod update-tx :property/licenses [e _ new-license-price]
  (-> (select-keys new-license-price [:db/id :license-price/price])
      (update :license-price/price float)))

(defn update! [conn property-id params]
  @(d/transact conn (reduce
                     (fn [acc [k v]]
                       (conj acc (update-tx property-id k v)))
                     []
                     params))
  {:result "ok"})

(comment
  (let [property (d/entity (d/db conn) [:property/internal-name "52gilbert"])]
    (->> (update-ops-fee conn property 30.0)
         first))

  (let [property (d/entity (d/db conn) [:property/internal-name "52gilbert"])]
    (update-ops-fee! conn property 25.0))

  (let [property (d/entity (d/db conn) [:property/internal-name "52gilbert"])]
    (:application_fee_percent (:body (sub/fetch "sub_9zbG4ycfe4VA1u"
                                                :managed (property/managed-account-id property)))))

  (let [property (d/entity (d/db conn) [:property/internal-name "52gilbert"])]
    (property/units property))

  )

;; =============================================================================
;; Fetch Units

(defn fetch-units
  [conn property-id {:keys [available-by license]}]
  (let [units (:property/units (d/entity (d/db conn) property-id))
        db    (d/db conn)]
    {:result
     (->> (map
           (fn [unit]
             (-> (clientize-unit conn unit)
                 (assoc :unit/available (if available-by
                                          (unit/available? db unit available-by)
                                          (unit/available? db unit (java.util.Date.)))
                        ;; NOTE: Using `:unit/market` to avoid disambiguation
                        ;; with the `:unit/rate` produced by `clientize-unit`.
                        ;; Obviously, finding a way to unify these things into
                        ;; one function would be better.
                        :unit/market (when license
                                       (unit/rate unit (d/entity db license))))))
           units)
          (sort-by :unit/name))}))

(s/def :fetch-units/available-by inst?)
(s/def :fetch-units/license integer?)
(s/def :fetch-units/result (s/* map?))
(s/fdef fetch-units
        :args (s/cat :conn p/conn?
                     :property-id integer?
                     :opts (s/keys :opt-un [:fetch-units/available-by
                                            :fetch-units/license]))
        :ret (s/keys :req-un [:fetch-units/result]))

;; =============================================================================
;; Unit Entry

(defn- clientize-unit-entry [conn unit]
  (let [occupant (unit/occupied-by conn unit)]
    ;; (println (:unit/licenses unit))
    (merge
     (select-keys unit [:db/id :unit/name])
     {:property/licenses (->> (unit/property unit)
                              :property/licenses
                              (map clientize-license-price))
      :unit/licenses     (map clientize-license-price (:unit/licenses unit))
      :unit/account      (when occupant
                           {:db/id        (:db/id occupant)
                            :account/name (account/full-name occupant)})})))

(defn unit-entry
  ""
  [conn unit-id]
  (let [unit (d/entity (d/db conn) unit-id)]
    {:result (clientize-unit-entry conn unit)}))


(comment
  #_(d/transact conn [{:db/id 285873023222893
                       :unit/licenses
                       [{:license-price/license (:db/id (license/by-term conn 1))
                         :license-price/price   2500.0}
                        {:license-price/license (:db/id (license/by-term conn 3))
                         :license-price/price   2400.0}
                        {:license-price/license (:db/id (license/by-term conn 6))
                         :license-price/price   2200.0}
                        {:license-price/license (:db/id (license/by-term conn 12))
                         :license-price/price   2100.0}]}])

  )

;; =============================================================================
;; Update License Price

(defn create-unit-license-price
  [conn unit-id price term]
  (assert (and (number? price) (> price 0)))
  (let [license (license/by-term conn term)]
    @(d/transact conn [{:db/id         unit-id
                        :unit/licenses {:license-price/license (:db/id license)
                                        :license-price/price   (float price)}}])
    {:result "Ok."}))

(defn update-license-price
  [conn license-price-id price]
  (assert (and (number? price) (> price 0)))
  @(d/transact conn [{:db/id               license-price-id
                      :license-price/price (float price)}])
  {:result "Ok."})

;; =============================================================================
;; Routes
;; =============================================================================

(defroutes routes
  (GET "/overview" []
       (fn [_]
         (-> (overview conn)
             (response/response)
             (response/content-type transit))))

  (GET "/:property-id" [property-id]
       (fn [_]
         (-> (entry conn (str->int property-id))
             (response/response)
             (response/content-type transit))))

  (POST "/:property-id" [property-id]
        (fn [{:keys [body-params] :as req}]
          (-> (update! conn (str->int property-id) body-params)
              (response/response)
              (response/content-type transit))))

  ;; =====================================
  ;; Units

  (GET "/:property-id/units" [property-id]
       (fn [{params :params}]
         (let [params (tb/transform-when-key-exists params
                        {:available-by (comp c/to-date c/from-long str->int)
                         :license      str->int})]
           (-> (fetch-units conn (str->int property-id) params)
               (response/response)
               (response/content-type transit)))))

  (GET "/:property-id/units/:unit-id" [unit-id]
       (fn [_]
         (-> (unit-entry conn (str->int unit-id))
             (response/response)
             (response/content-type transit))))

  (POST "/:property-id/units/:unit-id/license-prices" [unit-id]
        (fn [{params :params}]
          (-> (create-unit-license-price conn
                                         (str->int unit-id)
                                         (:price params)
                                         (:term params))
              (response/response)
              (response/content-type transit))))

  (POST "/:property-id/units/:unit-id/license-prices/:price-id" [price-id]
        (fn [{params :params}]
          (-> (update-license-price conn (str->int price-id) (:price params))
              (response/response)
              (response/content-type transit))))

  (DELETE "/:property-id/units/:unit-id/license-prices/:price-id" [price-id]
          (fn [_]
            @(d/transact conn [[:db.fn/retractEntity (str->int price-id)]])
            (-> (response/response {:result "Ok."})
                (response/content-type transit)))) )

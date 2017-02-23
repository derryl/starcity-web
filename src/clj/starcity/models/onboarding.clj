(ns starcity.models.onboarding
  (:require [starcity.models.stripe :as stripe]
            [starcity.models.stripe.customer :as customer]
            [starcity.models.charge :as charge]
            [starcity.datomic :refer [conn tempid]]
            [starcity.models.util :refer :all]
            [datomic.api :as d]
            [starcity.spec]
            [clojure.spec :as s]
            [starcity.models.account :as account]
            [starcity.models.approval :as approval]
            [starcity.models.unit :as unit]))

;; TODO: Change progress to communicate w/ entities rather than maps or ids

;; =============================================================================
;; Internal
;; =============================================================================

;; =============================================================================
;; DB Lookups

(s/def ::security-deposit
  (s/keys :req [:db/id
                :security-deposit/account
                :security-deposit/amount-required]
          :opt [:security-deposit/amount-received
                :security-deposit/payment-method
                :security-deposit/payment-type
                :security-deposit/charges
                :security-deposit/due-by]))

(defn- security-deposit
  "Retrieve the security deposit entity by account id."
  [lookup]
  (ent->map (one (d/db conn) :security-deposit/account lookup)))

(s/fdef security-deposit
        :args (s/cat :lookup :starcity.spec/lookup)
        :ret ::security-deposit)

(s/def :stripe-customer/customer-id string?)

(s/def ::stripe-customer
  (s/or
   :nothing empty?
   :stripe-customer (s/keys :req [:db/id
                                  :stripe-customer/account
                                  :stripe-customer/customer-id]
                            :opt [:stripe-customer/bank-account-token])))

(defn- stripe-customer
  "Retrieve the stripe customer record for given account-id."
  [lookup]
  (ent->map (one (d/db conn) :stripe-customer/account lookup)))

(s/fdef stripe-customer
        :args (s/cat :lookup :starcity.spec/lookup)
        :ret ::stripe-customer)

;; =============================================================================
;; Selectors

(defn security-deposit-id
  "Retrieve the entity id of the security deposit entity within the `progress` map."
  [progress]
  (get-in progress [:security-deposit :db/id]))

(defn stripe-customer-id
  "Retrieve the Stripe customer id within the `progress` map."
  [progress]
  (get-in progress [:stripe-customer :stripe-customer/customer-id]))

(def ^:private account-id
  "Retrieve the applicant's account-id from the `progress` map."
  :account-id)

(defn- bank-account-token
  "Retrieve the bank account token within the `progress` map."
  [progress]
  (get-in progress [:stripe-customer :stripe-customer/bank-account-token]))

;; =============================================================================
;; Transactions

;; =============================================================================
;; API
;; =============================================================================

;; =============================================================================
;; Specs

(s/def ::account-id integer?)

(s/def ::payment-method
  #{:security-deposit.payment-method/ach
    :security-deposit.payment-method/check})

(s/def ::progress
  (s/keys :req-un [::account-id ::security-deposit ::stripe-customer]))

;; =============================================================================
;; Selectors

(defn payment-method
  "Get the payment method from the onboarding progress."
  [progress]
  (get-in progress [:security-deposit :security-deposit/payment-method]))

(s/fdef payment-method
        :args (s/cat :progress ::progress))

(defn full-deposit-amount
  "Retrieve the full security deposit amount."
  [progress]
  (get-in progress [:security-deposit :security-deposit/amount-required]))

(defn applicant-property
  "The property that this applicant is being onboarded for."
  [progress]
  (let [account (d/entity (d/db conn) (account-id progress))]
    (-> account account/approval approval/unit unit/property)))

(def property-code
  (comp :property/internal-name applicant-property))

;; =============================================================================
;; Transactions

(defn update-payment-method
  "Update the payment method. Will create a new security deposit entity if one
  does not already exist, or update the existing one if it does."
  [progress method]
  @(d/transact conn [{:db/id                           (security-deposit-id progress)
                      :security-deposit/payment-method method}]))

(s/fdef update-payment-method
        :args (s/cat :progress ::progress
                     :method   ::payment-method))

;; =====================================
;; Perform ACH charge

;; Firstly, we need to know the correct amount to charge.
;; We can determine this by using a map of {#{"full" "partial"} <amt>}

(defn- charge-amount
  "Determine the correct amount to charge in cents given "
  [progress payment-choice]
  (if (= "full" payment-choice)
    (* (get-in progress [:security-deposit :security-deposit/amount-required]) 100)
    50000))

;; Now that we know how much to charge, we can actually make the charge!
;; If it succeeds, we'll want to create/update entities in the DB to reflect the
;; success. If not, an error will propogate up from the `stripe' model.

(defn- create-ach-charge
  [progress payment-choice]
  (stripe/create-charge! (account-id progress)
                         (charge-amount progress payment-choice)
                         (bank-account-token progress)
                         :description (format "'%s' security deposit payment" payment-choice)
                         :customer-id (stripe-customer-id progress)
                         :managed-account (-> progress applicant-property :property/managed-account-id)))

;; Assuming no error, we now need to update the security deposit entity to
;; reflect the successful transaction.

(defn- record-security-deposit-payment
  "Update the `security-deposit` entity to reflect that the security deposit has
  been paid via a successful ACH payment."
  [progress payment-choice charge-id]
  (let [payment-type (keyword "security-deposit.payment-type" payment-choice)]
    @(d/transact conn [{:db/id                         (security-deposit-id progress)
                        :security-deposit/charges      charge-id
                        :security-deposit/payment-type payment-type}])))


(declare payment-received?)

;; Here's what ties it all together.

(defn pay-ach
  "Pay the security deposit with ACH given current progress and the choice of
  payment method."
  [progress payment-choice]
  (if (payment-received? progress)
    (throw (ex-info "Cannot charge customer for security deposit twice!"
                    {:customer-id (stripe-customer-id progress)}))
    (let [charge-id (create-ach-charge progress payment-choice)]
      (record-security-deposit-payment progress payment-choice charge-id))))

(s/fdef pay-ach
        :args (s/cat :progress ::progress
                     :payment-choice #{"full" "partial"}))

;; =============================================================================
;; Predicates

(defn payment-method-chosen?
  "Has a payment method been chosen?"
  [progress]
  (let [{:keys [:security-deposit/payment-method]} (:security-deposit progress)]
    (#{:security-deposit.payment-method/ach
       :security-deposit.payment-method/check}
     payment-method)))

(defn customer-created?
  "Has a Stripe customer been created?"
  [progress]
  (-> progress :stripe-customer empty? not))

(defn verification-failed?
  "Has the verification step failed for all of this customer's sources?"
  [progress]
  (customer/verification-failed?
   (customer/fetch (stripe-customer-id progress))))

(defn bank-account-verified?
  "Is the stripe customer's bank account verified?"
  [progress]
  (or (:stripe-customer/bank-account-token progress) ; Only present in DB after
                                        ; verification
      (and (stripe-customer-id progress)
           (customer/has-verified-bank-account?
            (customer/fetch (stripe-customer-id progress))))))

(defn payment-received?
  "Payment is received when a payment method is chosen and the associated
  charge has been marked with the succeeded status."
  [{security-deposit :security-deposit :as progress}]
  (and (payment-method-chosen? progress)
       ;; If there are any pending charges, we'll let them move on.
       (or (some charge/is-pending? (:security-deposit/charges security-deposit))
           (> (get security-deposit :security-deposit/amount-received 0) 0))))

;; =============================================================================
;; Get Progress

(defn get-progress
  [lookup]
  {:account-id       lookup
   :stripe-customer  (stripe-customer lookup)
   :security-deposit (security-deposit lookup)})

(s/fdef get-progress
        :args (s/cat :lookup :starcity.spec/lookup)
        :ret ::progress)

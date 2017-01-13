(ns starcity.datomic.migrata.property
  (:require [datomic.api :as d]))

(defn- ^{:added "1.2.0"} add-ops-fees
  "Add the Stripe account identifiers of the managed accounts we've created in
  our test environment to the properties they're associated with in our system."
  [_]
  [{:db/id            [:property/internal-name "52gilbert"]
    :property/ops-fee 30.0}
   {:db/id            [:property/internal-name "2072mission"]
    :property/ops-fee 30.0}])

(defn- units-by-property-name [conn internal-name]
  (d/q '[:find [?e ...]
         :in $ ?p
         :where
         [?p :property/units ?e]]
       (d/db conn) [:property/internal-name internal-name]))

(defn- name-units [conn internal-name]
  (->> (units-by-property-name conn internal-name)
       (map-indexed
        (fn [i e]
          [:db/add e :unit/name (format "%s-%s" internal-name (inc i))]))))

(defn- ^{:added "1.2.0"} add-unit-names [conn]
  (concat (name-units conn "52gilbert")
          (name-units conn "2072mission")))

(defn norms [conn]
  {:migration.property/add-ops-fees-12-15-16
   {:txes [(add-ops-fees conn)]}

   :migration.property.unit/add-unit-names-1-13-17
   {:txes [(add-unit-names conn)]}})

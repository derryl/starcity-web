(ns starcity.datomic.migrations.initial.seed-gilbert
  (:require [starcity.datomic.migrations.utils :refer :all]
            [starcity.datomic.migrations :refer [defnorms]]
            [datomic.api :as d]))

(defnorms seed-gilbert [conn]
  :txes (let [pls [[1 2300.0] [6 2100.0] [12 2000.0]]]
          [{:db/id                    (d/tempid :db.part/starcity)
            :property/name            "West SoMa"
            :property/description     "A victorian home designed to be welcoming and enjoyable, our West SoMa community offers members 900 square feet in communal space. Members share a beautiful kitchen, social lounge, media room and more. Each member has their own private bedroom to nest in. The entire home is furnished to make moving in hassle-free. This community has five private units."
            :property/cover-image-url "/assets/img/southpark-soma.jpg"
            :property/internal-name   "52gilbert"
            :property/available-on    #inst "2016-09-15T00:00:00.000-00:00"
            :property/address         {:address/lines "52 Gilbert St."
                                       :address/city  "San Francisco"}
            :property/licenses        (map (partial property-license conn) pls)
            :property/units           []}])
  :env      #{:development :staging}
  :requires [seed-licenses add-property-schema add-address-schema])

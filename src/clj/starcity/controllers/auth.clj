(ns starcity.controllers.auth
  (:require [ring.util.response :as response]))

;; =============================================================================
;; API

(defn logout! [_]
  (-> (response/redirect "/login")
      (assoc :session {})))
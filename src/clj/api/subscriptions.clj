(ns api.subscriptions
  (:require [api.models.analysis :as analysis-model]
            [clojure.core.async :as async :refer [>! go go-loop]]
            [shared.utils :refer [clj->gql]]
            [taoensso.timbre :as log]))

(defn- create-status-subscription [sub-name callback]
  (fn [{:keys [authed-user-id db access-token]} {:keys [id]} source-stream]
    (log/debug "client subscribed to"  {:sub/name  sub-name
                                        :user/id   authed-user-id
                                        :parser/id id
                                        :token     access-token})
    ;; create the subscription
    (let [subscription-closed? (async/promise-chan)]
      (go-loop []
        (let [sleep    (async/timeout 1000)
              [_ port] (async/alts! [subscription-closed? sleep])]
          (when-not (= port subscription-closed?)
            (when-let [{:keys [status progress of-type readable-name]} (callback db id)]
              (source-stream (clj->gql {:id            id
                                        :readable-name readable-name
                                        :of-type       of-type
                                        :status        status
                                        :progress      (or progress 0)}))
              (recur)))))
      ;; return a function to cleanup the subscription
      (fn []
        (go
          (log/debug "subscription closed" {:sub/name    sub-name
                                            :user/id     authed-user-id
                                            :analysis/id id})
          (>! subscription-closed? true))))))

(defn create-analysis-status-sub []
  (create-status-subscription "analysis" (fn [db id]
                                           (analysis-model/get-analysis db {:id id}))))

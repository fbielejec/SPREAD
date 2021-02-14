(ns api.subscriptions
  (:require [api.models.bayes-factor :as bayes-factor-model]
            [api.models.continuous-tree :as continuous-tree-model]
            [api.models.discrete-tree :as discrete-tree-model]
            [api.models.time-slicer :as time-slicer-model]
            [clojure.core.async :refer [<! close! go-loop timeout]]
            [shared.utils :refer [clj->gql]]
            [taoensso.timbre :as log]))

(defn- create-status-subscription [sub-name callback]
  (fn [{:keys [authed-user-id db]} {:keys [id]} source-stream]
    (log/debug "client subscribed to" {:name        sub-name
                                       :user/id     authed-user-id
                                       :analysis/id id})
    ;; create the subscription
    (let [subscription (go-loop []
                         (when-let [{:keys [status]} (callback db id) #_ (continuous-tree-model/get-status db {:id id})]
                           (source-stream (clj->gql {:id     id
                                                     :status status}))
                           (<! (timeout 1000))
                           (recur)))]
      ;; return a function to cleanup the subscription
      (fn []
        (log/debug "subscription closed" {:name        sub-name
                                          :user/id     authed-user-id
                                          :analysis/id id})
        (close! subscription)))))

(defn create-continuous-tree-parser-status-sub []
  (create-status-subscription "continuous-tree" (fn [db id ]
                                                  (continuous-tree-model/get-status db {:id id}))))

(defn create-discrete-tree-parser-status-sub []
  (create-status-subscription "discrete-tree" (fn [db id ]
                                                (discrete-tree-model/get-status db {:id id}))))

(defn create-bayes-factor-parser-status-sub []
  (create-status-subscription "bayes-factor" (fn [db id ]
                                               (bayes-factor-model/get-status db {:id id}))))

(defn create-time-slicer-parser-status-sub []
  (create-status-subscription "time-slicer" (fn [db id ]
                                              (time-slicer-model/get-status db {:id id}))))
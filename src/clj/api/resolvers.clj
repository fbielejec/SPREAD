(ns api.resolvers
  (:require [api.models.analysis :as analysis-model]
            [api.models.bayes-factor :as bayes-factor-model]
            [api.models.continuous-tree :as continuous-tree-model]
            [api.models.discrete-tree :as discrete-tree-model]
            [api.models.error :as error-model]
            [api.models.time-slicer :as time-slicer-model]
            [api.models.user :as user-model]
            [clojure.data.json :as json]
            [com.walmartlabs.lacinia.executor :as executor]
            [shared.utils :refer [clj->gql]]
            [taoensso.timbre :as log]))

(defn get-authorized-user
  [{:keys [authed-user-id db]} _ _]
  (log/info "get-authorized-user query" {:authed-user-id authed-user-id})
  (clj->gql (user-model/get-user-by-id db {:id authed-user-id})))

(defn get-continuous-tree
  [{:keys [db] :as context} {id :id :as args} _]
  (log/info "get-continuous-tree query" args)
  (clj->gql (merge (continuous-tree-model/get-tree db {:id id})
                   (when (executor/selects-field? context :ContinuousTree/timeSlicer)
                     {:timeSlicer (time-slicer-model/get-time-slicer-by-continuous-tree-id db {:continuous-tree-id id})}))))

(defn continuous-tree->attributes
  [{:keys [db]} _ {tree-id :id :as parent}]
  (log/info "continuous-tree->attributes query" parent)
  (let [attributes (map :attribute-name (continuous-tree-model/get-attributes db {:id tree-id}))]
    (log/info "continuous-tree->attributes results" {:attributes attributes})
    attributes))

;; TODO: this resolver is not being called even if the `timeSlicer` field is present on `getContinuousTree` query
;; I have no clue WTF
;; this is fixed in the get-continuous-tree resolver by appending the field explicitely if the field is present
(defn continuous-tree->time-slicer
  [{:keys [db]} _ {tree-id :id :as parent}]
  (log/info "continuous-tree->time-slicer query" parent)
  (let [time-slicer (time-slicer-model/get-time-slicer-by-continuous-tree-id db {:continuous-tree-id tree-id})]
    (log/info "continuous-tree->time-slicer results" time-slicer)
    (clj->gql time-slicer)))

(defn get-discrete-tree
  [{:keys [db]} {id :id :as args} _]
  (log/info "get-discrete-tree" args)
  (clj->gql (discrete-tree-model/get-tree db {:id id})))

(defn discrete-tree->attributes
  [{:keys [db]} _ {tree-id :id :as parent}]
  (log/info "discrete-tree->attributes query" parent)
  (let [attributes (map :attribute-name (discrete-tree-model/get-attributes db {:id tree-id}))]
    (log/info "discrete-tree->attributes results" {:attributes attributes})
    attributes))

(defn time-slicer->attributes
  [{:keys [db]} _ {time-slicer-id :id :as parent}]
  (log/info "time-slicer->attributes query" parent)
  (let [attributes (map :attribute-name (time-slicer-model/get-attributes db {:id time-slicer-id}))]
    (log/info "time-slicer->attributes results" {:attributes attributes})
    attributes))

(defn get-bayes-factor-analysis
  [{:keys [db]} {id :id :as args} _]
  (log/info "get-bayes-factor-analysis query" args)
  (clj->gql (bayes-factor-model/get-bayes-factor-analysis db {:id id})))

(defn bayes-factor-analysis->bayes-factors
  [{:keys [db]} _ {bayes-factor-analysis-id :id :as parent}]
  (log/info "bayes-factor-analysis->bayes-factors query" parent)
  (let [{:keys [bayes-factors]} (bayes-factor-model/get-bayes-factors db {:id bayes-factor-analysis-id})
        bayes-factors           (when bayes-factors
                                  (json/read-str bayes-factors))]
    (log/info "bayes-factor-analysis->bayes-factors result" {:bayes-factors bayes-factors})
    (clj->gql bayes-factors)))

(defn get-user-analysis
  "Returns a list of all user analysis"
  [{:keys [db authed-user-id]} _ _]
  (log/info "get-user-analysis query" {:user/id authed-user-id})
  (let [analysis (analysis-model/get-user-analysis db {:user-id authed-user-id})]
    (log/info "get-user-analysis results" analysis)
    (clj->gql analysis)))

(defn analysis->error
  [{:keys [db]} _ {analysis-id :id :as parent}]
  (log/info "analysis->error query" parent)
  (let [error (error-model/get-error db {:id analysis-id})]
    (log/info "analysis->error result" error)
    (:error error)))

;; NOTE: not used atm
#_(defn search-user-analysis
    "Returns paginated user analysis, following the Relay specification:
  https://relay.dev/graphql/connections.htm.
  Clients can ask for the next page using an opaque cursor."
    [{:keys [db authed-user-id]} {first-n       :first
                                  after-cursor  :after
                                  last-n        :last
                                  before-cursor :before
                                  statuses      :statuses
                                  readable-name :readable-name
                                  :or           {statuses      [:UPLOADED
                                                                :ATTRIBUTES_PARSED
                                                                :ARGUMENTS_SET
                                                                :QUEUED
                                                                :RUNNING
                                                                :SUCCEEDED
                                                                :ERROR]
                                                 readable-name ""}
                                  :as           args} _]
    (log/info "search-user-analysis" args)
    (let [after                          (if after-cursor
                                           (-> after-cursor decode-base64 Integer/parseInt)
                                           0)
          before                         (when before-cursor
                                           (-> before-cursor decode-base64 Integer/parseInt))
          {:keys [total-count analysis]} (user-model/search-user-analysis db (merge (cond
                                                                                      (or first (and first after-cursor))
                                                                                      {:lower after
                                                                                       :upper (inc (+ after first-n))}

                                                                                      (and last before-cursor)
                                                                                      {:lower (dec (+ before last-n))
                                                                                       :upper before}

                                                                                      :else {:lower 0
                                                                                             :upper 0})
                                                                                    {:readable-name readable-name
                                                                                     :user-id       authed-user-id
                                                                                     :statuses      statuses}))
          edges     (map-indexed (fn [index item] {:cursor (inc (+ after index)) ;; NOTE: inc to match SQL which indexes rows from 1
                                                   :node   item})
                                 analysis)
          edges'    (map (fn [item] (update item :cursor (comp encode-base64 str)))
                         edges)
          page-info {:has-next-page     (if-not (empty? edges)
                                          (< (-> edges last :cursor) total-count)
                                          false)
                     :has-previous-page (if-not (empty? edges)
                                          (< 1 (-> edges first :cursor))
                                          false)
                     :start-cursor      (-> edges' first :cursor)
                     :end-cursor        (-> edges' last :cursor)}]
      (clj->gql {:total-count total-count
                 :edges       edges'
                 :page-info   page-info})))

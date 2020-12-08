(ns api.mutations
  (:require [aws.s3 :as aws-s3]
            [aws.sqs :as aws-sqs]
            [api.models.continuous-tree :as continuous-tree-model]
            [clojure.string :as string]
            [shared.utils :refer [new-uuid clj->gql]]
            [taoensso.timbre :as log]))

(defn s3-url->id
  "returns the id if the url is in the proper bucket,
  proper user directory, and is a proper uuid"
  [url bucket user-id]
  (let [[_ id-path] (string/split url (re-pattern user-id))
        [id _] (-> id-path
                   (string/replace "/" "")
                   (string/split (re-pattern "\\.")))]
    (if (= (count id) 36)
      id
      (let [message "Invalid S3 url"]
        (log/error message {:url url
                            :bucket bucket
                            :user-id user-id})
        (throw (Exception. message))))))

(defn get-upload-urls
  [{:keys [s3-presigner authed-user-id bucket-name]} {:keys [files] :as args} _]
  (log/info "get-upload-urls" {:user/id authed-user-id :files files})
  (loop [files files
         urls []]
    (if-let [file (first files)]
      (let [{:keys [extension]} file
            uuid (new-uuid)]
        (recur (rest files)
               (conj urls (aws-s3/get-signed-url
                           s3-presigner
                           {:bucket-name bucket-name
                            :key (str authed-user-id "/" uuid "." extension)}))))
      urls)))

(defn upload-continuous-tree [{:keys [sqs workers-queue-url bucket-name authed-user-id db] :as ctx}
                              {tree-file-url :treeFileUrl :as args} _]
  (log/info "upload-continuous-tree" {:user/id authed-user-id
                                      :args args})
  (let [id (s3-url->id tree-file-url bucket-name authed-user-id)
        status :TREE_UPLOADED
        continuous-tree {:id id
                         :name (:name args)
                         :user-id authed-user-id
                         :tree-file-url tree-file-url
                         :status status}]

    (try
      (continuous-tree-model/upsert-tree! db continuous-tree)
      ;; sends message to worker to parse hpd levels and attributes
      (aws-sqs/send-message sqs workers-queue-url {:message/type :continuous-tree-upload
                                                   :id id
                                                   :user-id authed-user-id})
      {:id id
       :status status}
      (catch Exception e
        (log/error "Exception occured" {:error e})
        (continuous-tree-model/upsert-tree! db {:id id
                                                :status :ERROR})))))

;; TODO : update tree mutation (to set atts etc)
(defn update-continuous-tree
  [{:keys [authed-user-id db]} {id :id
                                x-coordinate-attribute-name :xCoordinateAttributeName
                                y-coordinate-attribute-name :yCoordinateAttributeName
                                hpd-level :hpdLevel
                                has-external-annotations :hasExternalAnnotations
                                timescale-multiplier :timescaleMultiplier
                                most-recent-sampling-date :mostRecentSamplingDate
                                :as args} _]
  (log/info "update continuous tree" {:user/id authed-user-id
                                      :args args})
  (try
    (let [status :PARSER_ARGUMENTS_SET]
      (continuous-tree-model/upsert-tree! db {:id id
                                              :x-coordinate-attribute-name x-coordinate-attribute-name
                                              :y-coordinate-attribute-name y-coordinate-attribute-name
                                              :hpd-level hpd-level
                                              :has-external-annotations has-external-annotations
                                              :timescale-multiplier timescale-multiplier
                                              :most-recent-sampling-date most-recent-sampling-date
                                              :status status})
      {:id id
       :status status})
    (catch Exception e
      (log/error "Exception occured" {:error e})
      (continuous-tree-model/upsert-tree! db {:id id
                                              :status :ERROR}))))

;; TODO : response schema
;; TODO : invoke worker
(defn start-continuous-tree-parser
  [{:keys [sqs workers-queue-url authed-user-id]} {id :id} _]

  (log/info "start-parser-execution" {:a args})

  (aws-sqs/send-message sqs workers-queue-url {:id id
                                               :user-id authed-user-id})

  #_{:id "ffffffff-ffff-ffff-ffff-ffffffffffff"
     :status :SENT})

(comment
  (s3-url->id "http://127.0.0.1:9000/minio/spread-dev-uploads/ffffffff-ffff-ffff-ffff-ffffffffffff/3eef35e9-f554-4032-89d3-deb347acd118.tre"
              "spread-dev-uploads"
              "ffffffff-ffff-ffff-ffff-ffffffffffff"))

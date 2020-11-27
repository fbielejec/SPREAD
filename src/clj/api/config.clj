(ns api.config
  (:require [shared.utils :refer [get-env-variable]]))

(defn load []
  (let [environment (or (get-env-variable "SPREAD_ENV") "dev")
        dev-env? (= "dev" environment)]
    {:logging {:level (or (keyword (get-env-variable "LOGGING_LEVEL")) :debug)}
     :api {:port (or (get-env-variable "API_PORT") "3001")}
     :aws (cond-> {:region (get-env-variable "AWS_REGION")
                   :access-key-id  (or (get-env-variable "API_AWS_ACCESS_KEY_ID") "AKIAIOSFODNN7EXAMPLE")
                   :secret-access-key (or (get-env-variable "API_AWS_SECRET_ACCESS_KEY") "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
                   :bucket-name (or (get-env-variable "BUCKET_NAME") "spread-dev-uploads")
                   :workers-queue-url (or (get-env-variable "WORKERS_QUEUE_URL") "http://localhost:9324/queue/workers")}
            dev-env? (assoc :sqs-host "localhost"
                            :sqs-port 9324
                            :s3-host "127.0.0.1"
                            :s3-port 9000))

     ;; :db {:dbname (get-env-variable "DB_DATABASE" :required)
     ;;      :port (get-env-variable "DB_PORT" :required)
     ;;      :user (get-env-variable "DB_USER" :required)
     ;;      :password  (get-env-variable "DB_PASSWORD" :required)
     ;;      :host (get-env-variable "DB_HOST" :required)}

     }))

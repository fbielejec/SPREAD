(ns ui.config
  (:require [shared.macros :refer [get-env-variable]]))

(def environment (get-env-variable "SPREAD_ENV"))
(def sentry-dsn (get-env-variable "SENTRY_DSN"))
(def google-client-id (get-env-variable "GOOGLE_CLIENT_ID" :required))
(def version "0.1.0")

(def default-config
  {:logging
   {:level    :info
    :console? true
    :sentry   false}

   :router
   {:routes        [["/" :route/splash]
                    ["/home" :route/home]]
    :default-route :route/splash
    :scroll-top?   true
    :html5?        true #_false}

   :graphql
   {:url "http://127.0.0.1:3001/api"}

   :root-url "http://localhost:8020"

   :google
   {:client-id    google-client-id
    :redirect-uri "http://localhost:8020/?auth=google"}

   })

(def dev-config
  (-> default-config
      (assoc-in [:logging :level] :debug)))

(def qa-config
  (-> default-config
      (assoc-in [:logging :level] :info)
      (assoc-in [:logging :sentry] {:dsn         sentry-dsn
                                    :min-level   :warn
                                    :environment "QA"
                                    :release     version})))

(defn load []
  (case environment
    "dev" dev-config
    "qa"  qa-config
    dev-config))

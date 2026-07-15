(ns bookpubops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean production-record
  logging request through intake -> advise -> govern -> decide ->
  approval -> commit at phase 1 (assisted-logging, always approval),
  then re-runs the same op at phase 3 (supervised-auto, clean + high
  confidence -> auto-commit), then a production-operation scheduling
  request and a distribution coordination request (both auto-commit
  clean at phase 3), then a content-concern flag (ALWAYS escalates, at
  any phase -- approve, then commit), then HARD-hold scenarios: an
  unregistered title, a title registered but not yet verified, a
  proposal whose own `:effect` is not `:propose`, and a proposal that
  has drifted into the permanently-excluded editorial-content-decision/
  legal-risk-clearance/rights-licensing-grant scope."
  (:require [langgraph.graph :as g]
            [bookpubops.advisor :as advisor]
            [bookpubops.store :as store]
            [bookpubops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "publishing-coordinator-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        coordinator-phase-1 {:actor-id "coord-1" :actor-role :publishing-coordinator :phase 1}
        coordinator-phase-3 {:actor-id "coord-1" :actor-role :publishing-coordinator :phase 3}
        actor (op/build db)]

    (println "== log-production-record title-1 (phase 1, escalates -- human approves) ==")
    (let [r (exec-op actor "t1" {:op :log-production-record :title-id "title-1"
                                  :patch {:manuscript-status "final draft received" :print-run 5000}} coordinator-phase-1)]
      (println r)
      (println "-- human publishing coordinator approves --")
      (println (approve! actor "t1")))

    (println "\n== log-production-record title-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-production-record :title-id "title-1"
                                  :patch {:isbn-assigned "978-4-00-000001-1" :print-run 5000}} coordinator-phase-3))

    (println "\n== schedule-production-operation title-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-production-operation :title-id "title-1"
                                  :patch {:stage "typesetting" :date "2026-08-01"}} coordinator-phase-3))

    (println "\n== coordinate-distribution title-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :coordinate-distribution :title-id "title-1"
                                  :patch {:channel "ebook" :release-date "2026-09-15"}} coordinator-phase-3))

    (println "\n== flag-content-concern title-1 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t5" {:op :flag-content-concern :title-id "title-1"
                                 :patch {:concern "possible unverified claim about a named individual in chapter 4" :confidence 0.92}} coordinator-phase-3)]
      (println r)
      (println "-- human publishing coordinator reviews & approves --")
      (println (approve! actor "t5")))

    (println "\n== log-production-record title-99 (unregistered title -> HARD hold) ==")
    (println (exec-op actor "t6" {:op :log-production-record :title-id "title-99"
                                  :patch {:manuscript-status "unknown"}} coordinator-phase-3))

    (println "\n== log-production-record title-3 (registered but unverified -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-production-record :title-id "title-3"
                                  :patch {:manuscript-status "draft"}} coordinator-phase-3))

    (println "\n== schedule-production-operation title-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (println (exec-op actor-direct "t8" {:op :schedule-production-operation :title-id "title-1"
                                           :patch {:stage "print-run"}} coordinator-phase-3)))

    (println "\n== log-production-record title-1, advisor drifts into editorial-content/rights scope -> HARD hold, permanent ==")
    (println (exec-op actor "t9" {:op :log-production-record :title-id "title-1"
                                   :out-of-scope? true
                                   :patch {}} coordinator-phase-3))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "\n== committed coordination log ==")
    (doseq [r (store/coordination-log db)] (println r))))

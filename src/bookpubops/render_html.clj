(ns bookpubops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger): this repo previously had NO demo page and no
  generator at all. This namespace drives the REAL actor stack
  (`bookpubops.operation` -> `bookpubops.governor` -> `bookpubops.store`)
  through a scenario adapted from this repo's own `bookpubops.sim` demo
  driver (`clojure -M:dev:run`, confirmed by actually running it before
  this file was written -- unlike `cloud-itonami-isic-851`'s
  `schoolops.sim`, this repo's own sim driver uses title ids that DO
  match `bookpubops.store/demo-data`'s seeded titles exactly, and every
  disposition it produces (auto-commit / escalate+approve / HARD hold,
  and the exact `:rule` on each hold) matches `bookpubops.governor`'s
  own documented checks precisely, so it was safe to reuse rather than
  author from scratch), trimmed to a representative subset (three clean
  phase-3 auto-commits covering all three auto-eligible ops, the one op
  that ALWAYS escalates regardless of phase or confidence -- approved by
  a human publishing coordinator -- and three DISTINCT HARD-hold reasons
  that never reach a human) and rendered deterministically -- no
  invented numbers, no timestamps in the page content, byte-identical
  across reruns against the same seed (verified by diffing two
  consecutive runs before shipping).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [bookpubops.store :as store]
            [bookpubops.operation :as op]
            [bookpubops.advisor :as advisor]
            [langgraph.graph :as g]))

;; ----------------------------- harness (unchanged across every repo
;; in this cluster -- do not rewrite, only copy) -----------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :publishing-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach, using ONLY real title ids from
  `bookpubops.store/demo-data`:

  title-1 (\"The Silent Orchard\", contract signed, rights cleared --
  `:registered? true :verified? true`) walks the full clean path at
  rollout phase 3 (`bookpubops.phase/default-phase`), the SAME phase
  this repo's own `bookpubops.sim` exercises: `:log-production-record`
  (manuscript status / print-run / ISBN-assignment logging),
  `:schedule-production-operation` (editing/typesetting/print-run
  scheduling) and `:coordinate-distribution` (print-run/ebook
  distribution coordination) are ALL THREE members of phase 3's `:auto`
  set (`bookpubops.phase/phases`), so each auto-commits with no human
  in the loop when the governor is clean and confidence is above the
  floor -- exactly the phase-3 auto-commit path this checklist item
  requires. `:flag-content-concern` is then proposed for the same
  title: it is a member of `bookpubops.governor/always-escalate-ops`
  AND deliberately absent from every phase's `:auto` set (two
  independent layers agreeing a content-risk concern always needs a
  human), so it escalates and is approved by a human publishing
  coordinator, producing one committed content-concern record.

  Then three DISTINCT HARD-hold reasons, none of which ever reach a
  human (a human approver cannot override a HARD violation --
  `bookpubops.governor/check`'s `hard` vector is computed before any
  escalation/approval routing even happens):
    - title-99 (never registered -- absent from
      `bookpubops.store/demo-data`'s title directory entirely):
      `:log-production-record` HARD-holds on `:title-unverified` --
      the advisor may not log production activity against a title the
      publishing house has no record of.
    - title-1, advisor wrapped to claim direct actuation
      (`:effect :commit` instead of `:effect :propose`, the same
      technique this repo's own `bookpubops.sim` uses to exercise this
      check): `:schedule-production-operation` HARD-holds on
      `:effect-not-propose` -- the governor independently enforces that
      every proposal is only ever a *proposal*, never a claim to
      directly actuate/commit outside governance.
    - title-1, request carries `:out-of-scope? true` (a documented test
      hook in `bookpubops.advisor/infer`, \"allow injecting
      scope-excluded content to exercise the governor's scope-exclusion
      block end-to-end\"): `:log-production-record` HARD-holds on
      `:scope-excluded` -- the governor independently re-scans every
      proposal for drift into finalizing-an-editorial-content-decision/
      legal-risk-clearance/rights-licensing-grant territory, regardless
      of the op or how clean everything else is.

  Returns the resulting store -- every field `render` below reads is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        actor-direct (op/build db {:advisor (reify advisor/Advisor
                                              (-advise [_ _ req]
                                                (assoc (advisor/infer nil req) :effect :commit)))})]

    ;; title-1: production-record logging (manuscript status / print-run /
    ;; ISBN assignment) -- clean, phase-3 auto-commit, no human in the loop.
    (exec! actor "t1-log" {:op :log-production-record :title-id "title-1"
                            :patch {:manuscript-status "final draft received" :print-run 5000}})

    ;; title-1: editing/typesetting/print-run scheduling -- clean, phase-3
    ;; auto-commit.
    (exec! actor "t1-schedule" {:op :schedule-production-operation :title-id "title-1"
                                 :patch {:stage "typesetting" :date "2026-08-01"}})

    ;; title-1: print-run/ebook distribution coordination -- clean, phase-3
    ;; auto-commit.
    (exec! actor "t1-distribute" {:op :coordinate-distribution :title-id "title-1"
                                   :patch {:channel "ebook" :release-date "2026-09-15"}})

    ;; title-1: content-risk concern flag -- ALWAYS escalates, at any
    ;; phase, regardless of confidence; approved by a human publishing
    ;; coordinator.
    (exec! actor "t1-flag" {:op :flag-content-concern :title-id "title-1"
                             :patch {:concern "possible unverified claim about a named individual in chapter 4"
                                     :confidence 0.92}})
    (approve! actor "t1-flag")

    ;; title-99: never registered -> HARD hold on :title-unverified,
    ;; never reaches a human.
    (exec! actor "t99-log" {:op :log-production-record :title-id "title-99"
                             :patch {:manuscript-status "unknown"}})

    ;; title-1, advisor claims direct actuation (:effect :commit) -> HARD
    ;; hold on :effect-not-propose, never reaches a human.
    (exec! actor-direct "t1-direct" {:op :schedule-production-operation :title-id "title-1"
                                      :patch {:stage "print-run"}})

    ;; title-1, request drifts into permanently-excluded editorial-content/
    ;; legal-clearance/rights-grant scope -> HARD hold on :scope-excluded,
    ;; never reaches a human.
    (exec! actor "t1-scope" {:op :log-production-record :title-id "title-1"
                              :out-of-scope? true :patch {}})

    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger title-id]
  (last (filter #(= (:title-id %) title-id) ledger)))

(defn- status-cell [ledger title-id]
  (let [f (last-fact-for ledger title-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- title-row [ledger {:keys [title-id name registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc title-id) (esc name)
          (if registered? "<span class=\"ok\">registered</span>" "<span class=\"critical\">unregistered</span>")
          (if verified? "<span class=\"ok\">verified</span>" "<span class=\"warn\">unverified</span>")
          (status-cell ledger title-id)))

(defn- ledger-row [{:keys [t op title-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc title-id)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) %)) (str/join ", "))
                    (some-> disposition name) ""))))

(defn- record-row [{:keys [op title-id value payload]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (name (or op :n-a))) (esc title-id) (esc (pr-str (dissoc value :title-id)))
          (if (:approved-by payload)
            (str "<span class=\"ok\">approved by " (esc (:approved-by payload)) "</span>")
            "<span class=\"ok\">auto-committed (phase 3, governor-clean)</span>")))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract
  ;; (`bookpubops.governor`/`bookpubops.phase`) -- documentation of fixed
  ;; behavior, not runtime telemetry, so it is legitimately hand-
  ;; described rather than derived from a live run.
  ["        <tr><td><code>:log-production-record</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &middot; manuscript status / print-run / ISBN-assignment logging</span></td></tr>"
   "        <tr><td><code>:schedule-production-operation</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &middot; editing/typesetting/print-run scheduling</span></td></tr>"
   "        <tr><td><code>:coordinate-distribution</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &middot; print-run/ebook distribution coordination</span></td></tr>"
   "        <tr><td><code>:flag-content-concern</code></td><td><span class=\"warn\">ALWAYS human approval, at any phase &middot; defamation/copyright/factual-accuracy concern, never auto-committed</span></td></tr>"])

(def ^:private hard-check-rows
  ;; Same rationale as `action-gate-rows` above -- these three checks are
  ;; cross-cutting (evaluated on every proposal regardless of op), not
  ;; per-op, so they are documented separately.
  ["        <tr><td><code>:title-unverified</code></td><td>target title must exist AND be independently <code>:registered?</code>/<code>:verified?</code> in the store -- never trusts the proposal's own claim</td></tr>"
   "        <tr><td><code>:effect-not-propose</code></td><td>every proposal's <code>:effect</code> must be <code>:propose</code> -- any other value is a claim to directly actuate outside governance</td></tr>"
   "        <tr><td><code>:scope-excluded</code></td><td>editorial-content-decision / legal-risk-clearance / rights-licensing-grant territory is permanently out of scope, scanned unconditionally on every proposal</td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        titles (store/all-titles db)
        title-rows (str/join "\n" (map (partial title-row ledger) titles))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        record-rows (str/join "\n" (map record-row (store/coordination-log db)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-5811 &middot; book publishing</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Book publishing operations coordination (ISIC 5811) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · editorial/legal/rights decisions permanently out of scope</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Titles</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>bookpubops.store</code> via <code>bookpubops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Title id</th><th>Name</th><th>Registered</th><th>Verified</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     title-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Committed coordination records</h2>\n"
     "    <p class=\"muted\">Production-record logging / production-operation scheduling / distribution coordination / content-concern flags actually committed to the SSoT by this run — never a finalized editorial-content, legal-clearance or rights-grant decision (permanently out of this actor's scope).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Title</th><th>Value</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     record-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (BookPubGovernor, per-op)</h2>\n"
     "    <p class=\"muted\">Rollout phase 3 (supervised-auto). A content-concern flag always needs a human to actually look at it, at every rollout phase.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (BookPubGovernor, cross-cutting HARD checks)</h2>\n"
     "    <p class=\"muted\">All three are permanent and un-overridable by any human approval — evaluated on every proposal regardless of op.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Rule</th><th>Check</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" hard-check-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Title</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "committed coordination records )")))

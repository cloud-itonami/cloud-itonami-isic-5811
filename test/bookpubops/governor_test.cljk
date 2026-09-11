(ns bookpubops.governor-test
  "Pure unit tests of `bookpubops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [bookpubops.governor :as gov]
            [bookpubops.store :as store]))

(def title-1 {:title-id "title-1" :name "The Silent Orchard" :registered? true :verified? true})
(def title-3 {:title-id "title-3" :name "Undisclosed Manuscript" :registered? true :verified? false})

(defn- clean-proposal [op title-id]
  {:op op :title-id title-id :summary "s" :rationale "routine publishing coordination"
   :cites [title-id] :effect :propose :value {} :confidence 0.85})

(deftest title-unregistered-is-hard
  (testing "no title record at all -> HARD hold"
    (let [s (store/mem-store {"title-1" title-1})
          verdict (gov/check {} nil (clean-proposal :log-production-record "unknown-title") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:title-unverified} (map :rule (:violations verdict)))))))

(deftest title-unverified-is-hard
  (testing "title registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"title-3" title-3})
          verdict (gov/check {} nil (clean-proposal :log-production-record "title-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:title-unverified} (map :rule (:violations verdict)))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"title-1" title-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-production-operation "title-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"title-1" title-1})
          verdict (gov/check {} nil (clean-proposal :finalize-manuscript "title-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest editorial-decision-content-is-hard-and-permanent
  (testing "a proposal whose rationale touches finalizing an editorial-content decision is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"title-1" title-1})
          poisoned (assoc (clean-proposal :log-production-record "title-1")
                          :rationale "finalized the editorial-content decision on the manuscript's ending"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legal-risk-clearance-content-is-hard
  (testing "a proposal touching a legal-risk/defamation clearance decision is HARD-blocked, same as editorial-content"
    (let [s (store/mem-store {"title-1" title-1})
          poisoned (assoc (clean-proposal :log-production-record "title-1")
                          :rationale "issued a legal-risk clearance for the defamation concern in chapter 4"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest rights-licensing-grant-content-is-hard
  (testing "a proposal touching a rights/licensing grant decision is HARD-blocked"
    (let [s (store/mem-store {"title-1" title-1})
          poisoned (assoc (clean-proposal :coordinate-distribution "title-1")
                          :summary "issued a rights grant to the foreign-language distribution partner")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest licensing-grant-value-content-is-hard
  (testing "a proposal touching a licensing grant decision embedded in the draft value is HARD-blocked"
    (let [s (store/mem-store {"title-1" title-1})
          poisoned (assoc (clean-proposal :schedule-production-operation "title-1")
                          :value {:decision "approve licensing grant for the audiobook edition"})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-content-concern-is-not-scope-excluded
  (testing "flagging an observed defamation/copyright/factual-accuracy concern (not a clearance decision) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"title-1" title-1})
          concern (assoc (clean-proposal :flag-content-concern "title-1")
                         :value {:concern "possible unverified factual claim about a named individual in chapter 4, and passage overlaps closely with a previously published work"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (defamation/copyright/factual-accuracy risk) is exactly what this op exists to surface"))))

(deftest legitimate-distribution-coordination-is-not-scope-excluded
  (testing "a clean distribution-coordination proposal that merely mentions it does not finalize contract terms never trips scope-exclusion"
    (let [s (store/mem-store {"title-1" title-1})
          clean (assoc (clean-proposal :coordinate-distribution "title-1")
                       :rationale "adjusts only the ebook release schedule; does not finalize any contract terms")
          verdict (gov/check {} nil clean s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "scheduling language must not accidentally self-trip the rights/licensing-grant block"))))

(ns bookpubops.advisor
  "BookPubAdvisor -- the *contained intelligence node* for the
  ISIC-5811 book-publishing operations-coordination actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: production-record logging (manuscript/print-run/ISBN-
  assignment data), production-operation scheduling (editing/typesetting/
  print-run scheduling), content-concern flagging (defamation/copyright/
  factual-accuracy risk), and distribution coordination (outbound print-run/
  ebook distribution). CRITICAL: it is a smart-but-untrusted advisor. It
  returns a *proposal* (with a rationale + the fields it cited), never a
  committed record and NEVER a direct actuation -- every proposal's
  `:effect` is always `:propose`. Every output is censored downstream by
  `bookpubops.governor` before anything touches the SSoT.

  This advisor NEVER drafts a finalized editorial-content decision (what
  the book says, whether it ships as written), a defamation/legal-risk
  clearance decision, or a rights/licensing grant decision -- those are
  permanently out of scope for this actor, not merely un-implemented.
  `bookpubops.governor`'s `scope-exclusion-violations` independently
  re-scans every proposal for exactly this failure mode (a compromised or
  confused advisor drifting into scope it must never touch) and
  HARD-holds it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :title-id   str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-production-record
  "Draft a production-record log entry: manuscript status, print-run
  quantity, ISBN-assignment data. Pure back-office logging of observed
  production facts -- never an editorial-content judgment."
  [_db {:keys [title-id patch]}]
  {:op         :log-production-record
   :title-id   title-id
   :summary    (str title-id " の制作記録（原稿状況/刷り部数/ISBN割当）を記録: " (pr-str (keys patch)))
   :rationale  "原稿・印刷部数・ISBN割当などの制作事実の記録のみ。編集内容の判断なし。"
   :cites      [title-id]
   :effect     :propose
   :value      (merge {:title-id title-id} patch)
   :confidence 0.94})

(defn- propose-production-schedule
  "Draft an editing/typesetting/print-run scheduling proposal (a calendar
  entry, never a direct press-line dispatch or a sign-off on the edited
  content itself)."
  [_db {:keys [title-id patch]}]
  {:op         :schedule-production-operation
   :title-id   title-id
   :summary    (str title-id " の制作工程（編集/組版/印刷）スケジュールを提案: " (pr-str (keys patch)))
   :rationale  "編集・組版・印刷工程の日程調整のみ。編集内容そのものの確定判断ではない。"
   :cites      [title-id]
   :effect     :propose
   :value      (merge {:title-id title-id} patch)
   :confidence 0.89})

(defn- propose-distribution-coordination
  "Draft an outbound print-run/ebook distribution coordination proposal
  (shipment/release scheduling only -- never a rights/licensing grant to a
  distribution partner)."
  [_db {:keys [title-id patch]}]
  {:op         :coordinate-distribution
   :title-id   title-id
   :summary    (str title-id " の配本/電子版配信の調整を提案: " (pr-str (keys patch)))
   :rationale  "印刷部数の配本・電子書籍配信スケジュールの調整のみを行い、契約条件の確定は伴わない。"
   :cites      [title-id]
   :effect     :propose
   :value      (merge {:title-id title-id} patch)
   :confidence 0.91})

(defn- propose-content-concern
  "Surface a content-risk concern (potential defamation, copyright/
  plagiarism concern, or factual-accuracy concern observed in a
  manuscript or proof) for HUMAN triage. This op ALWAYS escalates in
  `bookpubops.governor` -- never auto-committed at any phase -- regardless
  of how confident the advisor is that the concern is real."
  [_db {:keys [title-id patch]}]
  {:op         :flag-content-concern
   :title-id   title-id
   :summary    (str title-id " のコンテンツ懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "名誉毀損リスク・著作権/剽窃の疑い・事実確認の懸念に関する観察事実の報告。常に人間の確認・判断が必要。"
   :cites      [title-id]
   :effect     :propose
   :value      (merge {:title-id title-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-production-record (propose-production-record _db request)
                   :schedule-production-operation (propose-production-schedule _db request)
                   :coordinate-distribution (propose-distribution-coordination _db request)
                   :flag-content-concern (propose-content-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually finalized the editorial-content decision and issued a rights grant")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :title-id (:title-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))

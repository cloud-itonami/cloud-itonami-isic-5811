# cloud-itonami-isic-5811

**Book publishing** — ISIC Rev.4 class 5811.

A coordination-only actor for book publishing back-office operations, behind an independent Governor that earns advisor trust through structured oversight: proposal → advise → govern → decide → commit|hold|escalate.

## Features

- **Closed proposal-op allowlist**: log-production-record, schedule-production-operation, coordinate-distribution, flag-content-concern (all `:effect :propose`).
- **Three HARD governor checks** (permanent, un-overridable):
  1. **Title verified** — target title (a book title under contract) must exist AND be registered/verified in the store before any proposal for it may commit or escalate.
  2. **Effect is :propose** — any other `:effect` value is rejected.
  3. **Scope exclusion** — finalizing an editorial-content decision, a defamation/legal-risk clearance decision, and a rights/licensing grant decision are permanently blocked, regardless of confidence or op.
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: production-record logging only (approval-gated)
  - Phase 2: + production-operation scheduling, distribution coordination (approval-gated)
  - Phase 3: auto-commits clean, high-confidence proposals (content concerns always escalate)
- **Append-only audit ledger** — every decision is an immutable log entry.
- **langgraph-clj StateGraph** — one request = one supervised run; human-in-the-loop via `interrupt-before`.

## CRITICAL scope exclusions

This actor coordinates the back-office operations of a book publishing house:
manuscript/print-run/ISBN-assignment production-record logging,
editing/typesetting/print-run scheduling proposals, outbound print-run/ebook
distribution coordination, and content-risk-concern flagging (defamation,
copyright, factual-accuracy).

**This actor does NOT:**
- Finalize an editorial-content decision (what the book actually says, whether it ships as written).
- Issue a defamation/legal-risk clearance decision.
- Grant rights or licensing terms to an author, translator, distributor, or any other party.

Every proposal is `:effect :propose` only. `:flag-content-concern` always
escalates to a human, at every phase, regardless of confidence — this
actor never self-clears a content-risk concern it raises.

## Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:dev:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

## Test suite

- `test/bookpubops/governor_test.clj` — unit tests of governor hard checks and scope exclusion
- `test/bookpubops/advisor_test.clj` — advisor proposal shape and consistency
- `test/bookpubops/phase_test.clj` — rollout phase logic
- `test/bookpubops/governor_contract_test.clj` — full graph integration, audit trail
- `test/bookpubops/store_contract_test.clj` — Store protocol and MemStore implementation

## Modules

- `bookpubops.store` — SSoT (MemStore, String-keyed title directory, append-only ledger)
- `bookpubops.advisor` — contained intelligence node (mock + real-LLM seam)
- `bookpubops.governor` — independent compliance layer
- `bookpubops.phase` — staged rollout (0→3)
- `bookpubops.operation` — langgraph-clj StateGraph
- `bookpubops.sim` — demo driver

## License

AGPL-3.0-or-later. See LICENSE file.

## Governance

This actor is part of the cloud-itonami Wave 4 (human-facing/personal-services) fleet. See ADR-2607121000, ADR-2607152500, and the ISIC-5811 coverage ADR for design decisions.

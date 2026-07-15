# Contributing to cloud-itonami-isic-5811

Contributions should preserve the actor's scope: back-office coordination only,
with CRITICAL exclusions of finalizing editorial-content decisions,
legal-risk/defamation clearance decisions, and rights/licensing grant
decisions (see README.md).

- All code must be .cljc (portable Clojure, no JVM-only constructs).
- Tests must pass: clojure -M:test
- Commit messages should link to relevant ADRs or issues.

**This actor does NOT:**
- Finalize an editorial-content decision (what the book actually says, whether it ships as written).
- Issue a defamation/legal-risk clearance decision.
- Grant rights or licensing terms to an author, translator, distributor, or any other party.

Contributions that cross these boundaries will be rejected.

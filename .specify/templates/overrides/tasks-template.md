# Tasks: [FEATURE]

**Spec**: [PATH]  
**Plan**: [PATH]

## Rules

- Every task must be independently verifiable.
- Keep one concern per task and one primary module per PR.
- Mark dependencies explicitly.
- Put contract and migration work before consumers.
- Put tests beside the behavior they validate, not in a final cleanup bucket.
- Include privacy, security, observability and documentation tasks when applicable.

## Phase 1 — Contracts and foundations

- [ ] T001 [P?] [module] Define or update versioned contract and compatibility notes.
- [ ] T002 [module] Add required migration or configuration.

## Phase 2 — Behavior

- [ ] T003 [module] Implement one domain/application behavior.
- [ ] T004 [module] Add unit and integration tests for T003.

## Phase 3 — Integration

- [ ] T005 [module] Connect producer and consumer through the approved contract.
- [ ] T006 [module] Add contract/end-to-end verification.

## Phase 4 — Operational readiness

- [ ] T007 Add logs, metrics, correlation and actionable failure reasons.
- [ ] T008 Verify authorization, sensitive-data handling, retention and deletion.
- [ ] T009 Update docs, ADRs and runbooks.

## Phase 5 — Convergence

- [ ] T010 Run consistency analysis against spec, plan and constitution.
- [ ] T011 Record remaining gaps as new atomic tasks; do not hide them in the PR.

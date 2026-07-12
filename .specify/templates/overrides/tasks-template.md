# Tasks: [FEATURE]

**Spec**: [LINK]  
**Plan**: [LINK]

## Task rules

Every task MUST:

- use an ID `T###`;
- have one primary outcome;
- identify module/files affected;
- state dependencies;
- map to one or more requirements or acceptance scenarios;
- include verification evidence;
- be suitable for one focused pull request unless marked `[groupable]`.

Format:

```text
- [ ] T001 [P?] [US?] Outcome — module/path
      Depends on: none | T###
      Traces to: FR-###, SC-###, Scenario #
      Verify: exact test, command or observable behaviour
      Roles: required reviewers/gates
```

## Phase 1 — Walking skeleton

- [ ] T001 [US1] [Atomic outcome] — [path]
      Depends on: none
      Traces to: [requirements]
      Verify: [evidence]
      Roles: Product, QA, [conditional roles]

## Phase 2 — Independent user story increments

### User Story 1

- [ ] T002 [US1] [Atomic outcome] — [path]
      Depends on: T001
      Traces to: [requirements]
      Verify: [evidence]
      Roles: [roles]

## Phase 3 — Hardening

- [ ] T900 [Cross-cutting but single outcome] — [path]
      Depends on: [tasks]
      Traces to: [NFR]
      Verify: [evidence]
      Roles: QA, Security/Privacy

## Traceability check

| Requirement/scenario | Covered by tasks | Verification |
|---|---|---|
| FR-001 | T### | [test/evidence] |

## Parallelization notes

Only tasks marked `[P]` may run in parallel. They MUST not modify the same files, schema or contract and MUST not depend on unmerged behaviour.

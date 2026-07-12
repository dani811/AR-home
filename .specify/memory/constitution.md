# AR Home Engineering Constitution

## 1. Specification is the source of truth

Every product change MUST originate from an approved specification. The implementation, tests, API contracts and documentation MUST remain traceable to the feature spec and plan. Code that changes intended behaviour without updating the specification is non-compliant.

## 2. Atomic delivery

Tasks and pull requests MUST have one primary outcome. Dependencies MUST be explicit. Infrastructure, refactors and product behaviour MUST be separated unless coupling is technically unavoidable and documented in the plan.

## 3. Spatial correctness over visual plausibility

Generated maps, routes, anchors and furniture identities MUST expose confidence and provenance. The system MUST NOT present estimated geometry or recognition as exact. Coordinate systems, units, transforms and algorithm versions MUST be explicit and testable.

## 4. Privacy and local-first capture

Interior imagery, depth, geometry and inventory data are sensitive. Collection MUST be minimal, consent-aware and encrypted in transit. Specs MUST define retention, deletion, access control and whether processing is local or remote. Raw capture data MUST NOT be retained indefinitely by default.

## 5. Contract-first interoperability

Boundaries between mobile capture, backend, spatial workers, frontend and Home Assistant MUST use versioned contracts. Breaking contract changes require migration or explicit version negotiation. Coordinate conventions and object-storage ownership MUST be defined in the contract.

## 6. Testable vertical slices

The preferred delivery unit is an end-to-end vertical slice with observable user value. Each plan MUST define unit, integration and contract tests, plus spatial golden datasets when algorithms are involved. A feature is not complete when it only works on a developer machine.

## 7. Modular monolith before distributed complexity

The Java backend starts as a modular monolith. New deployable services require evidence that isolation, scaling or technology constraints justify the operational cost. Spatial processing workers remain separate because their runtime and compute model differ materially.

## 8. Human validation for uncertain automation

Furniture recognition, room extraction and route generation MUST support manual correction. Automated decisions affecting inventory location MUST retain observations and confidence so a user can understand and override them.

## Quality gates

Before implementation:

- spec has measurable acceptance scenarios;
- ambiguities affecting data, privacy, geometry or UX are resolved;
- plan passes architecture and contract review;
- tasks are atomic and dependency ordered;
- `speckit-analyze` reports no unresolved critical inconsistency.

Before merge:

- relevant tests pass;
- spec/plan/tasks reflect the final behaviour;
- API and spatial contracts are versioned;
- privacy and security impacts are reviewed;
- `speckit-converge` has no untracked mandatory work.

## Governance

This constitution overrides convenience and local implementation preferences. Amendments require a dedicated pull request explaining the motivation, affected workflows and migration impact. Feature pull requests MUST NOT silently amend these principles.

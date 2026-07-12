# AR Home Constitution

## 1. Spec before code
Every feature starts from an approved specification that states user value, scope, non-goals, acceptance criteria and measurable outcomes. Implementation must not begin while material ambiguities remain.

## 2. Atomic delivery
Each task and pull request must represent one independently reviewable change. The default limit is 300 net changed lines; larger changes require an explicit justification in the PR. Infrastructure, domain changes and UI work must not be mixed without necessity.

## 3. Contract-first boundaries
Public APIs, events and spatial payloads are versioned before implementation. Breaking changes require a migration path and an architecture decision record. Coordinate systems, units, timestamps and identifiers must be explicit.

## 4. Testable behavior
Acceptance criteria must be executable or objectively verifiable. Backend endpoints require automated tests. Spatial algorithms require deterministic fixtures and tolerance-based assertions. Bugs require a regression test whenever feasible.

## 5. Privacy and security by design
Indoor imagery, geometry, device poses and inventory data are sensitive. Specifications and plans must address data minimization, retention, encryption, authorization, deletion and offline behavior. Raw captures are never exposed publicly by default.

## 6. Observable and reversible systems
Long-running capture and processing work must expose status, failure reasons and correlation identifiers. Migrations and deployments need rollback strategies. Jobs must be idempotent and safe to retry.

## 7. Spatial correctness over visual plausibility
The system must distinguish measured, inferred and manually confirmed spatial data. Confidence, provenance and algorithm version must be stored. A visually convincing result is not accepted if its coordinate semantics are undefined.

## 8. Modular monolith first
The backend starts as a modular monolith. Services are extracted only with evidence of an operational or scaling need. Python/C++ spatial workers remain separate because their runtime and dependency profile are materially different.

## 9. Human validation
Automatic reconstruction and recognition must support review and correction. The system must preserve manual overrides and must not silently replace user-confirmed spatial identities.

## 10. Definition of done
A task is done only when code, tests, documentation, contracts, migrations, security considerations and operational impact are complete as applicable; CI is green; and the implementation still matches spec, plan and tasks.

## Governance
- This constitution overrides feature-level convenience.
- Changes require a dedicated PR and rationale.
- Architectural changes require an ADR in `docs/adr/`.
- Spec Kit artifacts in `specs/` are product records and evolve with intended behavior.

# AR Home Roles and Review Gates

These roles are responsibilities, not necessarily separate people or agents. One contributor may hold several roles, but each required gate must be addressed explicitly.

## Product Owner

Owns problem framing, user scenarios, scope and acceptance outcomes.

Required in: `spec.md`.

Rejects when:
- the feature describes technology instead of user value;
- success cannot be observed or measured;
- edge cases for homes, warehouses or shops are ignored.

## Solution Architect

Owns module boundaries, contracts, data ownership, deployment topology and ADRs.

Required in: `plan.md`.

Rejects when:
- a new service lacks operational justification;
- versioning, migration or failure behaviour is absent;
- frontend, backend and spatial worker responsibilities overlap ambiguously.

## Backend Engineer

Owns Java/Spring modules, persistence, API behaviour, idempotency and asynchronous orchestration.

Required when: `backend/**`, database, queue or object-storage APIs change.

Gate:
- transactional boundaries documented;
- API errors structured;
- idempotency and concurrency considered;
- unit and integration tests included.

## Frontend and 3D Engineer

Owns Angular architecture, Three.js scene lifecycle, 2D/3D synchronization, accessibility and performance.

Required when: `frontend/**` or visual navigation changes.

Gate:
- state ownership is explicit;
- WebGL resources are disposed;
- large models use progressive loading;
- non-3D fallback and keyboard interaction are considered.

## XR Capture Engineer

Owns ARCore, ARKit, RoomPlan, WebXR fallback, sensors, coordinate transforms and offline upload.

Required when: `capture-mobile/**` or capture contracts change.

Gate:
- device capability matrix documented;
- tracking loss and recovery specified;
- timestamps and coordinate frames are unambiguous;
- battery, storage and offline behaviour are covered.

## Spatial Vision Engineer

Owns keyframes, pose graph, reconstruction, segmentation, multiview fusion, confidence and algorithm provenance.

Required when: `spatial-processing/**`, geometry or recognition changes.

Gate:
- input/output datasets versioned;
- metrics and acceptance thresholds defined;
- confidence and failure modes exposed;
- golden dataset or reproducible evaluation included.

## QA and Test Engineer

Owns acceptance traceability, test pyramid, fixtures, contract tests and end-to-end validation.

Required for every feature.

Gate:
- each acceptance scenario maps to evidence;
- failure and retry paths are tested;
- tests are deterministic;
- flaky tests block completion.

## Security and Privacy Reviewer

Owns threat modelling, authorization, secrets, retention and sensitive indoor data handling.

Required when capture data, identity, sharing, cloud processing or external integrations change.

Gate:
- data classification and retention defined;
- least privilege applied;
- deletion and export paths specified;
- logs exclude sensitive imagery, tokens and precise location data.

## Role assignment in a feature

Every `plan.md` must contain a role matrix:

| Role | Required | Owner | Gate evidence |
|---|---:|---|---|
| Product | yes | TBD | acceptance scenarios |
| Architecture | conditional | TBD | plan/ADR |
| Backend | conditional | TBD | tests/API contract |
| Frontend/3D | conditional | TBD | UI/performance evidence |
| XR Capture | conditional | TBD | device/tracking evidence |
| Spatial Vision | conditional | TBD | metrics/golden dataset |
| QA | yes | TBD | traceability matrix |
| Security/Privacy | conditional | TBD | threat/privacy review |

# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Spec**: [LINK] | **Date**: [DATE]

## Summary

[Technical approach and boundaries.]

## Constitution check

- Specification is source of truth: [PASS/FAIL + evidence]
- Atomic delivery: [PASS/FAIL]
- Spatial correctness/provenance: [PASS/NA]
- Privacy/local-first: [PASS/NA]
- Contract-first interoperability: [PASS/NA]
- Testable vertical slice: [PASS/FAIL]
- Modular monolith constraint: [PASS/NA]
- Human correction for uncertainty: [PASS/NA]

Unresolved failures block task generation.

## Technical context

- Modules affected:
- Runtime/languages:
- Storage and ownership:
- External integrations:
- Device capability requirements:
- Performance budget:

## Architecture and boundaries

### Components

[Responsibilities and dependency direction.]

### Contracts

- Contract/schema:
- Versioning strategy:
- Idempotency:
- Error model:
- Coordinate system and units: [or N/A]

### Data model and migrations

[Entities, ownership, migration and deletion impact.]

### Failure and recovery

[Retries, partial upload, tracking loss, processing failure, rollback.]

## Security and privacy

- Threats:
- Authorization:
- Sensitive data:
- Retention/deletion:
- Logging restrictions:

## Test strategy

- Unit:
- Integration:
- Contract:
- End-to-end:
- Spatial golden dataset/metrics: [or N/A]

## Role matrix

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

## Delivery slices

Each slice must be independently demonstrable and map to acceptance scenarios.

1. [Walking skeleton]
2. [Increment]

## Decisions and ADRs

- [Decision, alternatives, rationale]

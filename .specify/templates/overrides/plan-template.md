# Implementation Plan: [FEATURE]

**Spec**: [PATH]  
**Branch**: [BRANCH]  
**Owners / roles**: [ROLES]

## Constitution check

Confirm compliance with every applicable principle in `.specify/memory/constitution.md`. Any exception must be explicit and approved before implementation.

## Technical context

- Runtime and versions:
- Modules affected:
- Persistence and migrations:
- External services:
- Device/platform constraints:
- Performance budgets:

## Architecture and boundaries

Describe module ownership, data flow and why the design belongs in existing boundaries. Link required ADRs.

## Contracts

List APIs, events and schemas. Define versions, units, coordinate frames, timestamps, idempotency keys and compatibility strategy.

## Security and privacy design

Cover threat model, authorization, sensitive-data flow, encryption, retention, deletion, logging redaction and consent.

## Observability and operations

Define logs, metrics, traces, correlation IDs, job states, failure reasons, retries, rollback and support diagnostics.

## Test strategy

- Unit:
- Integration:
- Contract:
- End-to-end:
- Spatial fixtures and tolerances:
- Device/browser matrix:

## Delivery slices

Break implementation into independently reviewable PRs. Default maximum: 300 net changed lines per PR unless justified.

## Risks and decisions

| Risk / decision | Mitigation / outcome | Owner |
|---|---|---|

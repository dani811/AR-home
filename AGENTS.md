# AR Home agent instructions

## Mandatory workflow

For feature work, follow Spec Kit in this order: constitution, specify, clarify, plan, tasks, analyze, implement, converge.

Do not implement a feature without approved `spec.md`, `plan.md` and `tasks.md` artifacts unless the change is a narrowly scoped emergency fix. Emergency fixes still require a regression test and follow-up spec reconciliation.

## Operating rules

- Read `.specify/memory/constitution.md` before planning or coding.
- Select accountable roles from `docs/engineering/roles.md` based on impact.
- Keep pull requests atomic; target no more than 300 net changed lines unless justified.
- Version API, event and spatial schemas before implementing consumers.
- Never leave coordinate systems, units, timestamps or spatial provenance implicit.
- Treat indoor imagery, geometry, poses and inventory as sensitive data.
- Add tests alongside behavior.
- Record cross-module architectural decisions in `docs/adr/`.
- Preserve manual spatial corrections and user-confirmed identities.
- Do not install community Spec Kit components without source review.

## Module ownership

- `backend/`: Backend Engineer; Chief Architect for boundary changes.
- `frontend/`: Frontend and 3D Engineer.
- `capture-mobile/`: XR Capture Engineer and Security/Privacy Reviewer.
- `spatial-processing/`: Spatial AI Engineer.
- `infrastructure/`: Platform Engineer and Security/Privacy Reviewer.
- `contracts/`: Chief Architect plus every affected producer/consumer.

## Completion gate

Before marking a task complete, verify implementation against the constitution, feature spec, plan, tasks and acceptance criteria. Record discovered gaps as explicit tasks.

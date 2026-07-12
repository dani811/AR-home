# Engineering roles

Roles are review perspectives, not job titles. One person may perform several roles, but every feature must name the accountable roles in its plan.

## Product Owner
Owns user value, scope, non-goals, acceptance criteria and prioritization. Rejects technically elegant work that does not solve the stated problem.

## Chief Architect
Owns system boundaries, ADRs, cross-module contracts and technical risk. Reviews any change affecting more than one bounded context or runtime.

## Backend Engineer
Owns Spring Boot modules, persistence, APIs, transactions, idempotency and domain invariants.

## Frontend and 3D Engineer
Owns Angular architecture, Three.js scenes, accessibility, performance budgets and state synchronization between 2D, 3D and photographic routes.

## XR Capture Engineer
Owns ARCore, ARKit, RoomPlan, WebXR fallback, camera intrinsics, poses, IMU, depth, tracking quality and offline upload behavior.

## Spatial AI Engineer
Owns keyframe selection, pose optimization, reconstruction, semantic extraction, furniture recognition, confidence and algorithm reproducibility.

## Platform Engineer
Owns Docker, CI/CD, environments, secrets, observability, backups, queues and object storage.

## QA Engineer
Owns acceptance traceability, test strategy, regression coverage, fixtures, device matrix and non-functional validation.

## Security and Privacy Reviewer
Owns threat modelling, authorization, encryption, retention, deletion, consent, GDPR impact and safe handling of indoor imagery and geometry.

## Required review matrix

| Change | Required roles |
|---|---|
| API or domain | Product Owner, Backend, QA |
| Cross-module contract | Chief Architect, affected engineers, QA |
| Mobile capture | XR Capture, Security/Privacy, QA |
| Spatial processing | Spatial AI, Chief Architect, QA |
| 2D/3D viewer | Frontend/3D, Product Owner, QA |
| Infrastructure/security | Platform, Security/Privacy, Chief Architect |

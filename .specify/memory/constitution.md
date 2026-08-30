# AR Home Constitution

## Mission
AR Home exists to make physical storage searchable and navigable. A successful system lets a user register an object in an indoor environment, leave or restart the application, return from a different position, recover a trustworthy device pose, and be guided to the correct storage location.

The product is not a 3D-scanning demo. Geometry, reconstruction, AR overlays and AI are means to recover physical objects reliably.

## 1. Spec before code
Every feature starts from an approved specification that states user value, scope, non-goals, acceptance criteria and measurable outcomes. Implementation must not begin while material ambiguities remain.

## 2. Atomic delivery
Each task and pull request represents one independently reviewable concern. The default target is at most 300 net changed lines; larger changes require explicit justification. Infrastructure, domain, mobile/XR, spatial processing and UI changes are not mixed unless the contract itself is the concern under review.

## 3. Contract-first boundaries
Public APIs, events and spatial payloads are versioned before implementation. Breaking changes require a migration path and an ADR. Coordinate frames, axis conventions, units, timestamps, identifiers, confidence and provenance are explicit.

## 4. Spatial correctness over visual plausibility
Measured, inferred and manually confirmed spatial data are distinct. Every pose-producing subsystem records source, confidence, timestamp and algorithm/provider version. A visually convincing overlay is not accepted if its coordinate semantics or uncertainty are undefined.

## 5. Localization is a replaceable capability
Business and inventory domains must not depend directly on ARCore, Cloud Anchors, markers, COLMAP, Wi-Fi RTT, UWB or any single localization implementation.

Localization providers expose a common pose contract equivalent to:

```text
PoseEstimate
- transform: 6DoF pose in an explicit coordinate frame
- confidence
- uncertainty/covariance when available
- source/provider
- timestamp
- provider/algorithm version
```

The product may combine multiple providers, but downstream navigation consumes the normalized result.

## 6. Capability-driven mobile behavior
ARCore, depth sensing, Wi-Fi RTT, UWB and other hardware/platform capabilities are optional accelerators unless a product tier explicitly requires them. The Android client detects supported capabilities at runtime and degrades deliberately.

Unsupported capabilities must produce an actionable fallback, not undefined behavior.

## 7. Relocalization across sessions is the primary technical gate
Relative motion tracking alone is insufficient. The system is considered spatially viable only when, after a mapped environment has been recorded, a new application/session start from a different position can recover a sufficiently accurate global pose and maintain useful tracking while the user moves toward a target.

The MVP viability gate is defined in `docs/mvp-viability-gate.md`.

## 8. Global navigation and final approach may use different precision regimes
The system does not require centimetric precision over an entire dwelling if the product objective can be achieved through hierarchical refinement.

A valid strategy is:

```text
coarse/global localization
→ room/route navigation
→ target furniture recognition/relocalization
→ local furniture coordinate frame
→ precise compartment guidance
```

Precision requirements are therefore specified per navigation stage and validated against user-visible outcomes.

## 9. Storage hierarchy and navigation graph are separate models
Containment answers "where is this object stored?". Navigation answers "how do I reach it?". They must not be represented by the same graph.

The containment model supports arbitrary recursive depth. The navigation model independently represents traversable connections, approach poses and routing constraints.

## 10. Buy/integrate before rebuilding spatial infrastructure
The project must prefer proven, suitably licensed components for VIO, SfM, feature extraction, matching and reconstruction until evidence shows they cannot satisfy the product requirements.

A proprietary SLAM/VIO implementation requires an ADR documenting the measured limitation of available alternatives, expected competitive advantage, maintenance cost and licensing implications.

## 11. Human validation remains authoritative
Automatic reconstruction, detection and recognition must support review and correction. User-confirmed spatial identities and placements are never silently replaced by lower-confidence inference.

## 12. Privacy and security by design
Indoor imagery, geometry, device trajectories, poses and inventory data are sensitive. Specifications and plans address minimization, consent, retention, deletion, encryption, authorization, logging redaction and offline behavior.

Raw captures are private by default and must not be exposed through public object URLs or unauthenticated APIs.

## 13. Modular monolith first; specialized workers where justified
The Java backend starts as a modular monolith. Service extraction requires evidence of an operational, runtime or scaling need. Spatial AI/GPU processing may remain in separate Python/C++ workers because its dependency and execution profile is materially different.

## 14. Observable, reproducible and reversible systems
Long-running mapping/relocalization processing exposes status, failure reasons and correlation identifiers. Spatial algorithm outputs are reproducible from versioned inputs and model/provider versions where practical. Jobs are idempotent and safe to retry. Migrations and deployments have rollback strategies.

## 15. MVP is judged by object retrieval
The MVP is not accepted merely because it can capture a room, render a mesh, show camera overlays or persist inventory.

The minimum product story is:

```text
MAP → REGISTER → CLOSE/RESTART → RETURN → RELOCALIZE → NAVIGATE → FIND
```

A demo must prove that flow end to end in a mapped indoor environment using a supported Android device.

## 16. Definition of done
A task is done only when applicable code, tests, contracts, migrations, documentation, privacy/security analysis and operational diagnostics are complete; CI is green; and implementation matches the constitution, specification, plan and tasks.

Spatial behavior additionally requires device/fixture evidence and stated tolerances.

## Governance
- This constitution overrides feature-level convenience.
- Amendments require a dedicated governance PR and rationale.
- Cross-module architectural decisions require an ADR under `docs/adr/`.
- Product features follow: `constitution → specify → clarify → plan → tasks → analyze → implement → converge`.
- Remaining uncertainty is represented as an explicit experiment or task; it is not hidden behind optimistic assumptions.

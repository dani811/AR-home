# MVP Spatial Retrieval Viability Gate

## Purpose

This document defines the technical go/no-go experiment that must succeed before AR Home invests significantly in inventory UX, automatic recognition, Home Assistant integration or production scaling.

The question is intentionally narrow:

> After mapping a small indoor environment, can a fresh Android session started from a different position recover a useful global 6DoF pose and maintain enough spatial consistency to guide a user to a previously registered storage target?

If the answer is no, work remains focused on localization. If the answer is yes, the product can progress to the end-to-end MVP.

## Demo environment

The first controlled environment must contain at least:

- two distinct rooms connected by a doorway or corridor;
- one target furniture instance in the destination room;
- one nested storage path with at least three containment levels below the room;
- ten registered inventory objects, with at least one designated demo target;
- visually distinct and visually repetitive areas so relocalization failure modes are observable.

Example target:

```text
Home
└── Bedroom
    └── Wardrobe
        └── Right module
            └── Drawer 2
                └── Organizer
                    └── Compartment A
                        └── Passport
```

## Required end-to-end story

The acceptance demo executes the following without editing persisted spatial data between steps:

1. Map the demo environment.
2. Register the target furniture/storage hierarchy.
3. Register the target object and its placement.
4. Close the application and terminate its spatial session.
5. Move to a different starting position in the mapped environment.
6. Start a fresh application/spatial session.
7. Search for the target object.
8. Relocalize the device in the persistent environment coordinate frame.
9. Guide the user through the environment toward the destination room/furniture.
10. Refine localization at the destination when required.
11. Identify the correct final storage container and display the target object record.

A demo that depends on preserving the original AR session does not pass.

## Phase A — Device capability spike

Before implementing persistent localization, the Android probe records actual capabilities of the selected test device rather than relying on model assumptions.

Required observations:

- Android/device identity and OS version;
- rear camera configurations and usable frame rates;
- camera intrinsics when available;
- accelerometer availability and sampling;
- gyroscope availability and sampling;
- rotation-vector availability;
- magnetometer availability;
- ARCore availability and session creation result;
- ARCore tracking state and tracking failure reason;
- ARCore Depth AUTOMATIC support;
- raw depth/confidence availability where exposed;
- CPU camera-frame acquisition while spatial tracking is active;
- Wi-Fi RTT feature support;
- runtime thermal/performance observations during a representative walk.

The result is stored as a machine-readable capability report plus a human-readable test summary.

### Phase A pass condition

At least one viable tracking/capture path exists that can provide synchronized camera observations and relative device motion suitable for the persistent-localization experiment.

Failure does not terminate the project; it selects a lower-level CameraX/IMU path instead of ARCore for the next experiment.

## Phase B — Persistent relocalization experiment

### Mapping run

A user walks the environment once or more while the system records versioned spatial observations such as:

- selected RGB keyframes;
- timestamp;
- camera intrinsics;
- relative/VIO pose when available;
- tracking quality;
- optional depth;
- optional IMU metadata;
- optional coarse radio observations used only as priors.

The processing pipeline creates a persistent environment map/reference representation.

### Localization runs

Perform at least 20 fresh-session trials distributed across multiple starting positions and orientations. At least five trials must begin in each of two different rooms.

Each trial:

1. starts with no retained volatile AR session state;
2. begins at a recorded ground-truth/reference test point or a manually measured tolerance zone;
3. attempts global relocalization;
4. records time to first accepted pose;
5. walks a route to the target room/furniture;
6. records tracking losses and recoveries;
7. records final target/container result.

## Provisional acceptance thresholds

These thresholds are product-oriented starting values and must be revised from measured evidence, not intuition.

### Initial relocalization

- successful accepted global relocalization in at least 18 of 20 trials (90%);
- median time to accepted localization <= 5 s;
- p95 time to accepted localization <= 10 s for successful trials;
- no accepted pose may confidently place the user in the wrong room.

### Route-level guidance

- the route remains usable after initial localization in at least 90% of successful trials;
- temporary tracking loss must produce a visible degraded state rather than silently presenting a false precise overlay;
- the system can request/recover through a fallback relocalization action when confidence becomes insufficient.

### Final approach

- the system identifies the correct destination furniture in at least 18 of 20 trials;
- after local refinement, the system identifies the correct target compartment/storage instruction in at least 18 of 20 trials;
- the final UI must distinguish spatially measured/inferred guidance from semantic instructions such as "open drawer 2".

### Safety against false confidence

A false high-confidence localization is considered more severe than a declared localization failure.

Any trial in which the system reports a high-confidence pose in the wrong room is a release-blocking defect until its failure mode is understood and bounded.

## Fallback requirement

The MVP may use a deterministic recovery mechanism such as a visual marker, augmented image or user-confirmed landmark when automatic relocalization confidence is insufficient.

The fallback is acceptable only if:

- it is explicitly surfaced to the user;
- after recovery, continuous relative tracking resumes without requiring markers throughout the route;
- the normalized localization contract remains unchanged for navigation consumers.

The existence of a fallback does not excuse poor automatic-localization metrics; automatic and fallback success rates are reported separately.

## Non-goals for the viability gate

The following are explicitly excluded from this experiment:

- automatic recognition of arbitrary household objects;
- automatic generation of the entire storage hierarchy;
- photorealistic 3D reconstruction;
- centimetric accuracy across the full dwelling;
- multi-floor navigation;
- iOS support;
- UWB infrastructure;
- mandatory Wi-Fi RTT infrastructure;
- Home Assistant integration;
- multi-tenant production scaling;
- custom proprietary SLAM/VIO implementation.

## Go / investigate / no-go decision

### GO

Proceed to the end-to-end inventory/navigation MVP when Phase B passes and failure cases are understood well enough to define supported operating conditions.

### INVESTIGATE

Do not expand product scope when metrics are close but below threshold. Create atomic experiments for the dominant failure mode, for example:

- weak visual retrieval;
- incorrect feature correspondences;
- VIO drift after localization;
- repetitive-texture ambiguity;
- poor low-light behavior;
- device thermal throttling;
- target-furniture local refinement.

### NO-GO for the current localization approach

Replace or redesign the localization provider when repeated controlled experiments cannot approach the acceptance thresholds without requirements incompatible with the intended consumer experience.

This is not automatically a no-go for AR Home as a product; it is a no-go for that provider/architecture.

## Evidence required in the PR/experiment report

Every viability run must preserve:

- device capability report;
- app/provider versions;
- environment/map version;
- test-point definitions;
- per-trial results;
- latency distribution;
- success/failure classification;
- tracking-loss counts;
- confidence values;
- representative failure logs;
- privacy-safe screenshots/video where useful;
- explicit conclusion: GO, INVESTIGATE or provider NO-GO.

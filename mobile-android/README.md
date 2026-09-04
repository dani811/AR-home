# Android device capability probe

Diagnostic-only Android application for issue #19. It measures capabilities from the real device instead of inferring them from a commercial model name.

## Passive probe

The current slice reports:

- device and Android build identity;
- rear Camera2 devices, hardware level, focal lengths, exposed intrinsic calibration, AE FPS ranges and YUV output sizes;
- accelerometer, gyroscope, rotation vector and magnetic-field sensor metadata;
- Wi-Fi RTT feature flag;
- ARCore runtime availability;
- battery temperature and basic runtime/memory information.

It deliberately does **not** open the camera or start an ARCore `Session`. Active AR tracking, depth and CPU-frame acquisition are a separate atomic slice.

## Build

Requirements: JDK 17, Android SDK 36 and Gradle 9.5.

```bash
cd mobile-android
gradle :app:assembleDebug :app:lintDebug
```

The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Report

Launching the app runs the passive probe and writes a versioned JSON document to the app-specific external files directory under `capability-reports/`. The absolute path is displayed on screen.

A report is evidence only for the device/build on which it was captured. No capability may be promoted to a product requirement solely from this report.

## Automatic map validation (policy v1)

Importing a ZIP or finishing a capture queues an on-device WorkManager job. A private immutable snapshot is copied before enqueueing; each job has its own directory, report and input fingerprint. The latest job ID survives Activity recreation and process restarts. Android may defer/reschedule execution; this is not an always-running service. No network, server or AI API is used.

The worker checks image dimensions/calibration, records brightness and Laplacian variance, and flags views with fewer than 100 ORB features. It does **not** declare blur from an uncalibrated Laplacian threshold. Every fourth image (indices 1,5,9,...) is reserved. Only the remaining images/poses build the 3D model using the production builder. The production PnP provider then estimates reserved poses. Acceptance requires >=90% of at least three reserved views within 20 cm and 5 degrees of the recorded ARCore poses, with no low-feature images. These are **provisional engineering gates**, not measured physical accuracy or proof of fresh-session recovery. Baseline/reprojection checks are inherited from the production builder; a dense coverage map and live capture coaching are not implemented in this slice.

Statuses: pending/running progress, internal pass, needs more capture or review, unable to validate. Recovery is blocked until internal pass. The report button shows unsuccessful/low-feature views and exports JSON via the system picker. Original map export remains available even if validation fails, and is described as an unvalidated capture. To retry, import the ZIP again.

This initial worker accepts 12–160 images and cooperatively limits computation to four minutes. Oversized, incomplete, invalid or timed-out maps receive an unable-to-validate result, never a pass. Work interrupted by Android can restart from the immutable snapshot. Reports and snapshots are app-private and retained until app data is removed; automatic retention cleanup is not yet implemented.

### Device acceptance checks

1. Import a map: progress appears without a camera session; leave and reopen the app, then open/export the finished report.
2. Import a different map while validation runs: its result must not be overwritten by the older job.
3. Finish a new capture: archive is saved, camera stops, validation starts automatically.
4. Inspect a needs-review result: failed view thumbnails are shown and recovery is blocked.
5. Import <12 images or corrupt calibration: no false pass; show a concrete failure.
6. Restart the device during a queued/running job: WorkManager eventually reschedules, subject to OS restrictions (force-stop requires reopening the app).

Build checks do not replace these device checks. The earlier Python replay of map-1788120232860 produced 5/10 accepted held-out views; Android OpenCV 4.14 may differ from that Python OpenCV 4.12 replay.

### Isolated installation diagnostic

CI currently builds with `-PisolatedInstall=true`. This keeps the validation
implementation but installs debug builds as `io.arhome.capabilities.validation`,
labelled **AR Home Localizer**, using the same package as the previously distributed validation build.
This release replaces that build; it does not introduce another parallel app.
Create a new scan to obtain depth data. Without the property the original
application ID and label are retained. This is a diagnostic for installation
conflicts, not a repair of signing continuity. CI still needs a securely persisted
signing key before these builds can reliably update one another. No private key
is committed to the repository. Device installation remains a manual gate.

### Depth capture and reconstruction (schema 3)

The depth scan build enables autofocus and checks Depth support at runtime. On
supported sessions, a fresh raw depth image (unsigned millimeters, little
endian) and confidence bytes are attached when ARCore provides them. The
manifest records all timestamps and the per-frame affine mapping from CPU image
pixels to depth UVs; it does not assume that RGB and depth have equal aspect
ratios. Reprojected old depth frames are skipped. Once a new capture position is
ready, the app waits at most 750 ms for a fresh depth measurement, then saves the
RGB keyframe even if none arrived. The screen and manifest report the configured
mode, attempt count, outcome counts and timestamps. Unsupported devices retain
RGB capture and declare that source.

The capture keeps only its latest local ARCore anchor. Before accepting the next
view, it composes the relative transform while the previous and new anchors are
both tracking in the same ARCore update, then detaches the previous anchor. This
forms a pairwise pose chain relative to the first view without requiring every
historical anchor to remain tracked for the whole scan. If the only initial
anchor is lost for two seconds, that unusable single view is discarded and the
capture restarts automatically. A longer chain is preserved and reports the
latest reference state instead of silently corrupting it. Capture is bounded at
80 views. These poses remain ARCore estimates, not physical ground truth or
cross-session persistence. No cloud anchors, service calls or QR markers are
involved. Schema-2 anchor-snapshot and schema-1 legacy maps remain readable.

Raw-depth maps build ORB landmarks by backprojecting reliable measurements. A
single lucky depth frame cannot force the complete map onto that path: raw depth
is selected only with at least six depth frames covering at least half of all
views; otherwise the complete RGB sequence uses triangulation.
The initial, provisional policy requires confidence >=192/255, optical depth
0.2–8 m and five consistent samples in a 3x3 neighborhood. Mixed-depth edges
are rejected at max(50 mm, 5% of depth). One depth pixel contributes at most
one feature per frame; a round-robin cap preserves representation of all views.
These are engineering gates to validate on device, not calibrated accuracy
guarantees. PnP uses unique target rows for these maps and the original pose
acceptance thresholds. No RGB triangulation is silently substituted if a depth
map produces insufficient reliable points.

The held-out validation reconstructs from training frames only and reports
landmarkSource, depthFrameCount and poseReferenceIsGroundTruth=false. Legacy
schema-1 ZIPs still load and retain their RGB pipeline; this change cannot add
missing depth measurements to existing captures.

Validation: unit coverage for padded image-plane decoding, unsigned 16-bit
values, crop/rotation alignment, camera-axis convention, missing measurements,
confidence filtering, depth discontinuities, pairwise pose composition and map
schema completeness. Real-device depth availability, performance, precision and
recovery still need validation; a complete overlay and adaptive direction
planner are not part of this build.

CI also generates 20 deterministic but distinct views of known 3D geometry,
with matching unsigned-depth/confidence planes and camera poses. Five views are
held out. A host contract check and an Android-emulator instrumentation test run
the depth-landmark and production PnP path against them. The generated ZIP,
per-view results and Android test report are uploaded as evidence. Passing this
synthetic contract proves the geometry and validation path on controlled input;
it does not prove that a physical phone is producing usable depth observations.

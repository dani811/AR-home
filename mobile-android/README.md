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

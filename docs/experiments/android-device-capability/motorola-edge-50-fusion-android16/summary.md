# Motorola Edge 50 Fusion — Android capability gate

Date: 2026-08-30
Issue: #19
Device: Motorola Edge 50 Fusion (`cuscoi`)
OS: Android 16 / API 36

## Decision

**PASS — select ARCore as the relative tracking and synchronized capture provider for the persistent relocalization PoC.**

The hardware test satisfies the Phase A acceptance criterion: at least one viable path provides synchronized camera observations and relative device motion suitable for the persistent-localization experiment.

## Evidence

- ARCore runtime availability: `SUPPORTED_INSTALLED`.
- A real ARCore `Session` was created and updated successfully.
- `TrackingState.TRACKING` was observed for 33 frames.
- `trackingFailureReason = NONE`.
- ARCore produced a 6DoF camera pose.
- Runtime image intrinsics were available for the 640x480 CPU camera stream.
- A CPU camera frame was acquired while the session was active.
- `DepthMode.AUTOMATIC` is supported.
- `RAW_DEPTH_ONLY` is supported.
- Raw depth and confidence images were acquired.
- CPU camera, raw depth and raw depth confidence shared the same recorded timestamp (`692915631467104 ns`) in the sample, giving a directly usable synchronized observation set.
- No active-probe error was recorded (`lastError = null`).

## Camera and sensor observations

The rear logical Camera2 device reports `LEVEL_3`, YUV sizes up to 4096x3072 and AE ranges including 30 and 60 fps. Camera2 calibration data is exposed.

The IMU path is also viable if needed for diagnostics or future lower-level work:

- accelerometer minimum delay: 5000 us;
- gyroscope minimum delay: 5000 us;
- rotation vector minimum delay: 5000 us;
- magnetometer minimum delay: 10000 us.

For the PoC, ARCore remains the source of relative pose. We should not independently fuse the Android IMU into a competing pose estimate unless a later experiment requires it.

## Intrinsics rule

Do not derive ARCore stream intrinsics by naively scaling the Camera2 static calibration. The Camera2 calibration (`fx≈2780`, `fy≈2780`, `cx=2056`, `cy=1544`) and the live ARCore 640x480 stream (`fx≈435.57`, `fy≈434.72`, `cx≈317.87`, `cy≈240.70`) reflect the actual stream/crop pipeline.

The localization pipeline must persist the intrinsics associated with each captured image and use the ARCore runtime intrinsics for ARCore CPU frames.

## Depth rule

Raw depth is available and useful as an optional geometry cue, but it is not a dependency for global relocalization. The sample raw depth/confidence images are 160x90 while the CPU image is 640x480, so consumers must use the proper camera/depth transforms rather than assuming pixel-for-pixel alignment.

## Wi-Fi RTT

`wifiRtt = false` on this device. Wi-Fi RTT must therefore remain an optional coarse prior and cannot be a requirement for the MVP.

## Performance caveat

The captured active sample lasted only 2.58 seconds:

- 69 total frames;
- 33 tracking frames;
- 36 paused frames;
- average update rate: 26.34 Hz;
- maximum observed frame gap: 83.29 ms;
- battery temperature: 31 °C.

This is sufficient for the Phase A capability gate, but **not** sufficient to characterize sustained thermals, long-walk tracking stability or production frame pacing. Those are not blockers for starting #20 and should be measured during the relocalization experiments.

The high paused-frame proportion is interpreted only as a startup/very-short-sample observation because the session subsequently reached `TRACKING`, reported `NONE` as the failure reason, and delivered all required synchronized data. It must not be treated as a long-run tracking success rate.

## Next experiment

Proceed to #20: persistent indoor relocalization after a fresh session.

ARCore responsibilities in #20:

1. provide relative 6DoF VIO pose;
2. provide synchronized CPU camera keyframes and per-frame intrinsics;
3. optionally provide raw depth/confidence for mapping and geometric filtering;
4. maintain local pose between global visual relocalization updates.

The persistent/global pose must be solved independently by the replaceable `LocalizationProvider`; ARCore local coordinates alone are not considered persistent localization.

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

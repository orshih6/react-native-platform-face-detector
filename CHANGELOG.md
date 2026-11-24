# Changelog

## 0.1.1 — 2026-11-24

First version on npm.

- iOS simulators: detection failed with "Could not create inference context". Vision now runs on the CPU on a simulator; real devices are unchanged.
- The README explains how to use it in a plain React Native app without Expo.

Also runs in a plain React Native 0.88.1 app set up with `install-expo-modules` (release builds, iOS simulator and Android emulator). The README lists the two manual fixes that setup needs.

## 0.1.0 — 2026-11-23

First version.

- `detectFacesInImage`: faces in a picture on disk.
- `FaceCameraView`: a live camera view that reports faces, with `takeSnapshot`.
- `getCameraPermission` / `requestCameraPermission`.
- iOS uses Apple Vision; Android uses ML Kit with CameraX.

Verified: the Apple Vision detection logic against a portrait on macOS; the example app builds for an arm64 iOS simulator (Xcode 26.6, Expo SDK 58).

The Android side compiles (Kotlin, in the example app, Android SDK 37).

The iOS side runs in the example app on a real iPhone (release build, iPhone 16 Pro Max, iOS 27.2): the live camera opens and reports faces.

The Android side runs in the example app on an Android 14 emulator (release build, R8 on): a portrait gives one face with its landmarks, the live view shows frames on the back lens, and `takeSnapshot` writes a picture. Running it found and fixed three things:

- Release builds shrink code with R8, which broke ML Kit so that reading any picture failed. The library now ships keep rules (`android/consumer-rules.pro`) that apps pick up automatically.
- The live view stayed black and produced no frames, because the preview's drawing surface was never laid out. `FaceCameraView` now runs that layout pass itself.
- `detectFacesInImage` now says why a picture could not be read, and decodes it by hand if ML Kit cannot open it.

The example app has a button to switch between the front and back lens.

Not yet verified: a live face in front of an Android camera (the emulator's scene has no person), the Android front lens, and a real Android phone. On iOS, the angle signs, `takeSnapshot` and `detectFacesInImage` have not been checked one by one on the phone.

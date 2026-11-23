# Changelog

## 0.1.0 — unreleased

First version.

- `detectFacesInImage`: faces in a picture on disk.
- `FaceCameraView`: a live camera view that reports faces, with `takeSnapshot`.
- `getCameraPermission` / `requestCameraPermission`.
- iOS uses Apple Vision; Android uses ML Kit with CameraX.

Verified: the Apple Vision detection logic against a portrait on macOS; the example app builds for an arm64 iOS simulator (Xcode 26.6, Expo SDK 58).

The Android side compiles (Kotlin, in the example app, Android SDK 37).

The iOS side runs in the example app on a real iPhone (release build, iPhone 16 Pro Max, iOS 27.2): the live camera opens and reports faces.

Not yet verified: the Android module has not been run in an app, on a device or an emulator. On iOS, the angle signs, `takeSnapshot` and `detectFacesInImage` have not been checked one by one on the phone.

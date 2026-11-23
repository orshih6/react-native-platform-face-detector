# Changelog

## 0.1.0 — unreleased

First version.

- `detectFacesInImage`: faces in a picture on disk.
- `FaceCameraView`: a live camera view that reports faces, with `takeSnapshot`.
- `getCameraPermission` / `requestCameraPermission`.
- iOS uses Apple Vision; Android uses ML Kit with CameraX.

Verified: the Apple Vision detection logic against a portrait on macOS; the example app builds for an arm64 iOS simulator (Xcode 26.6, Expo SDK 58).

The Android side compiles (Kotlin, in the example app, Android SDK 37).

Not yet verified: neither module has been called from JavaScript at runtime, and the live camera has not been run on a device or an emulator.

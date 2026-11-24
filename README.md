# react-native-platform-face-detector

Face detection for React Native and Expo that uses **each platform's own detector**: Apple Vision on iOS, Google ML Kit on Android. One API over both, for still images and for a live camera view.

> **Status: 0.1.1, early.** The iOS side has run on a real iPhone. The Android side has run on an emulator: faces in a photo are found, and the live view shows frames and takes snapshots. It also runs in a plain React Native app on both platforms. **A live face in front of an Android camera has not been tested yet** — see [What is verified](#what-is-verified).

## Why this exists

Every React Native face-detection package wraps ML Kit on both platforms. On iOS, ML Kit ships no arm64 simulator slice, so an app that links it **cannot be built for iOS 26 simulators on Apple Silicon** — the whole app, not just the screen that uses it.

Native teams don't have this problem, because they use Apple Vision on iOS and ML Kit on Android. This package does the same from React Native:

| | iOS | Android |
|---|---|---|
| Detector | Apple Vision (part of the OS) | ML Kit face detection (bundled model) |
| Third-party SDK added | none | ML Kit + CameraX |
| Simulator / emulator build | works on Apple Silicon | works |

Detection runs on the device. Nothing is uploaded, and this package stores nothing.

## What it does, and does not

- **Detects**: where each face is, the head's yaw / pitch / roll, and optionally five landmarks (eyes, nose, mouth corners).
- **Does not recognise**: it cannot tell you whose face it is. There is no embedding, no matching, no identity.
- **Is not a liveness product**: head angles let you ask for a turn and see that it happened, which stops a still photo. A replayed video of a person turning their head will pass.

## Install

It is an Expo module, so it needs a development build (it is not in Expo Go).

```sh
npm install github:orshih6/react-native-platform-face-detector
npx expo prebuild   # or: npx pod-install
```

Requirements: Expo SDK 58 / React Native 0.88 (what it is built against), iOS 16.4+, Android as your Expo SDK requires.

### In a React Native app without Expo

It is written as an Expo module, so the app needs Expo's native core. A plain React Native app gets that once with:

```sh
npx install-expo-modules@latest
npm install github:orshih6/react-native-platform-face-detector
npx pod-install
```

This adds the `expo` package and a few lines to the native projects. It does not move the app to Expo's workflow. An app that cannot take any Expo dependency cannot use this library.

Tested with a fresh React Native 0.88.1 app (see [What is verified](#what-is-verified)). On that version `install-expo-modules` leaves two things unfinished, and the app does not build until they are fixed by hand:

**iOS** — the new scene-based template is only half converted. Replace the two files with Expo's own shape (use your app's name where it says `YourApp`):

```swift
// ios/YourApp/AppDelegate.swift
internal import Expo
import React
import ReactAppDependencyProvider
import UIKit

@main
class AppDelegate: ExpoAppDelegate, ExpoReactNativeFactoryProvider {
  var window: UIWindow?
  var reactNativeDelegate: ExpoReactNativeFactoryDelegate?
  var reactNativeFactory: RCTReactNativeFactory?
  var reactNativeFactoryModuleName: String { "YourApp" }

  public override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    let delegate = ReactNativeDelegate()
    let factory = ExpoReactNativeFactory(delegate: delegate)
    delegate.dependencyProvider = RCTAppDependencyProvider()
    reactNativeDelegate = delegate
    reactNativeFactory = factory
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
}

class ReactNativeDelegate: ExpoReactNativeFactoryDelegate {
  override func sourceURL(for bridge: RCTBridge) -> URL? {
    bridge.bundleURL ?? bundleURL()
  }

  override func bundleURL() -> URL? {
#if DEBUG
    return RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
#else
    return Bundle.main.url(forResource: "main", withExtension: "jsbundle")
#endif
  }
}
```

```swift
// ios/YourApp/SceneDelegate.swift
internal import Expo

class SceneDelegate: ExpoAppSceneDelegate {
}
```

**Android** — in `android/app/build.gradle`, the `cliFile` line cannot find Expo's CLI. Change it to:

```gradle
cliFile = new File(["node", "--print", "require.resolve('@expo/cli', { paths: [require.resolve('expo/package.json')] })"].execute(null, rootDir).text.trim())
```

Both are gaps in Expo's setup tool, not in this library, and may be fixed in a later `install-expo-modules`.

Add the camera usage text to your app (`app.json`):

```json
{
  "expo": {
    "ios": { "infoPlist": { "NSCameraUsageDescription": "Used to …" } },
    "android": { "permissions": ["android.permission.CAMERA"] }
  }
}
```

## Use

### Live camera

```tsx
import { useRef, useState } from 'react';
import { FaceCameraView, requestCameraPermission, type FaceCameraViewRef } from 'react-native-platform-face-detector';

function Look() {
  const camera = useRef<FaceCameraViewRef>(null);
  const [seen, setSeen] = useState(false);

  // Ask before mounting the view: await requestCameraPermission() === 'granted'

  return (
    <FaceCameraView
      ref={camera}
      style={{ width: 240, height: 320 }}
      facing="front"
      onFacesDetected={async ({ nativeEvent }) => {
        const face = nativeEvent.faces[0];
        setSeen(!!face);
        if (face && Math.abs(face.yaw ?? 0) < 10) {
          // A silent frame of what the camera sees right now.
          const shot = await camera.current?.takeSnapshot({ maxWidth: 640, base64: true });
        }
      }}
      onError={({ nativeEvent }) => console.warn(nativeEvent.code, nativeEvent.message)}
    />
  );
}
```

### A picture on disk

```ts
import { detectFacesInImage } from 'react-native-platform-face-detector';

const { faces, width, height } = await detectFacesInImage(fileUri, { landmarks: true });
```

## API

### `detectFacesInImage(uri, options?) → Promise<{ faces, width, height }>`

`uri` is a `file://` URI or an absolute path (Android also takes `content://`). `options.landmarks` adds the five points to each face.

### `<FaceCameraView />`

| Prop | Default | |
|---|---|---|
| `facing` | `'front'` | `'front'` or `'back'`. The front picture is mirrored, as a selfie preview is. |
| `active` | `true` | `false` releases the camera without unmounting the view. |
| `landmarks` | `false` | Report the five points on each face. Slower. |
| `minIntervalMs` | `100` | Analyse a frame at most this often. |
| `onFacesDetected` | | `{ faces, frameWidth, frameHeight, timestamp }` for each analysed frame; `faces` is empty when nobody is there. |
| `onCameraReady` | | The first frame has arrived. |
| `onError` | | `{ code, message }` — `NO_CAMERA` (a simulator), `NO_PERMISSION`, `SESSION_FAILED`. |

Ref: `takeSnapshot({ maxWidth?, quality?, base64? }) → Promise<{ uri? , base64?, width, height }>` — the newest frame as a JPEG, with no shutter and no sound.

### `getCameraPermission()` / `requestCameraPermission()`

Answer `'granted' | 'denied' | 'undetermined'`. Use these or any other permission library; the view only needs the permission to be held.

### A `Face`

```ts
type Face = {
  bounds: { x: number; y: number; width: number; height: number };
  yaw: number | null;    // degrees: turn left / right
  pitch: number | null;  // degrees: nod up / down
  roll: number | null;   // degrees: tilt toward a shoulder
  landmarks?: { leftEye?, rightEye?, nose?, mouthLeft?, mouthRight? };
};
```

- **Coordinates** are a share of the picture, 0..1, with the origin at the **top left**, on both platforms.
- **Left and right** in `landmarks` are as they appear in the picture: `leftEye` is the eye on the left side of the image.
- In the live view, coordinates describe the **camera frame**, which the view crops to fill itself. Map them through `frameWidth` / `frameHeight` before drawing over the view.

## Known differences and limits

- ⚠️ **Angle signs are not reconciled between platforms.** Each angle carries its detector's own sign. Compare an angle with an earlier reading of itself, or use its size; do not rely on "positive yaw means left".
- **`takeSnapshot` returns different crops**: the whole camera frame on iOS, the visible (cropped) picture on Android.
- **iOS simulators have no camera**: the view stays black and reports `NO_CAMERA`. `detectFacesInImage` works there.
- Apple Vision reports `pitch` only from iOS 15; this package requires 16.4 anyway.
- Web is not supported.

## What is verified

| | State |
|---|---|
| iOS detection logic (Apple Vision → faces, coordinates, landmarks, angles) | Run on macOS against a portrait: one face, landmarks in the right order, top-left origin |
| iOS module + view compile for an Apple Silicon simulator | Built: the example app, arm64 `iphonesimulator`, Xcode 26.6, Expo SDK 58 (0.1.0) |
| iOS module loaded and called from JavaScript at runtime | Run: the example app, release build, on an iPhone 16 Pro Max (iOS 27.2) |
| iOS live camera on a real iPhone | Seen working on the same phone: the camera opens and faces are reported. Angle signs, `takeSnapshot` and `detectFacesInImage` were **not checked one by one** |
| Android module + view compile (Kotlin, ML Kit 16.1.7, CameraX 1.4.2) | Built: `:react-native-platform-face-detector:compileDebugKotlin` in the example app, Android SDK 37, JDK 17, Expo SDK 58 (0.1.0) |
| Android `detectFacesInImage` in a running app | Run: the example app, release build with R8 shrinking on, Android 14 emulator (x86_64). A 960×1200 portrait gave one face with eyes, nose and mouth in the right places |
| Android live view and `takeSnapshot` | Run on the same emulator, back lens: the preview shows, frames are analysed, a snapshot is written. The emulator's scene has no person in it, so **finding a live face on Android is not tested yet**, and neither is the front lens or a real phone |
| Plain React Native app (no Expo workflow), React Native 0.88.1 + `install-expo-modules` | Run, release builds: on an iOS 26.5 simulator a bundled portrait gave one face; on the Android 14 emulator a portrait gave one face, the live view sent events and a snapshot was taken and scanned. Needed the two manual fixes described under Install |
| iOS simulator | `detectFacesInImage` works (Vision is run on the CPU there). A simulator has no camera, so the live view does not |

## Example

`example/` is a small app: open the camera and watch the readout, take a snapshot, or pick a photo and see the faces found in it.

```sh
cd example && npm install && npx expo run:ios
```

## License

MIT

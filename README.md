# react-native-platform-face-detector

Face detection for React Native and Expo that uses **each platform's own detector**: Apple Vision on iOS, Google ML Kit on Android. One API over both, for still images and for a live camera view.

> **Status: 0.1.0, early.** Both sides compile, and the iOS detection is checked against a real picture. **Neither side has been run in an app on a device yet** — see [What is verified](#what-is-verified).

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
| iOS module loaded and called from JavaScript at runtime | **Not run yet** — compiled only |
| iOS live camera on a real iPhone | **Not tested yet** |
| Android module + view compile (Kotlin, ML Kit 16.1.7, CameraX 1.4.2) | Built: `:react-native-platform-face-detector:compileDebugKotlin` in the example app, Android SDK 37, JDK 17, Expo SDK 58 (0.1.0) |
| Android run in an app, on an emulator or a device | **Not run yet** — compiled only |

## Example

`example/` is a small app: open the camera and watch the readout, take a snapshot, or pick a photo and see the faces found in it.

```sh
cd example && npm install && npx expo run:ios
```

## License

MIT

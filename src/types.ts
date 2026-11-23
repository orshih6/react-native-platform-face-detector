import type { StyleProp, ViewStyle } from 'react-native';

/** A point as a share of the image or camera frame: 0..1, origin at the top left. */
export type Point = { x: number; y: number };

/** A box as a share of the image or camera frame: 0..1, origin at the top left. */
export type FaceBounds = { x: number; y: number; width: number; height: number };

/**
 * Five points of a face. "Left" and "right" are as they APPEAR in the image —
 * the eye on the left side of the picture is `leftEye` — on both platforms.
 * Any of them can be missing when the detector could not place it.
 */
export type FaceLandmarks = {
  leftEye?: Point;
  rightEye?: Point;
  nose?: Point;
  mouthLeft?: Point;
  mouthRight?: Point;
};

export type Face = {
  bounds: FaceBounds;
  /**
   * Head angles in degrees; null when the platform's detector did not report
   * one. `yaw` is the turn left/right, `pitch` the nod up/down, `roll` the
   * tilt toward a shoulder.
   *
   * ⚠️ The SIGN of each angle is the platform detector's own and has not been
   * reconciled between Apple Vision and ML Kit. Compare an angle with an
   * earlier reading of itself (how far the head has turned), or use its size;
   * do not rely on "positive means left".
   */
  yaw: number | null;
  pitch: number | null;
  roll: number | null;
  /** Present when `landmarks` was asked for. */
  landmarks?: FaceLandmarks;
};

export type DetectOptions = {
  /** Also place the eyes, nose and mouth corners. Slower. Default false. */
  landmarks?: boolean;
};

export type ImageFaces = {
  faces: Face[];
  /** The image's size in pixels, upright. */
  width: number;
  height: number;
};

export type CameraPermission = 'granted' | 'denied' | 'undetermined';

export type FacesDetectedEvent = {
  faces: Face[];
  /** The camera frame's size in pixels, upright. */
  frameWidth: number;
  frameHeight: number;
  /** Milliseconds since the Unix epoch, when the frame was analysed. */
  timestamp: number;
};

export type FaceCameraErrorEvent = {
  /** `NO_CAMERA` (a simulator, or no such lens), `NO_PERMISSION`, `SESSION_FAILED`. */
  code: string;
  message: string;
};

export type SnapshotOptions = {
  /** Scale the frame down to at most this many pixels wide. Default: as captured. */
  maxWidth?: number;
  /** JPEG quality 0..1. Default 0.8. */
  quality?: number;
  /** Return the JPEG as base64 instead of writing a file. Default false. */
  base64?: boolean;
};

export type Snapshot = {
  /** A `file://` URI in the cache directory; absent when `base64` was asked for. */
  uri?: string;
  base64?: string;
  width: number;
  height: number;
};

export type FaceCameraViewProps = {
  /** Which lens. Default `front`. The front picture is mirrored, as a selfie preview is. */
  facing?: 'front' | 'back';
  /** Run the camera. Default true. False releases the camera without unmounting. */
  active?: boolean;
  /** Also report landmarks on each face. Default false. */
  landmarks?: boolean;
  /** Analyse a frame at most this often, in milliseconds. Default 100. */
  minIntervalMs?: number;
  /**
   * Faces in the newest analysed frame — an empty list when nobody is there.
   * Coordinates are shares of the CAMERA FRAME, which the view crops to fill
   * itself: map them through `frameWidth`/`frameHeight` before drawing over
   * the view.
   */
  onFacesDetected?: (event: { nativeEvent: FacesDetectedEvent }) => void;
  onCameraReady?: (event: { nativeEvent: Record<string, never> }) => void;
  onError?: (event: { nativeEvent: FaceCameraErrorEvent }) => void;
  style?: StyleProp<ViewStyle>;
};

export type FaceCameraViewRef = {
  /** The newest camera frame as a JPEG. Silent: no shutter, no sound. Rejects before the first frame. */
  takeSnapshot(options?: SnapshotOptions): Promise<Snapshot>;
};

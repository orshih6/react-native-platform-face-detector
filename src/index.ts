import Native from './PlatformFaceDetectorModule';
import type { CameraPermission, DetectOptions, ImageFaces } from './types';

export { FaceCameraView } from './FaceCameraView';
export * from './types';

/**
 * Find the faces in a picture on disk. `uri` is a `file://` URI or an absolute
 * path (on Android a `content://` URI works too).
 */
export function detectFacesInImage(uri: string, options: DetectOptions = {}): Promise<ImageFaces> {
  return Native.detectFacesInImage(uri, options);
}

export function getCameraPermission(): Promise<CameraPermission> {
  return Native.getCameraPermission();
}

/** Shows the system prompt the first time; afterwards answers what was decided. */
export function requestCameraPermission(): Promise<CameraPermission> {
  return Native.requestCameraPermission();
}

import { NativeModule, requireNativeModule } from 'expo';

import type { CameraPermission, DetectOptions, ImageFaces } from './types';

declare class PlatformFaceDetectorModule extends NativeModule<Record<string, never>> {
  detectFacesInImage(uri: string, options: DetectOptions): Promise<ImageFaces>;
  getCameraPermission(): Promise<CameraPermission>;
  requestCameraPermission(): Promise<CameraPermission>;
}

export default requireNativeModule<PlatformFaceDetectorModule>('PlatformFaceDetector');

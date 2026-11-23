import { requireNativeView } from 'expo';
import * as React from 'react';

import type { FaceCameraViewProps, FaceCameraViewRef } from './types';

const NativeView: React.ComponentType<FaceCameraViewProps & { ref?: React.Ref<FaceCameraViewRef> }> =
  requireNativeView('PlatformFaceDetector');

/**
 * A live camera picture that reports the faces in it, found on the phone by
 * the platform's own detector (Apple Vision on iOS, ML Kit on Android).
 *
 * The app must hold camera permission before this is mounted — see
 * `requestCameraPermission`. On an iOS simulator there is no camera: the view
 * stays black and `onError` fires with `NO_CAMERA`.
 */
export function FaceCameraView({ ref, ...props }: FaceCameraViewProps & { ref?: React.Ref<FaceCameraViewRef> }) {
  return <NativeView ref={ref} {...props} />;
}

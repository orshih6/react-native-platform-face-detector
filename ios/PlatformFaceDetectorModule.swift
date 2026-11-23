import AVFoundation
import ExpoModulesCore
import ImageIO
import UIKit
import Vision

struct DetectOptions: Record {
  @Field var landmarks: Bool = false
}

struct SnapshotOptions: Record {
  @Field var maxWidth: Double?
  @Field var quality: Double = 0.8
  @Field var base64: Bool = false
}

final class FaceDetectorException: GenericException<String> {
  override var reason: String { param }
}

public class PlatformFaceDetectorModule: Module {
  public func definition() -> ModuleDefinition {
    Name("PlatformFaceDetector")

    AsyncFunction("detectFacesInImage") { (uri: String, options: DetectOptions) -> [String: Any] in
      let path = URL(string: uri).flatMap { $0.isFileURL ? $0.path : nil } ?? uri
      guard let image = UIImage(contentsOfFile: path), let cg = image.cgImage else {
        throw FaceDetectorException("Could not read an image at \(uri)")
      }
      let handler = VNImageRequestHandler(cgImage: cg, orientation: Self.orientation(of: image), options: [:])
      let faces = try FaceVision.detect(handler: handler, landmarks: options.landmarks)
      // `size` is the upright size, which is what the coordinates describe.
      return [
        "faces": faces,
        "width": Double(image.size.width * image.scale),
        "height": Double(image.size.height * image.scale),
      ]
    }

    AsyncFunction("getCameraPermission") { () -> String in
      Self.permission(AVCaptureDevice.authorizationStatus(for: .video))
    }

    AsyncFunction("requestCameraPermission") { (promise: Promise) in
      let now = AVCaptureDevice.authorizationStatus(for: .video)
      guard now == .notDetermined else {
        promise.resolve(Self.permission(now))
        return
      }
      AVCaptureDevice.requestAccess(for: .video) { granted in
        promise.resolve(granted ? "granted" : "denied")
      }
    }

    View(FaceCameraView.self) {
      Events("onFacesDetected", "onCameraReady", "onError")

      Prop("facing") { (view: FaceCameraView, facing: String?) in
        view.facing = facing == "back" ? .back : .front
      }
      Prop("active") { (view: FaceCameraView, active: Bool?) in
        view.active = active ?? true
      }
      Prop("landmarks") { (view: FaceCameraView, landmarks: Bool?) in
        view.landmarks = landmarks ?? false
      }
      Prop("minIntervalMs") { (view: FaceCameraView, interval: Double?) in
        view.minInterval = max(0, (interval ?? 100) / 1000)
      }

      AsyncFunction("takeSnapshot") { (view: FaceCameraView, options: SnapshotOptions?) -> [String: Any] in
        try view.snapshot(options ?? SnapshotOptions())
      }
    }
  }

  private static func permission(_ status: AVAuthorizationStatus) -> String {
    switch status {
    case .authorized: return "granted"
    case .notDetermined: return "undetermined"
    default: return "denied"
    }
  }

  private static func orientation(of image: UIImage) -> CGImagePropertyOrientation {
    switch image.imageOrientation {
    case .up: return .up
    case .down: return .down
    case .left: return .left
    case .right: return .right
    case .upMirrored: return .upMirrored
    case .downMirrored: return .downMirrored
    case .leftMirrored: return .leftMirrored
    case .rightMirrored: return .rightMirrored
    @unknown default: return .up
    }
  }
}

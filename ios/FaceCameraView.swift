import AVFoundation
import CoreImage
import ExpoModulesCore
import UIKit
import Vision

/// A live camera picture that reports the faces in it.
///
/// The frames are delivered UPRIGHT and, for the front lens, MIRRORED — the
/// connection is told to do both — so what Vision measures, what the preview
/// shows and what a snapshot holds are one and the same picture.
final class FaceCameraView: ExpoView, AVCaptureVideoDataOutputSampleBufferDelegate {
  let onFacesDetected = EventDispatcher()
  let onCameraReady = EventDispatcher()
  let onError = EventDispatcher()

  var facing: AVCaptureDevice.Position = .front {
    didSet { if facing != oldValue { reconfigure() } }
  }
  var active = true {
    didSet { if active != oldValue { reconfigure() } }
  }
  var landmarks = false
  var minInterval: TimeInterval = 0.1

  private let session = AVCaptureSession()
  private let preview = AVCaptureVideoPreviewLayer()
  /// Everything that touches the session runs here, never on the main queue.
  private let sessionQueue = DispatchQueue(label: "platformfacedetector.session")
  private let frameQueue = DispatchQueue(label: "platformfacedetector.frames")
  private let imageContext = CIContext()

  // Read and written on `frameQueue` only, except `latest`, which is guarded.
  private var lastAnalysis: CFTimeInterval = 0
  private let latestLock = NSLock()
  private var latest: CVPixelBuffer?
  private var announcedReady = false

  required init(appContext: AppContext? = nil) {
    super.init(appContext: appContext)
    clipsToBounds = true
    backgroundColor = .black
    preview.session = session
    preview.videoGravity = .resizeAspectFill
    layer.addSublayer(preview)
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    preview.frame = bounds
  }

  override func didMoveToWindow() {
    super.didMoveToWindow()
    reconfigure()
  }

  deinit {
    let session = self.session
    sessionQueue.async { if session.isRunning { session.stopRunning() } }
  }

  // MARK: session

  private func reconfigure() {
    let wanted = active && window != nil
    let position = facing
    sessionQueue.async { [weak self] in
      guard let self = self else { return }
      if !wanted {
        if self.session.isRunning { self.session.stopRunning() }
        return
      }
      guard AVCaptureDevice.authorizationStatus(for: .video) == .authorized else {
        self.fail("NO_PERMISSION", "Camera permission has not been granted")
        return
      }
      guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position) else {
        // A simulator, or a device without that lens.
        self.fail("NO_CAMERA", "No camera is available")
        return
      }
      do {
        let input = try AVCaptureDeviceInput(device: device)
        self.session.beginConfiguration()
        self.session.inputs.forEach { self.session.removeInput($0) }
        self.session.outputs.forEach { self.session.removeOutput($0) }
        self.session.sessionPreset = .vga640x480
        guard self.session.canAddInput(input) else {
          self.session.commitConfiguration()
          self.fail("SESSION_FAILED", "The camera could not be attached")
          return
        }
        self.session.addInput(input)

        let output = AVCaptureVideoDataOutput()
        output.alwaysDiscardsLateVideoFrames = true
        output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
        output.setSampleBufferDelegate(self, queue: self.frameQueue)
        guard self.session.canAddOutput(output) else {
          self.session.commitConfiguration()
          self.fail("SESSION_FAILED", "The camera's frames could not be read")
          return
        }
        self.session.addOutput(output)
        if let connection = output.connection(with: .video) {
          if #available(iOS 17.0, *) {
            if connection.isVideoRotationAngleSupported(90) { connection.videoRotationAngle = 90 }
          } else if connection.isVideoOrientationSupported {
            connection.videoOrientation = .portrait
          }
          if connection.isVideoMirroringSupported {
            connection.automaticallyAdjustsVideoMirroring = false
            connection.isVideoMirrored = position == .front
          }
        }
        self.session.commitConfiguration()
        self.announcedReady = false
        if !self.session.isRunning { self.session.startRunning() }
      } catch {
        self.fail("SESSION_FAILED", error.localizedDescription)
      }
    }
  }

  private func fail(_ code: String, _ message: String) {
    DispatchQueue.main.async { [weak self] in
      self?.onError(["code": code, "message": message])
    }
  }

  // MARK: frames

  func captureOutput(
    _ output: AVCaptureOutput,
    didOutput sampleBuffer: CMSampleBuffer,
    from connection: AVCaptureConnection
  ) {
    guard let pixels = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
    latestLock.lock()
    latest = pixels
    latestLock.unlock()

    if !announcedReady {
      announcedReady = true
      DispatchQueue.main.async { [weak self] in self?.onCameraReady([:]) }
    }

    let now = CACurrentMediaTime()
    guard now - lastAnalysis >= minInterval else { return }
    lastAnalysis = now

    let handler = VNImageRequestHandler(cvPixelBuffer: pixels, orientation: .up, options: [:])
    guard let faces = try? FaceVision.detect(handler: handler, landmarks: landmarks) else { return }
    let event: [String: Any] = [
      "faces": faces,
      "frameWidth": CVPixelBufferGetWidth(pixels),
      "frameHeight": CVPixelBufferGetHeight(pixels),
      "timestamp": Date().timeIntervalSince1970 * 1000,
    ]
    DispatchQueue.main.async { [weak self] in self?.onFacesDetected(event) }
  }

  // MARK: snapshot

  func snapshot(_ options: SnapshotOptions) throws -> [String: Any] {
    latestLock.lock()
    let pixels = latest
    latestLock.unlock()
    guard let pixels = pixels else {
      throw FaceDetectorException("The camera has not produced a frame yet")
    }
    var image = CIImage(cvPixelBuffer: pixels)
    if let maxWidth = options.maxWidth, maxWidth > 0, image.extent.width > CGFloat(maxWidth) {
      let scale = CGFloat(maxWidth) / image.extent.width
      image = image.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
    }
    let quality = min(max(options.quality, 0), 1)
    guard
      let space = CGColorSpace(name: CGColorSpace.sRGB),
      let jpeg = imageContext.jpegRepresentation(
        of: image,
        colorSpace: space,
        options: [CIImageRepresentationOption(rawValue: kCGImageDestinationLossyCompressionQuality as String): quality]
      )
    else {
      throw FaceDetectorException("The frame could not be written as a JPEG")
    }
    var out: [String: Any] = [
      "width": Double(image.extent.width.rounded()),
      "height": Double(image.extent.height.rounded()),
    ]
    if options.base64 {
      out["base64"] = jpeg.base64EncodedString()
    } else {
      let url = FileManager.default.temporaryDirectory
        .appendingPathComponent("face-\(UUID().uuidString).jpg")
      try jpeg.write(to: url, options: .atomic)
      out["uri"] = url.absoluteString
    }
    return out
  }
}

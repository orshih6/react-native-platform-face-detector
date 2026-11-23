import CoreGraphics
import CoreVideo
import Foundation
import Vision

/// Apple Vision, asked one question: what faces are in this picture.
///
/// Everything is answered as a share of the picture (0...1) with the origin at
/// the TOP left, which is not Vision's own convention (bottom left) — the
/// flip happens here so no caller has to remember it.
enum FaceVision {
  static func detect(
    handler: VNImageRequestHandler,
    landmarks: Bool
  ) throws -> [[String: Any]] {
    // Revision 3 of the rectangles request is the one that reports yaw as a
    // continuous angle and adds pitch; the landmarks request's own yaw comes
    // in coarse steps, so angles are always read from this one.
    let rectangles = VNDetectFaceRectanglesRequest()
    try handler.perform([rectangles])
    let found = rectangles.results ?? []
    if found.isEmpty { return [] }

    var marks: [UUID: VNFaceLandmarks2D] = [:]
    if landmarks {
      let request = VNDetectFaceLandmarksRequest()
      request.inputFaceObservations = found
      try handler.perform([request])
      for face in request.results ?? [] {
        if let l = face.landmarks { marks[face.uuid] = l }
      }
    }

    return found.map { face in
      let box = face.boundingBox
      var out: [String: Any] = [
        "bounds": [
          "x": Double(box.minX),
          "y": Double(1 - box.maxY),
          "width": Double(box.width),
          "height": Double(box.height),
        ],
        "yaw": degrees(face.yaw),
        "pitch": degrees(face.pitch),
        "roll": degrees(face.roll),
      ]
      if landmarks {
        out["landmarks"] = marks[face.uuid].map { points(of: $0, in: box) } ?? [:]
      }
      return out
    }
  }

  private static func degrees(_ radians: NSNumber?) -> Any {
    guard let r = radians?.doubleValue else { return NSNull() }
    return r * 180 / .pi
  }

  /// The middle of a landmark region, as a share of the whole picture.
  private static func centre(_ region: VNFaceLandmarkRegion2D?, in box: CGRect) -> CGPoint? {
    guard let region = region, region.pointCount > 0 else { return nil }
    let pts = region.normalizedPoints
    let sx = pts.reduce(CGFloat(0)) { $0 + $1.x } / CGFloat(pts.count)
    let sy = pts.reduce(CGFloat(0)) { $0 + $1.y } / CGFloat(pts.count)
    return CGPoint(x: box.minX + sx * box.width, y: 1 - (box.minY + sy * box.height))
  }

  private static func dict(_ p: CGPoint) -> [String: Double] {
    ["x": Double(p.x), "y": Double(p.y)]
  }

  private static func points(of l: VNFaceLandmarks2D, in box: CGRect) -> [String: Any] {
    var out: [String: Any] = [:]
    // "Left" and "right" are as they APPEAR in the picture, so they are
    // assigned by position rather than trusted from the region's name.
    let eyes = [centre(l.leftEye, in: box), centre(l.rightEye, in: box)].compactMap { $0 }.sorted { $0.x < $1.x }
    if eyes.count == 2 {
      out["leftEye"] = dict(eyes[0])
      out["rightEye"] = dict(eyes[1])
    }
    if let nose = centre(l.nose, in: box) { out["nose"] = dict(nose) }
    if let lips = l.outerLips, lips.pointCount > 1 {
      let pts = lips.normalizedPoints.map {
        CGPoint(x: box.minX + $0.x * box.width, y: 1 - (box.minY + $0.y * box.height))
      }
      if let l = pts.min(by: { $0.x < $1.x }), let r = pts.max(by: { $0.x < $1.x }) {
        out["mouthLeft"] = dict(l)
        out["mouthRight"] = dict(r)
      }
    }
    return out
  }
}

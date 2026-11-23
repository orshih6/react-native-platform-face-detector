package dev.orshih.platformfacedetector

import android.graphics.PointF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark

/**
 * ML Kit, asked one question: what faces are in this picture.
 *
 * Everything is answered as a share of the picture (0..1) with the origin at
 * the top left, the same shape the iOS side answers in.
 */
internal object FaceMlKit {
  fun detector(landmarks: Boolean): FaceDetector =
    FaceDetection.getClient(
      FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(
          if (landmarks) FaceDetectorOptions.LANDMARK_MODE_ALL else FaceDetectorOptions.LANDMARK_MODE_NONE
        )
        .build()
    )

  /**
   * @param width the upright picture's width in pixels
   * @param mirrored flip left and right, for a front-lens frame that is shown
   *   as a selfie preview shows it
   */
  fun describe(
    faces: List<Face>,
    width: Int,
    height: Int,
    landmarks: Boolean,
    mirrored: Boolean
  ): List<Map<String, Any?>> {
    val w = width.toDouble()
    val h = height.toDouble()
    fun x(px: Float): Double = if (mirrored) 1.0 - px / w else px / w

    return faces.map { face ->
      val box = face.boundingBox
      val left = if (mirrored) 1.0 - box.right / w else box.left / w
      val out = mutableMapOf<String, Any?>(
        "bounds" to mapOf(
          "x" to left,
          "y" to box.top / h,
          "width" to box.width() / w,
          "height" to box.height() / h
        ),
        "yaw" to face.headEulerAngleY.toDouble(),
        "pitch" to face.headEulerAngleX.toDouble(),
        "roll" to face.headEulerAngleZ.toDouble()
      )
      if (landmarks) {
        fun at(type: Int): PointF? = face.getLandmark(type)?.position
        fun point(p: PointF): Map<String, Double> = mapOf("x" to x(p.x), "y" to p.y / h)
        val marks = mutableMapOf<String, Any?>()
        // "Left" and "right" are as they APPEAR in the picture, so they are
        // assigned by position rather than trusted from the landmark's name.
        val eyes = listOfNotNull(at(FaceLandmark.LEFT_EYE), at(FaceLandmark.RIGHT_EYE)).map(::point)
          .sortedBy { it["x"] }
        if (eyes.size == 2) {
          marks["leftEye"] = eyes[0]
          marks["rightEye"] = eyes[1]
        }
        at(FaceLandmark.NOSE_BASE)?.let { marks["nose"] = point(it) }
        val mouth = listOfNotNull(at(FaceLandmark.MOUTH_LEFT), at(FaceLandmark.MOUTH_RIGHT)).map(::point)
          .sortedBy { it["x"] }
        if (mouth.size == 2) {
          marks["mouthLeft"] = mouth[0]
          marks["mouthRight"] = mouth[1]
        }
        out["landmarks"] = marks
      }
      out
    }
  }
}

package dev.orshih.platformfacedetector

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import expo.modules.interfaces.permissions.Permissions
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class DetectOptions : Record {
  @Field
  val landmarks: Boolean = false
}

class SnapshotOptions : Record {
  @Field
  val maxWidth: Double? = null

  @Field
  val quality: Double = 0.8

  @Field
  val base64: Boolean = false
}

class FaceDetectorException(message: String) : CodedException(message)

private const val TAG = "PlatformFaceDetector"

class PlatformFaceDetectorModule : Module() {
  private val context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  /**
   * The picture, upright. `fromFilePath` reads the EXIF orientation itself; if
   * it cannot open the picture, decode it by hand and pass the rotation along.
   */
  private fun readImage(source: Uri): InputImage {
    try {
      return InputImage.fromFilePath(context, source)
    } catch (e: Exception) {
      Log.w(TAG, "InputImage.fromFilePath failed for $source, decoding by hand", e)
    }
    val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
      ?: throw IOException("nothing to read")
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      ?: throw IOException("not a picture")
    val orientation = ExifInterface(ByteArrayInputStream(bytes))
      .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val rotation = when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> 90
      ExifInterface.ORIENTATION_ROTATE_180 -> 180
      ExifInterface.ORIENTATION_ROTATE_270 -> 270
      else -> 0
    }
    return InputImage.fromBitmap(bitmap, rotation)
  }

  override fun definition() = ModuleDefinition {
    Name("PlatformFaceDetector")

    AsyncFunction("detectFacesInImage") { uri: String, options: DetectOptions, promise: Promise ->
      val parsed = Uri.parse(uri)
      val source = if (parsed.scheme == null) Uri.fromFile(File(uri)) else parsed
      val image = try {
        readImage(source)
      } catch (e: Exception) {
        promise.reject(
          FaceDetectorException("Could not read an image at $uri (${e.javaClass.simpleName}: ${e.message})")
        )
        return@AsyncFunction
      }
      val detector = FaceMlKit.detector(options.landmarks)
      val sideways = image.rotationDegrees == 90 || image.rotationDegrees == 270
      val width = if (sideways) image.height else image.width
      val height = if (sideways) image.width else image.height
      detector.process(image)
        .addOnSuccessListener { faces ->
          promise.resolve(
            mapOf(
              "faces" to FaceMlKit.describe(faces, width, height, options.landmarks, mirrored = false),
              "width" to width,
              "height" to height
            )
          )
        }
        .addOnFailureListener { e -> promise.reject(FaceDetectorException(e.message ?: "Face detection failed")) }
        .addOnCompleteListener { detector.close() }
    }

    AsyncFunction("getCameraPermission") {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
      ) "granted" else "undetermined"
    }

    AsyncFunction("requestCameraPermission") { promise: Promise ->
      val permissions = appContext.permissions
      if (permissions == null) {
        promise.reject(FaceDetectorException("Permissions are not available"))
        return@AsyncFunction
      }
      permissions.askForPermissions(
        { result ->
          val status = result[Manifest.permission.CAMERA]?.status
          promise.resolve(
            when (status) {
              expo.modules.interfaces.permissions.PermissionsStatus.GRANTED -> "granted"
              expo.modules.interfaces.permissions.PermissionsStatus.DENIED -> "denied"
              else -> "undetermined"
            }
          )
        },
        Manifest.permission.CAMERA
      )
    }

    View(FaceCameraView::class) {
      Events("onFacesDetected", "onCameraReady", "onError")

      Prop("facing") { view: FaceCameraView, facing: String? -> view.setFacing(facing != "back") }
      Prop("active") { view: FaceCameraView, active: Boolean? -> view.setActive(active ?: true) }
      Prop("landmarks") { view: FaceCameraView, landmarks: Boolean? -> view.setLandmarks(landmarks ?: false) }
      Prop("minIntervalMs") { view: FaceCameraView, interval: Double? ->
        view.minIntervalMs = (interval ?: 100.0).coerceAtLeast(0.0).toLong()
      }

      AsyncFunction("takeSnapshot") { view: FaceCameraView, options: SnapshotOptions? ->
        view.snapshot(options ?: SnapshotOptions())
      }
    }
  }
}

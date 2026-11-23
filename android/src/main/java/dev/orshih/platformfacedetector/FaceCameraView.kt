package dev.orshih.platformfacedetector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Base64
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A live camera picture that reports the faces in it.
 *
 * The front lens is reported MIRRORED, as its preview is shown, so what the
 * events describe, what is on screen and what a snapshot holds are one picture
 * — the same convention the iOS side follows.
 */
class FaceCameraView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  private val onFacesDetected by EventDispatcher<Map<String, Any?>>()
  private val onCameraReady by EventDispatcher<Map<String, Any?>>()
  private val onError by EventDispatcher<Map<String, Any?>>()

  private val preview = PreviewView(context).apply {
    layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    // A TextureView underneath, so `bitmap` (the snapshot) is always readable.
    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    scaleType = PreviewView.ScaleType.FILL_CENTER
  }
  private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

  private var front = true
  private var active = true
  private var landmarks = false
  var minIntervalMs: Long = 100

  private var provider: ProcessCameraProvider? = null
  private var detector: FaceDetector? = null
  private var lastAnalysis = 0L
  private var announcedReady = false

  init {
    addView(preview)
  }

  fun setFacing(isFront: Boolean) {
    if (front != isFront) {
      front = isFront
      bind()
    }
  }

  fun setActive(isActive: Boolean) {
    if (active != isActive) {
      active = isActive
      bind()
    }
  }

  fun setLandmarks(wanted: Boolean) {
    if (landmarks != wanted) {
      landmarks = wanted
      detector?.close()
      detector = null
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    bind()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    provider?.unbindAll()
    detector?.close()
    detector = null
  }

  // React Native does not lay out native children added outside its tree.
  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    preview.measure(
      MeasureSpec.makeMeasureSpec(r - l, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(b - t, MeasureSpec.EXACTLY)
    )
    preview.layout(0, 0, r - l, b - t)
  }

  private fun fail(code: String, message: String) {
    post { onError(mapOf("code" to code, "message" to message)) }
  }

  private fun bind() {
    if (!isAttachedToWindow) return
    if (!active) {
      provider?.unbindAll()
      return
    }
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      fail("NO_PERMISSION", "Camera permission has not been granted")
      return
    }
    val owner = appContext.currentActivity as? LifecycleOwner
    if (owner == null) {
      fail("SESSION_FAILED", "There is no activity to run the camera in")
      return
    }
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
      try {
        val cameraProvider = future.get()
        provider = cameraProvider
        val selector =
          if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        if (!cameraProvider.hasCamera(selector)) {
          fail("NO_CAMERA", "No camera is available")
          return@addListener
        }
        val previewUseCase = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
          .build()
          .also { it.setAnalyzer(analysisExecutor, ::analyze) }
        cameraProvider.unbindAll()
        announcedReady = false
        cameraProvider.bindToLifecycle(owner, selector, previewUseCase, analysis)
      } catch (e: Exception) {
        fail("SESSION_FAILED", e.message ?: "The camera could not be started")
      }
    }, ContextCompat.getMainExecutor(context))
  }

  @OptIn(ExperimentalGetImage::class)
  private fun analyze(frame: ImageProxy) {
    if (!announcedReady) {
      announcedReady = true
      post { onCameraReady(emptyMap()) }
    }
    val now = System.currentTimeMillis()
    val media = frame.image
    if (media == null || now - lastAnalysis < minIntervalMs) {
      frame.close()
      return
    }
    lastAnalysis = now

    val rotation = frame.imageInfo.rotationDegrees
    val sideways = rotation == 90 || rotation == 270
    val width = if (sideways) frame.height else frame.width
    val height = if (sideways) frame.width else frame.height
    val wantLandmarks = landmarks
    val mirrored = front
    val active = detector ?: FaceMlKit.detector(wantLandmarks).also { detector = it }

    active.process(InputImage.fromMediaImage(media, rotation))
      .addOnSuccessListener { faces ->
        val event = mapOf(
          "faces" to FaceMlKit.describe(faces, width, height, wantLandmarks, mirrored),
          "frameWidth" to width,
          "frameHeight" to height,
          "timestamp" to now.toDouble()
        )
        post { onFacesDetected(event) }
      }
      // The frame must stay open until ML Kit has finished reading it.
      .addOnCompleteListener { frame.close() }
  }

  fun snapshot(options: SnapshotOptions): Map<String, Any?> {
    var bitmap = preview.bitmap ?: throw FaceDetectorException("The camera has not produced a frame yet")
    val maxWidth = options.maxWidth
    if (maxWidth != null && maxWidth > 0 && bitmap.width > maxWidth) {
      val scale = maxWidth / bitmap.width
      bitmap = Bitmap.createScaledBitmap(bitmap, maxWidth.toInt(), (bitmap.height * scale).toInt(), true)
    }
    val quality = (options.quality.coerceIn(0.0, 1.0) * 100).toInt()
    val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
      .toByteArray()
    val out = mutableMapOf<String, Any?>("width" to bitmap.width, "height" to bitmap.height)
    if (options.base64) {
      out["base64"] = Base64.encodeToString(bytes, Base64.NO_WRAP)
    } else {
      val file = File(context.cacheDir, "face-${UUID.randomUUID()}.jpg")
      file.writeBytes(bytes)
      out["uri"] = "file://${file.absolutePath}"
    }
    return out
  }
}

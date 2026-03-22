package com.awesomecyborg.cakemonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraManager(private val context: Context) {
    companion object {
        private const val TAG = "CameraManager"
        private const val CAPTURE_TIMEOUT_MS = 10000L
        private const val AF_TIMEOUT_MS = 5000L
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var isBusy = false

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    fun capturePhoto(onComplete: (File?, String?) -> Unit) {
        if (isBusy) {
            onComplete(null, "Camera is busy with another capture")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            onComplete(null, "Camera permission not granted")
            return
        }

        isBusy = true
        startBackgroundThread()

        try {
            val cameraId = getCameraId() ?: run {
                isBusy = false
                stopBackgroundThread()
                onComplete(null, "No rear camera found")
                return
            }

            openCameraAndCapture(cameraId, onComplete)
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating capture", e)
            isBusy = false
            stopBackgroundThread()
            onComplete(null, "Failed to open camera: ${e.message}")
        }
    }

    private fun getCameraId(): String? {
        return cameraManager.cameraIdList.find {
            val characteristics = cameraManager.getCameraCharacteristics(it)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    private fun openCameraAndCapture(cameraId: String, onComplete: (File?, String?) -> Unit) {
        val timeoutRunnable = Runnable {
            Log.e(TAG, "Camera capture timeout")
            cleanup()
            isBusy = false
            onComplete(null, "Capture timeout (10 seconds)")
        }

        backgroundHandler?.postDelayed(timeoutRunnable, CAPTURE_TIMEOUT_MS)

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession(camera, onComplete, timeoutRunnable)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(TAG, "Camera disconnected")
                    camera.close()
                    cameraDevice = null
                    backgroundHandler?.removeCallbacks(timeoutRunnable)
                    cleanup()
                    isBusy = false
                    onComplete(null, "Camera disconnected")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    camera.close()
                    cameraDevice = null
                    backgroundHandler?.removeCallbacks(timeoutRunnable)
                    cleanup()
                    isBusy = false
                    onComplete(null, "Camera error: $error")
                }
            }, backgroundHandler)
        } catch (e: SecurityException) {
            backgroundHandler?.removeCallbacks(timeoutRunnable)
            cleanup()
            isBusy = false
            onComplete(null, "Camera permission denied")
        }
    }

    private fun createCaptureSession(camera: CameraDevice, onComplete: (File?, String?) -> Unit, timeoutRunnable: Runnable) {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(camera.id)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val largestSize = map?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width * it.height }
                ?: run {
                    backgroundHandler?.removeCallbacks(timeoutRunnable)
                    cleanup()
                    isBusy = false
                    onComplete(null, "No suitable image sizes found")
                    return
                }

            imageReader = ImageReader.newInstance(
                largestSize.width,
                largestSize.height,
                ImageFormat.JPEG,
                1
            ).apply {
                setOnImageAvailableListener({ reader ->
                    backgroundHandler?.removeCallbacks(timeoutRunnable)
                    val image = reader.acquireNextImage()
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        val photoFile = createImageFile()
                        FileOutputStream(photoFile).use { it.write(bytes) }

                        Log.i(TAG, "Photo saved: ${photoFile.absolutePath}")
                        cleanup()
                        isBusy = false
                        onComplete(photoFile, null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving image", e)
                        cleanup()
                        isBusy = false
                        onComplete(null, "Error saving image: ${e.message}")
                    } finally {
                        image.close()
                    }
                }, backgroundHandler)
            }

            camera.createCaptureSession(
                listOf(imageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        triggerCapture(session, camera, onComplete, timeoutRunnable)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configuration failed")
                        backgroundHandler?.removeCallbacks(timeoutRunnable)
                        cleanup()
                        isBusy = false
                        onComplete(null, "Failed to configure capture session")
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating capture session", e)
            backgroundHandler?.removeCallbacks(timeoutRunnable)
            cleanup()
            isBusy = false
            onComplete(null, "Error creating capture session: ${e.message}")
        }
    }

    private fun triggerCapture(session: CameraCaptureSession, camera: CameraDevice, onComplete: (File?, String?) -> Unit, timeoutRunnable: Runnable) {
        try {
            val jpegQuality = Config.getJpegQuality(context).toByte()
            val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageReader!!.surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.JPEG_QUALITY, jpegQuality)
            }

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    Log.d(TAG, "Capture completed successfully")
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure
                ) {
                    Log.e(TAG, "Capture failed: ${failure.reason}")
                    backgroundHandler?.removeCallbacks(timeoutRunnable)
                    cleanup()
                    isBusy = false
                    onComplete(null, "Capture failed: ${failure.reason}")
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Error triggering capture", e)
            backgroundHandler?.removeCallbacks(timeoutRunnable)
            cleanup()
            isBusy = false
            onComplete(null, "Error triggering capture: ${e.message}")
        }
    }

    private fun createImageFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = context.cacheDir
        return File(storageDir, "CAKE_$timestamp.jpg")
    }

    private fun cleanup() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            // Post to main thread to avoid self-join deadlock when called from backgroundHandler
            Handler(Looper.getMainLooper()).post { stopBackgroundThread() }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    fun isBusy(): Boolean = isBusy
}

package com.awesomecyborg.cakemonitor

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class CameraMonitorService : Service() {

    companion object {
        private const val TAG = "CameraMonitorService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "CameraMonitorChannel"
        const val ACTION_START = "com.awesomecyborg.cakemonitor.START"
        const val ACTION_STOP = "com.awesomecyborg.cakemonitor.STOP"

        // Shared state for UI updates
        var lastCaptureTime: String? = null
        var lastCallbackResult: String? = null
        var isRunning = false
    }

    private var httpServer: HttpServer? = null
    private var cameraManager: CameraManager? = null
    private var callbackHandler: CallbackHandler? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")

        cameraManager = CameraManager(this)
        callbackHandler = CallbackHandler(this)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
            else -> startMonitoring() // Default to start
        }

        return START_STICKY
    }

    private fun startMonitoring() {
        if (isRunning) {
            Log.i(TAG, "Service already running")
            return
        }

        Log.i(TAG, "Starting monitoring service")

        val notification = buildNotification("Service starting...")
        startForeground(NOTIFICATION_ID, notification)

        isRunning = true

        // Start HTTP server
        val port = Config.getPort(this)
        httpServer = HttpServer(this, port) {
            handleSnapRequest()
        }

        if (httpServer?.startServer() == true) {
            updateNotification("Listening on port $port")
            Log.i(TAG, "HTTP server started successfully on port $port")
        } else {
            updateNotification("Failed to start server")
            Log.e(TAG, "Failed to start HTTP server")
        }
    }

    private fun stopMonitoring() {
        Log.i(TAG, "Stopping monitoring service")

        isRunning = false

        httpServer?.stopServer()
        httpServer = null

        releaseWakeLock()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleSnapRequest() {
        if (cameraManager?.isBusy() == true) {
            Log.w(TAG, "Camera is busy, ignoring snap request")
            return
        }

        updateNotification("Capturing photo...")
        acquireWakeLock()

        httpServer?.setCaptureBusy(true)

        cameraManager?.capturePhoto { photoFile, errorMessage ->
            httpServer?.setCaptureBusy(false)

            val timestamp = getCurrentTimestamp()
            lastCaptureTime = timestamp

            if (photoFile != null) {
                Log.i(TAG, "Photo captured successfully, sending callback")
                updateNotification("Sending photo...")

                callbackHandler?.sendCallback(photoFile, null) { success, message ->
                    lastCallbackResult = if (success) "Success" else "Failed: $message"
                    updateNotification("Ready - Last: $timestamp")
                    releaseWakeLock()
                    Log.i(TAG, "Callback completed: $message")
                }
            } else {
                Log.e(TAG, "Photo capture failed: $errorMessage")
                updateNotification("Capture failed")

                // Send error callback
                callbackHandler?.sendCallback(null, errorMessage) { success, message ->
                    lastCallbackResult = if (success) "Error reported" else "Failed: $message"
                    updateNotification("Ready - Last failed")
                    releaseWakeLock()
                    Log.i(TAG, "Error callback completed: $message")
                }
            }
        }
    }

    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy, hh:mm a", Locale.US)
        return dateFormat.format(Date())
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CakeMonitor::CaptureWakeLock"
            )
        }

        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(30000) // 30 second timeout
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Camera Monitor Service"
            val descriptionText = "Runs camera monitoring in background"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val location = Config.getLocation(this)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cake Monitor - $location")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val notification = buildNotification(statusText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service onDestroy")

        isRunning = false
        httpServer?.stopServer()
        releaseWakeLock()
    }
}

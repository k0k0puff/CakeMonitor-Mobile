package com.awesomecyborg.cakemonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed, checking if service should auto-start")

            // Only auto-start if configuration is complete
            if (Config.isConfigured(context)) {
                Log.i(TAG, "Configuration found, starting service")

                val serviceIntent = Intent(context, CameraMonitorService::class.java).apply {
                    action = CameraMonitorService.ACTION_START
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.w(TAG, "Configuration not complete, skipping auto-start")
            }
        }
    }
}

package com.awesomecyborg.cakemonitor

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CallbackHandler(private val context: Context) {
    companion object {
        private const val TAG = "CallbackHandler"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun sendCallback(photoFile: File?, errorMessage: String?, telegramId: String, onComplete: (Boolean, String) -> Unit) {
        val callbackUrl = Config.getCallbackUrl(context)
        if (callbackUrl.isBlank()) {
            Log.e(TAG, "Callback URL not configured")
            onComplete(false, "Callback URL not configured")
            return
        }

        sendWithRetry(photoFile, errorMessage, telegramId, callbackUrl, 0, onComplete)
    }

    private fun sendWithRetry(
        photoFile: File?,
        errorMessage: String?,
        telegramId: String,
        callbackUrl: String,
        attemptNumber: Int,
        onComplete: (Boolean, String) -> Unit
    ) {
        if (attemptNumber >= MAX_RETRIES) {
            val msg = "All $MAX_RETRIES retry attempts exhausted"
            Log.e(TAG, msg)
            onComplete(false, msg)
            return
        }

        val location = Config.getLocation(context)
        val deviceId = Config.getDeviceId(context)
        val timestamp = getCurrentTimestamp()

        val requestBody = if (photoFile != null && photoFile.exists()) {
            // Success callback with photo
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "photo",
                    photoFile.name,
                    photoFile.asRequestBody("image/jpeg".toMediaType())
                )
                .addFormDataPart("location", location)
                .addFormDataPart("timestamp", timestamp)
                .addFormDataPart("device_id", deviceId)
                .addFormDataPart("telegram_id", telegramId)
                .addFormDataPart("status", "ok")
                .build()
        } else {
            // Error callback without photo
            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("location", location)
                .addFormDataPart("timestamp", timestamp)
                .addFormDataPart("device_id", deviceId)
                .addFormDataPart("telegram_id", telegramId)
                .addFormDataPart("status", "error")

            errorMessage?.let {
                builder.addFormDataPart("error_message", it)
            }

            builder.build()
        }

        val request = Request.Builder()
            .url(callbackUrl)
            .post(requestBody)
            .build()

        val attemptLog = if (attemptNumber > 0) " (attempt ${attemptNumber + 1}/$MAX_RETRIES)" else ""
        Log.i(TAG, "Sending callback$attemptLog to $callbackUrl")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Callback failed$attemptLog: ${e.message}", e)

                if (attemptNumber < MAX_RETRIES - 1) {
                    Log.i(TAG, "Retrying in ${RETRY_DELAY_MS}ms...")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        sendWithRetry(photoFile, errorMessage, telegramId, callbackUrl, attemptNumber + 1, onComplete)
                    }, RETRY_DELAY_MS)
                } else {
                    onComplete(false, "Network error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        Log.i(TAG, "Callback successful: ${response.code}")
                        onComplete(true, "Success")

                        // Clean up photo file after successful send
                        photoFile?.delete()
                    } else {
                        Log.e(TAG, "Callback returned ${response.code}$attemptLog")

                        if (attemptNumber < MAX_RETRIES - 1) {
                            Log.i(TAG, "Retrying in ${RETRY_DELAY_MS}ms...")
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                sendWithRetry(photoFile, errorMessage, telegramId, callbackUrl, attemptNumber + 1, onComplete)
                            }, RETRY_DELAY_MS)
                        } else {
                            onComplete(false, "HTTP ${response.code}")
                        }
                    }
                }
            }
        })
    }

    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy, hh:mm a", Locale.US)
        return dateFormat.format(Date())
    }
}

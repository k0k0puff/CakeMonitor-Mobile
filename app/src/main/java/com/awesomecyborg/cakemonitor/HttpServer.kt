package com.awesomecyborg.cakemonitor

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class HttpServer(
    private val context: Context,
    private val port: Int,
    private val onSnapRequest: (String) -> Unit
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "HttpServer"
    }

    private var isCaptureInProgress = false

    fun setCaptureBusy(busy: Boolean) {
        isCaptureInProgress = busy
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(TAG, "Received $method request to $uri")

        return when {
            uri == "/snap" && method == Method.POST -> handleSnap(session)
            uri == "/health" && method == Method.GET -> handleHealth()
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                JSONObject().apply {
                    put("error", "Not found")
                }.toString()
            )
        }
    }

    private fun handleSnap(session: IHTTPSession): Response {
        // Parse request body
        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse request body", e)
        }

        val telegramId = extractTelegramId(session, files)
        if (telegramId.isBlank()) {
            Log.w(TAG, "/snap request missing telegram_id")
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                JSONObject().apply {
                    put("error", "Missing required parameter: telegram_id")
                }.toString()
            )
        }

        return if (isCaptureInProgress) {
            Log.i(TAG, "/snap request received, but capture already in progress")
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                JSONObject().apply {
                    put("status", "busy")
                }.toString()
            )
        } else {
            Log.i(TAG, "/snap request received (telegram_id=$telegramId), triggering photo capture")

            // Trigger photo capture asynchronously
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onSnapRequest(telegramId)
            }

            // Return immediate acknowledgment
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                JSONObject().apply {
                    put("status", "acknowledged")
                }.toString()
            )
        }
    }

    private fun extractTelegramId(session: IHTTPSession, files: HashMap<String, String>): String {
        // Try JSON body first
        val postData = files["postData"]
        if (!postData.isNullOrBlank()) {
            try {
                val json = JSONObject(postData)
                val id = json.optString("telegram_id", "")
                if (id.isNotBlank()) return id
            } catch (e: Exception) {
                // Not JSON, fall through to form params
            }
        }

        // Try URL-encoded form params
        return session.parameters["telegram_id"]?.firstOrNull() ?: ""
    }

    private fun handleHealth(): Response {
        val location = Config.getLocation(context)

        Log.d(TAG, "/health request received")

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            JSONObject().apply {
                put("status", "ok")
                put("location", location)
            }.toString()
        )
    }

    fun startServer(): Boolean {
        return try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.i(TAG, "HTTP server started on port $port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HTTP server", e)
            false
        }
    }

    fun stopServer() {
        stop()
        Log.i(TAG, "HTTP server stopped")
    }
}

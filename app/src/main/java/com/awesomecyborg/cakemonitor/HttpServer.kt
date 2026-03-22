package com.awesomecyborg.cakemonitor

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class HttpServer(
    private val context: Context,
    private val port: Int,
    private val onSnapRequest: () -> Unit
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
            uri == "/snap" && method == Method.POST -> handleSnap()
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

    private fun handleSnap(): Response {
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
            Log.i(TAG, "/snap request received, triggering photo capture")

            // Trigger photo capture asynchronously
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onSnapRequest()
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

package com.awesomecyborg.cakemonitor

import android.content.Context
import android.content.SharedPreferences

object Config {
    private const val PREFS_NAME = "CakeMonitorPrefs"
    private const val KEY_LOCATION = "location_label"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_PORT = "http_port"
    private const val KEY_CALLBACK_URL = "callback_url"
    private const val KEY_JPEG_QUALITY = "jpeg_quality"
    private const val KEY_FIRST_LAUNCH = "first_launch"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isConfigured(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_LOCATION, null) != null &&
                prefs.getString(KEY_DEVICE_ID, null) != null &&
                prefs.getString(KEY_CALLBACK_URL, null) != null
    }

    fun getLocation(context: Context): String {
        return getPrefs(context).getString(KEY_LOCATION, "Unknown") ?: "Unknown"
    }

    fun setLocation(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_LOCATION, value).apply()
    }

    fun getDeviceId(context: Context): String {
        return getPrefs(context).getString(KEY_DEVICE_ID, "device-1") ?: "device-1"
    }

    fun setDeviceId(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_DEVICE_ID, value).apply()
    }

    fun getPort(context: Context): Int {
        return getPrefs(context).getInt(KEY_PORT, 8080)
    }

    fun setPort(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_PORT, value).apply()
    }

    fun getCallbackUrl(context: Context): String {
        return getPrefs(context).getString(KEY_CALLBACK_URL, "") ?: ""
    }

    fun setCallbackUrl(context: Context, value: String) {
        getPrefs(context).edit().putString(KEY_CALLBACK_URL, value).apply()
    }

    fun getJpegQuality(context: Context): Int {
        return getPrefs(context).getInt(KEY_JPEG_QUALITY, 85)
    }

    fun setJpegQuality(context: Context, value: Int) {
        getPrefs(context).edit().putInt(KEY_JPEG_QUALITY, value).apply()
    }

    fun isFirstLaunch(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchComplete(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }
}

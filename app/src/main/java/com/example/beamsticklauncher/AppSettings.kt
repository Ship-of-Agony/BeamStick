package com.example.beamsticklauncher

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {

    companion object {
        const val VALUE_ALWAYS_ASK = "ALWAYS_ASK"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("beamstick_prefs", Context.MODE_PRIVATE)

    var isAutostartEnabled: Boolean
        get() = prefs.getBoolean("key_autostart", true)
        set(value) = prefs.edit().putBoolean("key_autostart", value).apply()

    var defaultVideoPlayer: String
        get() = prefs.getString("key_default_video", VALUE_ALWAYS_ASK) ?: VALUE_ALWAYS_ASK
        set(value) = prefs.edit().putString("key_default_video", value).apply()

    var defaultAudioPlayer: String
        get() = prefs.getString("key_default_audio", VALUE_ALWAYS_ASK) ?: VALUE_ALWAYS_ASK
        set(value) = prefs.edit().putString("key_default_audio", value).apply()

    var defaultImageViewer: String
        get() = prefs.getString("key_default_image", VALUE_ALWAYS_ASK) ?: VALUE_ALWAYS_ASK
        set(value) = prefs.edit().putString("key_default_image", value).apply()
}
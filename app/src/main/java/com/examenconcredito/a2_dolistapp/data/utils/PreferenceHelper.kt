package com.examenconcredito.a2_dolistapp.data.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import java.util.UUID

class PreferenceHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    // CONSTANTS FOR PREFERENCE KEYS
    companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_FIREBASE_UID = "firebase_uid"
        const val KEY_DARK_MODE = "dark_mode_enabled"
    }

    fun getUniqueDeviceId(): String {
        val firebaseUid = prefs.getString(KEY_FIREBASE_UID, null)
        println("DEBUG: Retrieved Firebase UID: $firebaseUid")

        if (firebaseUid != null) {
            return firebaseUid
        }

        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = "user_${UUID.randomUUID()}"
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        println("DEBUG: Using device ID: $deviceId")
        return deviceId
    }

    fun saveFirebaseUid(uid: String) {
        println("DEBUG: Saving Firebase UID: $uid")
        prefs.edit().putString(KEY_FIREBASE_UID, uid).apply()
        // VERIFY IT WAS SAVED
        val savedUid = prefs.getString(KEY_FIREBASE_UID, null)
        println("DEBUG: UID saved verification: $savedUid")
    }

    fun clearFirebaseUid() {
        prefs.edit().remove(KEY_FIREBASE_UID).apply()
    }

    fun clearDeviceId() {
        prefs.edit().remove(KEY_DEVICE_ID).apply()
    }

    // SAVE OTHER PREFS
    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    // DARK MODE
    fun isDarkModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_DARK_MODE, false) // DEFAULT: LIGHT MODE
    }

    fun setDarkModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun applySavedTheme() {
        if (isDarkModeEnabled()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}
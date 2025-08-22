package com.examenconcredito.a2_dolistapp.data.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import java.util.UUID

class PreferenceHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    //CONSTANTS FOR PREFERENCE KEYS
    companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_FIREBASE_UID = "firebase_uid"
        const val KEY_DARK_MODE = "dark_mode_enabled"
    }
    fun getUniqueDeviceId(): String {
        var deviceId = getString("device_id", "")
        if (deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString()
            saveString("device_id", deviceId)
        }
        return deviceId
    }
    fun saveFirebaseUid(uid: String) {
        saveString("firebase_uid", uid)
    }
    fun getFirebaseUid(): String {
        return getString("firebase_uid", "")
    }
    fun clearUserData() {
        // DELETE FIREBASE UID, KEEP DEVICE ID
        saveString("firebase_uid", "")
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


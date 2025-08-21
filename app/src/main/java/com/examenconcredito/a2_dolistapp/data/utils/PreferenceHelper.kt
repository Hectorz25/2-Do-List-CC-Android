package com.examenconcredito.a2_dolistapp.data.utils

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class PreferenceHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun getUniqueDeviceId(): String {
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = "user_${UUID.randomUUID()}"
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    fun clearDeviceId() {
        prefs.edit().remove("device_id").apply()
    }

    //Function to save other prefs if need
    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }
}
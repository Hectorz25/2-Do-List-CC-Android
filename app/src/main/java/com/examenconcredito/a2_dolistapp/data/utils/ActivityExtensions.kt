package com.examenconcredito.a2_dolistapp.utils

import android.app.Activity
import android.content.Intent
import androidx.fragment.app.Fragment

object ActivityExtensions {
    fun Activity.navigateTo(intent: Intent, enterAnim: Int, exitAnim: Int) {
        startActivity(intent)
        overridePendingTransition(enterAnim, exitAnim)
    }

    fun Activity.finishWithAnimation(enterAnim: Int, exitAnim: Int) {
        finish()
        overridePendingTransition(enterAnim, exitAnim)
    }

    fun Fragment.navigateTo(intent: Intent, enterAnim: Int, exitAnim: Int) {
        requireActivity().navigateTo(intent, enterAnim, exitAnim)
    }
}
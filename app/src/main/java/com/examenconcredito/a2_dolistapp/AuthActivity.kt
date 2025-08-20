package com.examenconcredito.a2_dolistapp

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.switchmaterial.SwitchMaterial

class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_auth)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val swDarkMode = findViewById<SwitchMaterial>(R.id.swThemeSelector)
        swDarkMode.setOnCheckedChangeListener {_, isSelected ->
            if(isSelected){
                enableDarkMode()
            }else{
                disableDarkMode()
            }
        }
    }
    private fun enableDarkMode(){
        animateThemeChange()
        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES)
        delegate.applyDayNight()
    }

    private fun disableDarkMode(){
        animateThemeChange()
        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO)
        delegate.applyDayNight()
    }

    private fun animateThemeChange() {
        val root = findViewById<View>(R.id.main)
        root.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                delegate.applyDayNight()
                root.alpha = 0f
                root.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }.start()
    }
}
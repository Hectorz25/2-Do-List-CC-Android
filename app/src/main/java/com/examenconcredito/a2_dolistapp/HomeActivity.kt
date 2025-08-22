package com.examenconcredito.a2_dolistapp

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.examenconcredito.a2_dolistapp.data.utils.PreferenceHelper
import com.examenconcredito.a2_dolistapp.databinding.ActivityHomeBinding
import com.google.android.material.switchmaterial.SwitchMaterial

class HomeActivity : AppCompatActivity() {
    private val preferenceHelper by lazy { PreferenceHelper(this) }
    private lateinit var binding: ActivityHomeBinding

    // VARIABLE TO PREVENT SWITCH RECURSION
    private var isThemeChangeInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // APPLY SAVED THEME BEFORE SUPER
        preferenceHelper.applySavedTheme()
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        // SETUP TOOLBAR
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // GET USER DATA FROM INTENT
        val userId = intent.getStringExtra("USER_ID") ?: "N/A"
        val userName = intent.getStringExtra("USER_NAME") ?: "N/A"
        val userLastName = intent.getStringExtra("USER_LAST_NAME") ?: "N/A"
        val userUsername = intent.getStringExtra("USER_USERNAME") ?: "N/A"
        val userEmail = intent.getStringExtra("USER_EMAIL") ?: "N/A"
        val userLogin = intent.getBooleanExtra("USER_LOGIN", false)

        // SET USER DATA TO TEXTVIEWS
        binding.tvUserId.text = "ID: $userId"
        binding.tvUserName.text = "Name: $userName"
        binding.tvUserLastName.text = "Last Name: $userLastName"
        binding.tvUserUsername.text = "Username: $userUsername"
        binding.tvUserEmail.text = "Email: $userEmail"
        binding.tvUserLogin.text = "Login Status: $userLogin"

        // SETUP THEME SWITCH WITH RECURSION PROTECTION
        val swDarkMode = findViewById<SwitchMaterial>(R.id.swThemeSelector)
        swDarkMode.isChecked = preferenceHelper.isDarkModeEnabled()

        swDarkMode.setOnCheckedChangeListener { _, isSelected ->
            if (isThemeChangeInProgress) return@setOnCheckedChangeListener

            isThemeChangeInProgress = true
            if (isSelected) {
                enableDarkMode()
            } else {
                disableDarkMode()
            }
            preferenceHelper.setDarkModeEnabled(isSelected)
            Toast.makeText(this,
                if (isSelected) "Dark mode activated" else "Light mode activated",
                Toast.LENGTH_SHORT).show()

            // RESET FLAG AFTER DELAY
            swDarkMode.postDelayed({
                isThemeChangeInProgress = false
            }, 1000)
        }
    }

    // INFLATE THE MENU
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        return true
    }

    // HANDLE MENU ITEM CLICKS - ONLY USER ICON
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_user -> {
                // SHOW POPUP MENU WHEN USER ICON IS CLICKED
                showUserOptionsPopup()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // SHOW POPUP MENU WITH OPTIONS
    private fun showUserOptionsPopup() {
        val popupMenu = android.widget.PopupMenu(this, findViewById(R.id.action_user))
        popupMenu.menuInflater.inflate(R.menu.user_popup_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.popup_logout -> {
                    performLogout()
                    true
                }
                R.id.popup_settings -> {
                    showSettings()
                    true
                }
                R.id.popup_profile -> {
                    showProfile()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun performLogout() {
        Toast.makeText(this, "Logout clicked", Toast.LENGTH_SHORT).show()
        // Firebase.auth.signOut()
        // preferenceHelper.clearFirebaseUid()
        // Redirect to AuthActivity
    }

    private fun showSettings() {
        Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show()
    }

    private fun showProfile() {
        Toast.makeText(this, "Profile clicked", Toast.LENGTH_SHORT).show()
    }

    private fun enableDarkMode() {
        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES)
        delegate.applyDayNight()
    }

    private fun disableDarkMode() {
        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO)
        delegate.applyDayNight()
    }
}
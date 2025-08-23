package com.examenconcredito.a2_dolistapp

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.examenconcredito.a2_dolistapp.data.database.AppDatabase
import com.examenconcredito.a2_dolistapp.data.utils.PreferenceHelper
import com.examenconcredito.a2_dolistapp.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {
    private val preferenceHelper by lazy { PreferenceHelper(this) }
    private lateinit var binding: ActivityProfileBinding
    private val auth by lazy { Firebase.auth }
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var isThemeChangeInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // SAVED THEME
        preferenceHelper.applySavedTheme()
        super.onCreate(savedInstanceState)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        // TOOLBAR
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = "Profile"

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // GET USER DATA
        val userId = intent.getStringExtra("USER_ID") ?: "N/A"
        val userName = intent.getStringExtra("USER_NAME") ?: "N/A"
        val userLastName = intent.getStringExtra("USER_LAST_NAME") ?: "N/A"
        val userUsername = intent.getStringExtra("USER_USERNAME") ?: "N/A"
        val userEmail = intent.getStringExtra("USER_EMAIL") ?: "N/A"
        val userLogin = intent.getBooleanExtra("USER_LOGIN", false)

        // SET USER DATA
        binding.tvUserId.text = "ID: $userId"
        binding.tvUserName.text = "Name: $userName"
        binding.tvUserLastName.text = "Last Name: $userLastName"
        binding.tvUserUsername.text = "Username: $userUsername"
        binding.tvUserEmail.text = "Email: $userEmail"
        binding.tvUserLogin.text = "Login Status: $userLogin"

        // THEME SWITCH WITH RECURSION PROTECTION
        val swDarkMode = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.swThemeSelector)
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
                if (isSelected) "DARK MODE ENABLED" else "LIGHT MODE ENABLED",
                Toast.LENGTH_SHORT).show()
            swDarkMode.postDelayed({
                isThemeChangeInProgress = false
            }, 1000)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        return true
    }

    // MENU ITEM CLICKS
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_user -> {
                // SHOW POPUP MENU
                showUserOptionsPopup()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // SHOW POPUP MENU
    private fun showUserOptionsPopup() {
        val popupMenu = android.widget.PopupMenu(this, findViewById(R.id.action_user))
        popupMenu.menuInflater.inflate(R.menu.user_popup_menu, popupMenu.menu)
        try {
            val fieldMPopup = popupMenu::class.java.getDeclaredField("mPopup")
            fieldMPopup.isAccessible = true
            val mPopup = fieldMPopup.get(popupMenu)
            mPopup::class.java
                .getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                .invoke(mPopup, true)
        } catch (e: Exception) {
        }
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.popup_profile -> {
                    Toast.makeText(this, "ALREADY IN PROFILE", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.popup_logout -> {
                    performLogout()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun performLogout() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userId = intent.getStringExtra("USER_ID") ?: ""
                val userEmail = intent.getStringExtra("USER_EMAIL") ?: ""
                val isGuestUser = userEmail.endsWith("@invitado.com")

                if (isGuestUser) {
                    // FOR GUEST: UPDATE LOGIN STATUS ONLY
                    db.userDao().updateLoginStatus(userId, false)
                } else {
                    // FOR FIREBASE: SIGN OUT AND CLEAN UP
                    auth.signOut()
                    preferenceHelper.clearUserData()
                    db.userDao().deleteAllUsers() // DELETE ALL USERS
                }

                withContext(Dispatchers.Main) {
                    // REDIRECT TO AUTH ACTIVITY AND CLEAR BACK STACK
                    val intent = Intent(this@ProfileActivity, AuthActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProfileActivity, "LOGOUT ERROR: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun enableDarkMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        delegate.applyDayNight()
    }

    private fun disableDarkMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        delegate.applyDayNight()
    }
}
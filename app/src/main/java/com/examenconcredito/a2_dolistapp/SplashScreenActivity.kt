package com.examenconcredito.a2_dolistapp

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.examenconcredito.a2_dolistapp.data.database.AppDatabase
import com.examenconcredito.a2_dolistapp.data.entities.UserEntity
import com.examenconcredito.a2_dolistapp.data.utils.PreferenceHelper
import com.examenconcredito.a2_dolistapp.databinding.ActivitySplashscreenBinding
import com.examenconcredito.a2_dolistapp.utils.ActivityExtensions.navigateTo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SplashScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashscreenBinding
    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private val firestore by lazy { Firebase.firestore }
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val preferenceHelper by lazy { PreferenceHelper(this) }
    private val persistentDeviceId by lazy { preferenceHelper.getUniqueDeviceId() }
    private val SPLASH_DELAY = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            // APPLY THEME BEFORE SUPER
            preferenceHelper.applySavedTheme()
            super.onCreate(savedInstanceState)

            binding = ActivitySplashscreenBinding.inflate(layoutInflater)
            setContentView(binding.root)
            enableEdgeToEdge()

            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
            checkUserSession()
        } catch (e: Exception) {
            println("DEBUG: FATAL ERROR IN ONCREATE: ${e.message}")
            e.printStackTrace()
            redirectToAuth()
        }
    }

    private fun checkUserSession() {
        lifecycleScope.launch {
            try {
                // CHECK FIREBASE AUTH USER FIRST
                val currentFirebaseUser = auth.currentUser

                if (currentFirebaseUser != null) {
                    // FIREBASE USER AUTHENTICATED - CHECK FIRESTORE
                    val firestoreUser = getFirestoreUser(currentFirebaseUser.uid)

                    if (firestoreUser != null) {
                        // SAVE USER TO LOCAL DB AND REDIRECT
                        withContext(Dispatchers.IO) {
                            db.userDao().deleteAllUsers()
                            db.userDao().insertUser(firestoreUser.copy(login = true))
                        }
                        redirectToHome(firestoreUser)
                    } else {
                        // CREATE NEW USER IN FIRESTORE IF NOT EXISTS (FOR EMAIL/PASSWORD USERS)
                        createNewFirebaseUser(currentFirebaseUser)
                    }
                    return@launch
                }

                // NO FIREBASE USER - CHECK LOCAL USER
                checkLocalUser()

            } catch (e: Exception) {
                println("DEBUG: EXCEPTION OCCURRED: ${e.message}")
                redirectToAuth()
            }
        }
    }

    private suspend fun checkLocalUser() {
        val localUser = withContext(Dispatchers.IO) {
            db.userDao().getFirstUser() // GET ANY EXISTING USER
        }

        if (localUser != null && localUser.login) {
            redirectToHome(localUser)
        } else {
            redirectToAuth()
        }
    }

    private suspend fun createNewFirebaseUser(firebaseUser: com.google.firebase.auth.FirebaseUser) {
        val newUser = UserEntity(
            id = firebaseUser.uid,
            name = firebaseUser.displayName ?: "User",
            last_name = "",
            username = firebaseUser.email?.substringBefore("@") ?: "user",
            email = firebaseUser.email ?: "",
            password = "",
            login = true
        )

        try {
            // SAVE TO FIRESTORE
            firestore.collection("users")
                .document(firebaseUser.uid)
                .set(newUser)
                .await()

            // SAVE TO LOCAL DB
            withContext(Dispatchers.IO) {
                db.userDao().deleteAllUsers()
                db.userDao().insertUser(newUser)
            }

            redirectToHome(newUser)

        } catch (e: Exception) {
            redirectToAuth()
        }
    }

    private suspend fun getFirestoreUser(userId: String): UserEntity? {
        return try {
            if (userId.isBlank()) return null
            val document = firestore.collection("users").document(userId).get().await()
            if (document.exists()) document.toObject(UserEntity::class.java) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun redirectToHome(user: UserEntity) {
        binding.root.postDelayed({
            val intent = Intent(this@SplashScreenActivity, HomeActivity::class.java).apply {
                putExtra("USER_ID", user.id)
                putExtra("USER_NAME", user.name)
                putExtra("USER_LAST_NAME", user.last_name)
                putExtra("USER_USERNAME", user.username)
                putExtra("USER_EMAIL", user.email)
                putExtra("USER_LOGIN", user.login)
            }
            navigateTo(intent, R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }, SPLASH_DELAY)
    }

    private fun redirectToAuth() {
        binding.root.postDelayed({
            val intent = Intent(this@SplashScreenActivity, AuthActivity::class.java)
            navigateTo(intent, R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }, SPLASH_DELAY)
    }
}
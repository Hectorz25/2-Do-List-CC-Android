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
            println("DEBUG: FATAL ERROR in onCreate: ${e.message}")
            e.printStackTrace()
            redirectToAuth()
        }
    }

    private fun checkUserSession() {
        lifecycleScope.launch {
            try {
                // CHECK USER IN FIREBASE AUTH
                val currentFirebaseUser = auth.currentUser

                if (currentFirebaseUser != null) {
                    // USER IN FIREBASE AUTH - CHECK IF EXISTS IN FIRESTORE
                    val firestoreUser = getFirestoreUser(currentFirebaseUser.uid)

                    if (firestoreUser != null) {
                        if (firestoreUser.login) {
                            // USER EXISTS AND IS LOGGED IN
                            migrateFromLocalToFirebaseUser(currentFirebaseUser.uid, firestoreUser)
                            redirectToHome(firestoreUser)
                        } else {
                            // USER EXISTS BUT LOGIN = FALSE
                            redirectToAuth()
                        }
                    } else {
                        // USER IN FIREBASE AUTH BUT NOT IN FIRESTORE - CREATE IT
                        createNewFirebaseUser(currentFirebaseUser)
                    }
                    return@launch
                }

                // NO USER IN FIREBASE AUTH, CHECK LOCALLY
                checkLocalUser()

            } catch (e: Exception) {
                // IF ANY ERROR, REDIRECT TO AUTH
                println("DEBUG: Exception occurred: ${e.message}")
                redirectToAuth()
            }
        }
    }

    private suspend fun checkLocalUser() {
        val localUser = withContext(Dispatchers.IO) {
            db.userDao().getUserById(persistentDeviceId)
        }

        if (localUser != null) {
            if (localUser.login) {
                redirectToHome(localUser)
            } else {
                redirectToAuth()
            }
        } else {
            // NO USER ANYWHERE
            redirectToAuth()
        }
    }

    private suspend fun migrateFromLocalToFirebaseUser(firebaseUid: String, firebaseUser: UserEntity) {
        withContext(Dispatchers.IO) {
            // DELETE OLD LOCAL USER
            val localUser = db.userDao().getUserById(persistentDeviceId)
            localUser?.let {
                db.userDao().deleteUser(it)
            }
            db.userDao().insertUser(firebaseUser)
            preferenceHelper.saveString("device_id", firebaseUid)
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

        // SAVE IN FIRESTORE
        try {
            firestore.collection("users")
                .document(firebaseUser.uid)
                .set(newUser)
                .await()

            // MIGRATE FROM LOCAL
            migrateFromLocalToFirebaseUser(firebaseUser.uid, newUser)
            redirectToHome(newUser)

        } catch (e: Exception) {
            redirectToAuth()
        }
    }

    private suspend fun getFirestoreUser(userId: String): UserEntity? {
        return try {
            if (userId.isBlank()) {
                return null
            }

            val document = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            if (document.exists()) {
                document.toObject(UserEntity::class.java)
            } else {
                null
            }
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
            val options = ActivityOptions.makeCustomAnimation(
                this,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
            finish()
        }, SPLASH_DELAY)
    }

    private fun redirectToAuth() {
        binding.root.postDelayed({
            val intent = Intent(this@SplashScreenActivity, AuthActivity::class.java)
            val options = ActivityOptions.makeCustomAnimation(
                this,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
            finish()
        }, SPLASH_DELAY)
    }
}
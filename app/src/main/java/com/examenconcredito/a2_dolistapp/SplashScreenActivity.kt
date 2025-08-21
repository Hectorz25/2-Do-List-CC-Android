package com.examenconcredito.a2_dolistapp

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SplashScreenActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { Firebase.auth }
    private val firestore: FirebaseFirestore by lazy { Firebase.firestore }
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val preferenceHelper by lazy { PreferenceHelper(this) }
    private val persistentDeviceId by lazy { preferenceHelper.getUniqueDeviceId() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splashscreen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        checkUserSession()
    }

    private fun checkUserSession() {
        lifecycleScope.launch {
            try {
                //First try w/Firebase using UUID
                val firebaseUser = checkFirebaseUser()
                if (firebaseUser != null) {
                    //User found on Firebase
                    if (firebaseUser.login) {
                        redirectToHome(firebaseUser)
                    } else {
                        redirectToAuth()
                    }
                    return@launch
                }

                //If not in Firebase, verify local with UUID
                val localUser = withContext(Dispatchers.IO) {
                    db.userDao().getUserById(persistentDeviceId)
                }

                if (localUser != null) {
                    //User found locally
                    if (localUser.login) {
                        redirectToHome(localUser)
                    } else {
                        redirectToAuth()
                    }
                } else {
                    //Not found anywhere
                    redirectToAuth()
                }

            } catch (e: Exception) {
                //Firebase connection error, verify locally
                checkLocalUser()
            }
        }
    }

    private suspend fun checkFirebaseUser(): UserEntity? {
        return try {
            // Search user in Firestore w/UUID
            val document = firestore.collection("users")
                .document(persistentDeviceId)
                .get()
                .await()

            if (document.exists()) {
                val user = document.toObject(UserEntity::class.java)
                user?.let {
                    //Save/Update to Local Database
                    withContext(Dispatchers.IO) {
                        db.userDao().insertUser(it)
                    }
                }
                user
            } else {
                null
            }
        } catch (e: Exception) {
            null //Connection error
        }
    }

    private fun checkLocalUser() {
        lifecycleScope.launch(Dispatchers.IO) {
            val localUser = db.userDao().getUserById(persistentDeviceId)
            withContext(Dispatchers.Main) {
                if (localUser != null) {
                    if (localUser.login) {
                        redirectToHome(localUser)
                    } else {
                        redirectToAuth()
                    }
                } else {
                    redirectToAuth()
                }
            }
        }
    }

    private fun redirectToHome(user: UserEntity) {
        val intent = Intent(this@SplashScreenActivity, HomeActivity::class.java).apply {
            putExtra("USER_ID", user.id)
            putExtra("USER_NAME", user.name)
            putExtra("USER_LAST_NAME", user.last_name)
            putExtra("USER_USERNAME", user.username)
            putExtra("USER_EMAIL", user.email)
            putExtra("USER_LOGIN", user.login)
        }
        startActivity(intent)
        finish()
    }

    private fun redirectToAuth() {
        val intent = Intent(this@SplashScreenActivity, AuthActivity::class.java)
        startActivity(intent)
        finish()
    }
}
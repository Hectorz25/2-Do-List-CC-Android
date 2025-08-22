package com.examenconcredito.a2_dolistapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.examenconcredito.a2_dolistapp.databinding.ActivityAuthBinding
import com.examenconcredito.a2_dolistapp.data.utils.PreferenceHelper
import com.examenconcredito.a2_dolistapp.data.database.AppDatabase
import com.google.android.gms.common.api.ApiException
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {
    private val preferenceHelper by lazy { PreferenceHelper(this) }
    private val persistentDeviceId by lazy { preferenceHelper.getUniqueDeviceId() }
    private val db by lazy { AppDatabase.getDatabase(this) }
    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: com.google.firebase.firestore.FirebaseFirestore

    // VARIABLE TO PREVENT SWITCH RECURSION
    private var isThemeChangeInProgress = false

    // RESULT FROM GOOGLE
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            handleGoogleSignInResult(data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // APPLY SAVED THEME BEFORE SUPER
        preferenceHelper.applySavedTheme()
        super.onCreate(savedInstanceState)

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        // INIT FIREBASE
        auth = Firebase.auth
        firestore = Firebase.firestore

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.root.post {
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
                // SAVE THEME PREFERENCE
                preferenceHelper.setDarkModeEnabled(isSelected)

                // RESET FLAG AFTER DELAY
                swDarkMode.postDelayed({
                    isThemeChangeInProgress = false
                }, 1000)
            }

            setupGoogleSignInButton()
        }
    }

    private fun setupGoogleSignInButton() {
        binding.btnSignInGoogle.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient =
            com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleGoogleSignInResult(data: Intent?) {
        try {
            val task =
                com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(
                    data
                )
            val account = task.getResult(ApiException::class.java)
            account?.let {
                firebaseAuthWithGoogle(it)
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google sign-in error: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        saveUserToFirestoreAndLocal(it, account)
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun saveUserToFirestoreAndLocal(
        firebaseUser: com.google.firebase.auth.FirebaseUser,
        googleAccount: com.google.android.gms.auth.api.signin.GoogleSignInAccount
    ) {
        val userEntity = com.examenconcredito.a2_dolistapp.data.entities.UserEntity(
            id = firebaseUser.uid,
            name = googleAccount.givenName ?: "",
            last_name = googleAccount.familyName ?: "",
            username = googleAccount.email?.substringBefore("@") ?: "user",
            email = googleAccount.email ?: "",
            password = "",
            login = true
        )

        // SAVE USER DATA TO INTENT FOR IMMEDIATE REDIRECTION
        val intent = Intent(this, HomeActivity::class.java).apply {
            putExtra("USER_ID", userEntity.id)
            putExtra("USER_NAME", userEntity.name)
            putExtra("USER_LAST_NAME", userEntity.last_name)
            putExtra("USER_USERNAME", userEntity.username)
            putExtra("USER_EMAIL", userEntity.email)
            putExtra("USER_LOGIN", userEntity.login)
        }

        // SAVE TO FIRESTORE IN BACKGROUND BUT REDIRECT IMMEDIATELY
        firestore.collection("users")
            .document(firebaseUser.uid)
            .set(userEntity)
            .addOnSuccessListener {
                // SAVE FIREBASE UID FOR FUTURE SESSIONS
                preferenceHelper.saveFirebaseUid(firebaseUser.uid)

                // SAVE LOCALLY IN BACKGROUND
                lifecycleScope.launch(Dispatchers.IO) {
                    // DELETE OLD LOCAL USER IF EXISTS
                    val localUser = db.userDao().getUserById(persistentDeviceId)
                    localUser?.let {
                        db.userDao().deleteUser(it)
                    }
                    // SAVE NEW USER
                    db.userDao().insertUser(userEntity)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Firestore save error: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }

        // REDIRECT IMMEDIATELY WITHOUT WAITING FOR FIRESTORE
        startActivity(intent)
        finish()
    }

    private fun enableDarkMode() {
        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES)
        delegate.applyDayNight()
    }

    private fun disableDarkMode() {
        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO)
        delegate.applyDayNight()
    }

    private fun animateThemeChange() {
        binding.root.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                delegate.applyDayNight()
                binding.root.alpha = 0f
                binding.root.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }.start()
    }
}
package com.examenconcredito.a2_dolistapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.examenconcredito.a2_dolistapp.data.database.AppDatabase
import com.examenconcredito.a2_dolistapp.data.entities.UserEntity
import com.examenconcredito.a2_dolistapp.data.utils.PreferenceHelper
import com.examenconcredito.a2_dolistapp.databinding.ActivityAuthBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import android.widget.Toast
import com.examenconcredito.a2_dolistapp.ui.fragments.RegisterBottomSheetFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthActivity : AppCompatActivity() {
    private val preferenceHelper by lazy { PreferenceHelper(this) }
    private val persistentDeviceId by lazy { preferenceHelper.getUniqueDeviceId() }
    private val db by lazy { AppDatabase.getDatabase(this) }
    private lateinit var binding: ActivityAuthBinding
    private val auth by lazy { Firebase.auth }
    private val firestore by lazy { Firebase.firestore }

    // VARIABLE TO PREVENT SWITCH RECURSION
    private var isThemeChangeInProgress = false
    private var isInRegisterFragment = false

    // RESULT FROM GOOGLE
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            handleGoogleSignInResult(data)
        }
    }

    // BACK PRESS
    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (isInRegisterFragment) {
                hideRegisterFragment()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // APPLY SAVED THEME BEFORE SUPER
        preferenceHelper.applySavedTheme()
        super.onCreate(savedInstanceState)

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        // SETUP BACK PRESS CALLBACK
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

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
            setupRegisterButton()
            setupGuestSignInButton()
            setupEmailSignInButton() // SETUP EMAIL SIGN IN BUTTON
        }
    }

    private fun setupGoogleSignInButton() {
        binding.btnSignInGoogle.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun setupRegisterButton() {
        binding.tvRegisterHere.setOnClickListener {
            showRegisterBottomSheet()
        }
    }

    private fun setupGuestSignInButton() {
        binding.btnSignInGuest.setOnClickListener {
            signInAsGuest()
        }
    }

    private fun setupEmailSignInButton() {
        binding.btnSignInEmail.setOnClickListener {
            signInWithEmail()
        }
    }

    private fun signInWithEmail() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "PLEASE ENTER EMAIL AND PASSWORD", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.isVisible = true
        binding.btnSignInEmail.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                binding.progressBar.isVisible = false
                binding.btnSignInEmail.isEnabled = true

                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        // CHECK IF USER EXISTS IN FIRESTORE
                        checkFirestoreUserAndRedirect(it.uid)
                    }
                } else {
                    Toast.makeText(
                        this,
                        "SIGN IN FAILED: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun checkFirestoreUserAndRedirect(userId: String) {
        lifecycleScope.launch {
            try {
                val firestoreUser = getFirestoreUser(userId)
                if (firestoreUser != null) {
                    // SAVE USER TO LOCAL DB AND REDIRECT
                    withContext(Dispatchers.IO) {
                        db.userDao().deleteAllUsers()
                        db.userDao().insertUser(firestoreUser.copy(login = true))
                    }
                    redirectToHome(firestoreUser)
                } else {
                    Toast.makeText(
                        this@AuthActivity,
                        "USER NOT FOUND IN DATABASE",
                        Toast.LENGTH_SHORT
                    ).show()
                    auth.signOut()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@AuthActivity,
                    "ERROR: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                auth.signOut()
            }
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

    private fun signInAsGuest() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // DELETE ALL EXISTING USERS
                db.userDao().deleteAllUsers()

                // CREATE NEW GUEST USER WITH CORRECT DEVICE ID
                val guestUser = UserEntity(
                    id = persistentDeviceId,
                    name = "Invitado",
                    last_name = "",
                    username = "Invitado_${persistentDeviceId.takeLast(4)}",
                    email = "invitado_${persistentDeviceId.takeLast(4)}@invitado.com",
                    password = "",
                    login = true
                )

                // SAVE USER
                db.userDao().insertUser(guestUser)

                // CLEAR ANY PREVIOUS FIREBASE UID
                preferenceHelper.clearUserData()

                withContext(Dispatchers.Main) {
                    redirectToHome(guestUser)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AuthActivity,
                        "ERROR SIGNING IN AS GUEST: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun redirectToHome(user: UserEntity) {
        val intent = Intent(this, HomeActivity::class.java).apply {
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

    private fun showRegisterBottomSheet() {
        val bottomSheet = RegisterBottomSheetFragment.newInstance()
        bottomSheet.show(supportFragmentManager, RegisterBottomSheetFragment.TAG)
    }

    private fun hideRegisterFragment() {
        supportFragmentManager.popBackStack()
        // SHOW MAIN AUTH VIEWS AND HIDE FRAGMENT CONTAINER
        binding.btnSignInGoogle.isVisible = true
        binding.tvRegisterHere.isVisible = true
        binding.authContainer.isVisible = false
        isInRegisterFragment = false
        onBackPressedCallback.isEnabled = false
    }

    private fun signInWithGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleGoogleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            account?.let {
                firebaseAuthWithGoogle(it)
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "GOOGLE SIGN-IN ERROR: ${e.statusCode}", Toast.LENGTH_SHORT).show()
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
                        "AUTHENTICATION FAILED: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun saveUserToFirestoreAndLocal(
        firebaseUser: com.google.firebase.auth.FirebaseUser,
        googleAccount: com.google.android.gms.auth.api.signin.GoogleSignInAccount
    ) {
        val userEntity = UserEntity(
            id = firebaseUser.uid,
            name = googleAccount.givenName ?: "",
            last_name = googleAccount.familyName ?: "",
            username = googleAccount.email?.substringBefore("@") ?: "user",
            email = googleAccount.email ?: "",
            password = "",
            login = true
        )

        firestore.collection("users")
            .document(firebaseUser.uid)
            .set(userEntity)
            .addOnSuccessListener {
                preferenceHelper.saveFirebaseUid(firebaseUser.uid)

                lifecycleScope.launch(Dispatchers.IO) {
                    // DELETE ALL EXISTING USERS BEFORE INSERTING FIREBASE USER
                    db.userDao().deleteAllUsers()
                    db.userDao().insertUser(userEntity)
                }

                redirectToHome(userEntity)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "FIRESTORE SAVE ERROR: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun enableDarkMode() {
        animateThemeChange(AppCompatDelegate.MODE_NIGHT_YES)
    }

    private fun disableDarkMode() {
        animateThemeChange(AppCompatDelegate.MODE_NIGHT_NO)
    }

    private fun animateThemeChange(mode: Int) {
        binding.root.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                AppCompatDelegate.setDefaultNightMode(mode)
                delegate.applyDayNight()
                binding.root.alpha = 0f
                binding.root.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }.start()
    }
}
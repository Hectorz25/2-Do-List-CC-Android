package com.examenconcredito.a2_dolistapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
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
import com.examenconcredito.a2_dolistapp.ui.fragments.RegisterBottomSheetFragment
import com.examenconcredito.a2_dolistapp.utils.ActivityExtensions.navigateTo
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
    private val handler = Handler(Looper.getMainLooper())

    private var isThemeChangeInProgress = false
    private var isInRegisterFragment = false

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            handleGoogleSignInResult(data)
        }
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (isInRegisterFragment) {
                hideRegisterFragment()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        preferenceHelper.applySavedTheme()
        super.onCreate(savedInstanceState)

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.root.post {
            val swDarkMode = findViewById<SwitchMaterial>(R.id.swThemeSelector)
            swDarkMode.isChecked = preferenceHelper.isDarkModeEnabled()

            swDarkMode.setOnCheckedChangeListener { _, isSelected ->
                if (isThemeChangeInProgress) return@setOnCheckedChangeListener
                isThemeChangeInProgress = true
                if (isSelected) enableDarkMode() else disableDarkMode()
                preferenceHelper.setDarkModeEnabled(isSelected)
                swDarkMode.postDelayed({ isThemeChangeInProgress = false }, 1000)
            }

            setupGoogleSignInButton()
            setupRegisterButton()
            setupGuestSignInButton()
            setupEmailSignInButton()
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
            Toast.makeText(this, getString(R.string.text_fill_auth_fields), Toast.LENGTH_SHORT).show()
            return
        }

        showLoading()
        binding.btnSignInEmail.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                binding.btnSignInEmail.isEnabled = true
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let { checkFirestoreUserAndRedirect(it.uid) }
                } else {
                    hideLoading()
                    Toast.makeText(this, getString(R.string.text_error_email_signin, task.exception?.message?: ""), Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkFirestoreUserAndRedirect(userId: String) {
        lifecycleScope.launch {
            try {
                val firestoreUser = getFirestoreUser(userId)
                if (firestoreUser != null) {
                    withContext(Dispatchers.IO) {
                        db.userDao().deleteAllUsers()
                        db.userDao().insertUser(firestoreUser.copy(login = true))
                    }
                    delayedRedirectToHome(firestoreUser)
                } else {
                    hideLoading()
                    Toast.makeText(this@AuthActivity, "No se encontro usuario en la base de datos", Toast.LENGTH_SHORT).show()
                    auth.signOut()
                }
            } catch (e: Exception) {
                hideLoading()
                Toast.makeText(this@AuthActivity, getString(R.string.text_error, e.message?: ""), Toast.LENGTH_SHORT).show()
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
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                db.userDao().deleteAllUsers()
                val guestUser = UserEntity(
                    id = persistentDeviceId,
                    name = "Invitado",
                    last_name = "",
                    username = "Invitado_${persistentDeviceId.takeLast(4)}",
                    email = "invitado_${persistentDeviceId.takeLast(4)}@invitado.com",
                    password = "",
                    login = true
                )
                db.userDao().insertUser(guestUser)
                preferenceHelper.clearUserData()

                // SWITCH TO MAIN THREAD BEFORE CALLING UI METHODS
                withContext(Dispatchers.Main) {
                    delayedRedirectToHome(guestUser)
                }
            } catch (e: Exception) {
                // SWITCH TO MAIN THREAD FOR UI UPDATES
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(this@AuthActivity, getString(R.string.text_error_guest_signin, e.message?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun delayedRedirectToHome(user: UserEntity) {
        handler.postDelayed({
            hideLoading()
            redirectToHome(user)
        }, 3000)
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
        navigateTo(intent, R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    fun showLoading() {
        runOnUiThread {
            binding.loadingOverlay.visibility = View.VISIBLE
            binding.lottieLoadingAnimation.playAnimation()
            binding.progressBar.isVisible = false

            binding.btnSignInEmail.isEnabled = false
            binding.btnSignInGoogle.isEnabled = false
            binding.btnSignInGuest.isEnabled = false
            binding.tvRegisterHere.isEnabled = false
        }
    }

    private fun hideLoading() {
        runOnUiThread {
            binding.loadingOverlay.visibility = View.GONE
            binding.lottieLoadingAnimation.cancelAnimation()

            binding.btnSignInEmail.isEnabled = true
            binding.btnSignInGoogle.isEnabled = true
            binding.btnSignInGuest.isEnabled = true
            binding.tvRegisterHere.isEnabled = true
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun showRegisterBottomSheet() {
        val bottomSheet = RegisterBottomSheetFragment.newInstance()
        bottomSheet.show(supportFragmentManager, RegisterBottomSheetFragment.TAG)
    }

    private fun hideRegisterFragment() {
        supportFragmentManager.popBackStack()
        binding.btnSignInGoogle.isVisible = true
        binding.tvRegisterHere.isVisible = true
        binding.authContainer.isVisible = false
        isInRegisterFragment = false
        onBackPressedCallback.isEnabled = false
    }

    private fun signInWithGoogle() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            val signInIntent = googleSignInClient.signInIntent
            val intent = Intent(signInIntent)
            intent.putExtra("prompt", "select_account")
            googleSignInLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.text_error_config_google_signin, e.message?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleGoogleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            account?.let { firebaseAuthWithGoogle(it) }
        } catch (e: ApiException) {
            Toast.makeText(this, getString(R.string.text_error_google_signin, e.statusCode.toString()), Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        showLoading()
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let { saveUserToFirestoreAndLocal(it, account) }
                } else {
                    hideLoading()
                    Toast.makeText(this, getString(R.string.text_error_google_auth, task.exception?.message?: ""), Toast.LENGTH_SHORT).show()
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
                    db.userDao().deleteAllUsers()
                    db.userDao().insertUser(userEntity)

                    // SWITCH TO MAIN THREAD
                    withContext(Dispatchers.Main) {
                        delayedRedirectToHome(userEntity)
                    }
                }
            }
            .addOnFailureListener {
                // SWITCH TO MAIN THREAD
                runOnUiThread {
                    hideLoading()
                    Toast.makeText(this, getString(R.string.text_debug_firebase_saving, it.message?:""), Toast.LENGTH_SHORT).show()
                }
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
                binding.root.animate().alpha(1f).setDuration(150).start()
            }.start()
    }
}
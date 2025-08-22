package com.examenconcredito.a2_dolistapp.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.examenconcredito.a2_dolistapp.HomeActivity
import com.examenconcredito.a2_dolistapp.R
import com.examenconcredito.a2_dolistapp.data.database.AppDatabase
import com.examenconcredito.a2_dolistapp.data.entities.UserEntity
import com.examenconcredito.a2_dolistapp.data.utils.PreferenceHelper
import com.examenconcredito.a2_dolistapp.databinding.FragmentRegisterBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentRegisterBottomSheetBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: com.google.firebase.firestore.FirebaseFirestore
    private lateinit var db: AppDatabase
    private lateinit var preferenceHelper: PreferenceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // APPLY CUSTOM STYLE FOR BOTTOM SHEET
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = Firebase.auth
        firestore = Firebase.firestore
        db = AppDatabase.getDatabase(requireContext())
        preferenceHelper = PreferenceHelper(requireContext())

        setupClickListeners()
    }
    private fun setupBottomSheetBehavior() {
        // CONFIGURAR EL COMPORTAMIENTO DEL BOTTOM SHEET
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)

            // PERMITIR QUE EL TECLADO EMPUJE EL MODAL
            behavior.isFitToContents = false
            behavior.isHideable = false
            behavior.skipCollapsed = true

            // EXPANDIR COMPLETAMENTE AL INICIO
            behavior.state = BottomSheetBehavior.STATE_EXPANDED

            // AGREGAR LISTENER PARA MANEJAR EL TECLADO
            behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    // MANTENER EXPANDIDO SIEMPRE
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // NO SE NECESITA IMPLEMENTAR
                }
            })
        }
    }
    override fun onStart() {
        super.onStart()
        // ASEGURARSE DE QUE ESTÉ EXPANDIDO AL INICIAR
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
    private fun setupClickListeners() {
        binding.btnRegister.setOnClickListener {
            registerUser()
        }

        binding.tvLogin.setOnClickListener {
            // CLOSE THE BOTTOM SHEET AND GO BACK TO LOGIN
            dismiss()
        }
    }

    private fun registerUser() {
        val name = binding.etName.text.toString().trim()
        val lastName = binding.etLastName.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (validateInputs(name, lastName, username, email, password)) {
            createUserWithEmail(email, password, name, lastName, username)
        }
    }

    private fun validateInputs(
        name: String,
        lastName: String,
        username: String,
        email: String,
        password: String
    ): Boolean {
        if (name.isEmpty()) {
            binding.etName.error = "El nombre es requerido"
            return false
        }
        if (lastName.isEmpty()) {
            binding.etLastName.error = "El apellido es requerido"
            return false
        }
        if (username.isEmpty()) {
            binding.etUsername.error = "El nombre de usuario es requerido"
            return false
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Correo electrónico inválido"
            return false
        }
        if (password.length < 6) {
            binding.etPassword.error = "La contraseña debe tener al menos 6 caracteres"
            return false
        }
        return true
    }

    private fun createUserWithEmail(
        email: String,
        password: String,
        name: String,
        lastName: String,
        username: String
    ) {
        binding.btnRegister.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    firebaseUser?.let {
                        saveUserToFirestoreAndLocal(it, name, lastName, username, email)
                    }
                } else {
                    binding.btnRegister.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        "Error al registrar: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun saveUserToFirestoreAndLocal(
        firebaseUser: com.google.firebase.auth.FirebaseUser,
        name: String,
        lastName: String,
        username: String,
        email: String
    ) {
        val userEntity = UserEntity(
            id = firebaseUser.uid,
            name = name,
            last_name = lastName,
            username = username,
            email = email,
            password = "",
            login = true
        )

        // SAVE TO FIRESTORE
        firestore.collection("users")
            .document(firebaseUser.uid)
            .set(userEntity)
            .addOnSuccessListener {
                // SAVE TO LOCAL AND REDIRECT
                saveUserToLocalAndRedirect(userEntity)
            }
            .addOnFailureListener { e ->
                binding.btnRegister.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    "Error al guardar datos: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun saveUserToLocalAndRedirect(userEntity: UserEntity) {
        CoroutineScope(Dispatchers.IO).launch {
            // SAVE TO LOCAL DATABASE
            db.userDao().insertUser(userEntity)

            // SAVE FIREBASE UID FOR FUTURE SESSIONS
            preferenceHelper.saveFirebaseUid(userEntity.id)

            withContext(Dispatchers.Main) {
                // REDIRECT TO HOME
                val intent = Intent(requireContext(), HomeActivity::class.java).apply {
                    putExtra("USER_ID", userEntity.id)
                    putExtra("USER_NAME", userEntity.name)
                    putExtra("USER_LAST_NAME", userEntity.last_name)
                    putExtra("USER_USERNAME", userEntity.username)
                    putExtra("USER_EMAIL", userEntity.email)
                    putExtra("USER_LOGIN", userEntity.login)
                }
                startActivity(intent)
                requireActivity().finish()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "RegisterBottomSheet"

        fun newInstance(): RegisterBottomSheetFragment {
            return RegisterBottomSheetFragment()
        }
    }
}
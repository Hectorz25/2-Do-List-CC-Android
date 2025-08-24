package com.examenconcredito.a2_dolistapp

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.examenconcredito.a2_dolistapp.data.database.AppDatabase
import com.examenconcredito.a2_dolistapp.data.entities.TaskListEntity
import com.examenconcredito.a2_dolistapp.data.utils.PreferenceHelper
import com.examenconcredito.a2_dolistapp.databinding.ActivityHomeBinding
import com.examenconcredito.a2_dolistapp.ui.adapters.TaskListAdapter
import com.examenconcredito.a2_dolistapp.ui.fragments.CreateListBottomSheetFragment
import com.examenconcredito.a2_dolistapp.ui.fragments.EditListBottomSheetFragment
import com.examenconcredito.a2_dolistapp.utils.ActivityExtensions.navigateTo
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity(),
    CreateListBottomSheetFragment.OnListCreatedListener,
    EditListBottomSheetFragment.OnListUpdatedListener {

    private lateinit var binding: ActivityHomeBinding
    private val preferenceHelper by lazy { PreferenceHelper(this) }
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val auth by lazy { Firebase.auth }
    private val firestore by lazy { Firebase.firestore }

    private var userName: String = ""
    private var userEmail: String = ""
    private var userId: String = ""
    private var isGuestUser: Boolean = false // NEW: TRACK GUEST STATUS

    private lateinit var taskListAdapter: TaskListAdapter
    private val allTaskLists = mutableListOf<TaskListEntity>() // NEW: STORE ALL LISTS
    private val filteredTaskLists = mutableListOf<TaskListEntity>() // NEW: STORE FILTERED LISTS

    // NEW: ENUM FOR FILTER TYPES
    private enum class FilterType {
        ALL, COMPLETED, PENDING
    }

    private var currentFilter = FilterType.ALL // NEW: CURRENT FILTER STATE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        userId = intent.getStringExtra("USER_ID") ?: ""
        userName = intent.getStringExtra("USER_NAME") ?: ""
        userEmail = intent.getStringExtra("USER_EMAIL") ?: ""

        // NEW: CHECK IF USER IS GUEST
        isGuestUser = userId.contains("invitado", ignoreCase = true) || userEmail.isEmpty()
        val welcomeName = if (isGuestUser) userName else userEmail
        binding.tvWelcomeMessage.text = getString(R.string.text_no_list_to_show, welcomeName)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupFilterSpinner() // NEW: SETUP FILTER SPINNER
        setupRecyclerView()
        setupFloatingActionButton()
    }

    override fun onResume() {
        super.onResume()
        loadTaskLists()
    }

    // NEW: SETUP FILTER SPINNER
    private fun setupFilterSpinner() {
        // CREATE FILTER OPTIONS
        val filterOptions = arrayOf(
            getString(R.string.filter_all_lists),
            getString(R.string.filter_completed),
            getString(R.string.filter_pending)
        )

        // SETUP SPINNER ADAPTER
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            filterOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.spinnerFilter.adapter = adapter

        // SETUP FILTER SELECTION LISTENER
        binding.spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> currentFilter = FilterType.ALL
                    1 -> currentFilter = FilterType.COMPLETED
                    2 -> currentFilter = FilterType.PENDING
                }
                applyFilter() // APPLY SELECTED FILTER
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                currentFilter = FilterType.ALL
                applyFilter()
            }
        }
    }

    private fun setupRecyclerView() {
        taskListAdapter = TaskListAdapter(
            filteredTaskLists, // CHANGED: USE FILTERED LISTS INSTEAD OF ALL LISTS
            onItemClick = { editTaskList(it) },
            onDeleteClick = { deleteTaskList(it) },
            db = db
        )

        binding.recyclerViewTaskLists.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = taskListAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupFloatingActionButton() {
        val fab: FloatingActionButton = binding.fabAddTask
        fab.setOnClickListener {
            showCreateListBottomSheet()
        }
    }

    private fun loadTaskLists() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val lists = db.taskListDao().getTaskListsByUser(userId)
                withContext(Dispatchers.Main) {
                    allTaskLists.clear()
                    allTaskLists.addAll(lists)
                    applyFilter()

                    if (lists.isNotEmpty()) {
                        binding.filterContainer.visibility = View.VISIBLE
                    } else {
                        binding.filterContainer.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeActivity, getString(R.string.text_loading_error), Toast.LENGTH_SHORT).show()
                    binding.filterContainer.visibility = View.GONE
                }
            }
        }
    }

    private fun applyFilter() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filteredLists = when (currentFilter) {
                    FilterType.ALL -> db.taskListDao().getTaskListsByUser(userId)
                    FilterType.COMPLETED -> db.taskListDao().getCompletedTaskLists(userId)
                    FilterType.PENDING -> db.taskListDao().getPendingTaskLists(userId)
                }

                withContext(Dispatchers.Main) {
                    filteredTaskLists.clear()
                    filteredTaskLists.addAll(filteredLists)
                    taskListAdapter.notifyDataSetChanged()
                    updateUI()

                    val shouldShowFilter = allTaskLists.isNotEmpty()
                    binding.filterContainer.visibility = if (shouldShowFilter) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeActivity, "Error applying filter", Toast.LENGTH_SHORT).show()
                    binding.filterContainer.visibility = View.GONE
                }
            }
        }
    }

    private fun updateUI() {
        if (filteredTaskLists.isNotEmpty()) {
            binding.cardWelcome.visibility = View.GONE
            binding.recyclerViewTaskLists.visibility = View.VISIBLE
            binding.filterContainer.visibility = View.VISIBLE
        } else {
            binding.recyclerViewTaskLists.visibility = View.GONE
            binding.filterContainer.visibility = View.GONE
            binding.cardWelcome.visibility = View.VISIBLE

            val message = when (currentFilter) {
                FilterType.ALL -> getString(R.string.text_no_list_to_show, userName)
                FilterType.COMPLETED -> getString(R.string.text_no_completed_lists)
                FilterType.PENDING -> getString(R.string.text_no_pending_lists)
            }
            binding.tvWelcomeMessage.text = message
        }
    }

    private fun editTaskList(taskList: TaskListEntity) {
        val bottomSheet = EditListBottomSheetFragment.newInstance(taskList.id)
        bottomSheet.show(supportFragmentManager, EditListBottomSheetFragment.TAG)
    }

    private fun deleteTaskList(taskList: TaskListEntity) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.text_delete_list_title))
            .setMessage(getString(R.string.text_delete_list_message, taskList.title))
            .setPositiveButton(getString(R.string.btn_text_delete)) { dialog, which ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        db.taskDao().deleteAllTasksByList(taskList.id)
                        db.taskListDao().deleteTaskList(taskList)

                        if (!isGuestUser && auth.currentUser != null) { // CHANGED: USE isGuestUser FLAG
                            deleteFromFirebase(taskList)
                        }

                        withContext(Dispatchers.Main) {
                            loadTaskLists() // RELOAD LISTS AFTER DELETION
                            Toast.makeText(this@HomeActivity, getString(R.string.text_list_deleted), Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@HomeActivity, getString(R.string.text_delete_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.btn_text_cancel), null)
            .show()
    }

    private suspend fun deleteFromFirebase(taskList: TaskListEntity) {
        try {
            val tasksQuery = firestore.collection("tasks")
                .whereEqualTo("listId", taskList.id)
                .get()
                .await()

            for (document in tasksQuery.documents) {
                document.reference.delete().await()
            }

            firestore.collection("task_lists")
                .document(taskList.id)
                .delete()
                .await()
        } catch (e: Exception) {
            // SILENT FAIL FOR FIREBASE ERRORS
        }
    }

    private fun showCreateListBottomSheet() {
        val bottomSheet = CreateListBottomSheetFragment.newInstance(userId)
        bottomSheet.show(supportFragmentManager, CreateListBottomSheetFragment.TAG)
    }

    override fun onListCreated() {
        loadTaskLists()
    }

    override fun onListUpdated() {
        loadTaskLists()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_user -> {
                showUserOptionsPopup()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

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
            // IGNORE ERROR
        }

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.popup_profile -> {
                    navigateToProfile()
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
                if (isGuestUser) { // CHANGED: USE isGuestUser FLAG
                    db.userDao().updateLoginStatus(userId, false)
                } else {
                    auth.signOut()
                    preferenceHelper.clearUserData()
                    db.userDao().deleteAllUsers()
                }

                withContext(Dispatchers.Main) {
                    val intent = Intent(this@HomeActivity, AuthActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    navigateTo(intent, R.anim.slide_in_left, R.anim.slide_out_right)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeActivity, getString(R.string.text_logout_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToProfile() {
        val intent = Intent(this, ProfileActivity::class.java).apply {
            putExtra("USER_ID", this@HomeActivity.intent.getStringExtra("USER_ID"))
            putExtra("USER_NAME", this@HomeActivity.intent.getStringExtra("USER_NAME"))
            putExtra("USER_LAST_NAME", this@HomeActivity.intent.getStringExtra("USER_LAST_NAME"))
            putExtra("USER_USERNAME", this@HomeActivity.intent.getStringExtra("USER_USERNAME"))
            putExtra("USER_EMAIL", this@HomeActivity.intent.getStringExtra("USER_EMAIL"))
            putExtra("USER_LOGIN", this@HomeActivity.intent.getBooleanExtra("USER_LOGIN", false))
        }
        startActivity(intent)
    }
}
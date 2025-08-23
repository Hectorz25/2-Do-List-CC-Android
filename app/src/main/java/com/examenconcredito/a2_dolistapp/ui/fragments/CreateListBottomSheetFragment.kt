package com.examenconcredito.a2_dolistapp.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.examenconcredito.a2_dolistapp.R
import com.examenconcredito.a2_dolistapp.data.database.AppDatabase
import com.examenconcredito.a2_dolistapp.data.entities.TaskEntity
import com.examenconcredito.a2_dolistapp.data.entities.TaskListEntity
import com.examenconcredito.a2_dolistapp.data.utils.PreferenceHelper
import com.examenconcredito.a2_dolistapp.databinding.FragmentCreateListBottomSheetBinding
import com.examenconcredito.a2_dolistapp.databinding.ItemTaskInputBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CreateListBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCreateListBottomSheetBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private val auth by lazy { Firebase.auth }
    private val firestore by lazy { Firebase.firestore }
    private val preferenceHelper by lazy { PreferenceHelper(requireContext()) }

    private val tasks = mutableListOf<TaskInput>()
    private var userId: String = ""

    private data class TaskInput(val binding: ItemTaskInputBinding)

    companion object {
        const val TAG = "CreateListBottomSheetFragment"
        fun newInstance(userId: String): CreateListBottomSheetFragment {
            return CreateListBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString("USER_ID", userId)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateListBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userId = arguments?.getString("USER_ID") ?: preferenceHelper.getUniqueDeviceId()
        setupListeners()
        addFirstTaskInput()
    }

    private fun setupListeners() {
        binding.btnAddTask.setOnClickListener {
            addTaskInput()
        }

        binding.btnCreate.setOnClickListener {
            createListWithTasks()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun addFirstTaskInput() {
        addTaskInput()
    }

    private fun addTaskInput() {
        val taskBinding = ItemTaskInputBinding.inflate(layoutInflater, binding.containerTasks, false)
        taskBinding.btnRemoveTask.setOnClickListener {
            removeTaskInput(taskBinding)
        }
        binding.containerTasks.addView(taskBinding.root)
        tasks.add(TaskInput(taskBinding))

        binding.scrollView.post {
            binding.scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun removeTaskInput(taskBinding: ItemTaskInputBinding) {
        binding.containerTasks.removeView(taskBinding.root)
        tasks.removeAll { it.binding == taskBinding }
    }

    private fun createListWithTasks() {
        val listTitle = binding.etListName.text.toString().trim()

        if (listTitle.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.text_enter_list_name), Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnCreate.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val taskList = TaskListEntity(userId = userId, title = listTitle)
                db.taskListDao().insertTaskList(taskList)

                val taskEntities = mutableListOf<TaskEntity>()
                tasks.forEach { taskInput ->
                    val taskText = taskInput.binding.etTask.text.toString().trim()
                    if (taskText.isNotEmpty()) {
                        taskEntities.add(TaskEntity(listId = taskList.id, text = taskText))
                    }
                }

                if (taskEntities.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        binding.btnCreate.isEnabled = true
                        Toast.makeText(requireContext(), getString(R.string.text_add_one_task), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                taskEntities.forEach { task ->
                    db.taskDao().insertTask(task)
                }

                if (!isGuestUser() && auth.currentUser != null) {
                    try {
                        syncWithFirebase(taskList, taskEntities)
                    } catch (e: Exception) {
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.btnCreate.isEnabled = true
                    Toast.makeText(requireContext(), getString(R.string.text_list_created), Toast.LENGTH_SHORT).show()
                    dismiss()
                    (activity as? OnListCreatedListener)?.onListCreated()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnCreate.isEnabled = true
                    Toast.makeText(requireContext(), getString(R.string.text_create_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun isGuestUser(): Boolean {
        return userId.contains("invitado") || auth.currentUser == null
    }

    private suspend fun syncWithFirebase(taskList: TaskListEntity, taskEntities: List<TaskEntity>) {
        try {
            val user = auth.currentUser ?: return

            val taskListData = hashMapOf(
                "id" to taskList.id,
                "userId" to user.uid,
                "title" to taskList.title,
                "createdAt" to System.currentTimeMillis()
            )

            firestore.collection("task_lists")
                .document(taskList.id)
                .set(taskListData)
                .await()

            taskEntities.forEach { task ->
                val taskData = hashMapOf(
                    "id" to task.id,
                    "listId" to taskList.id,
                    "text" to task.text,
                    "isCompleted" to task.isCompleted,
                    "createdAt" to System.currentTimeMillis()
                )

                firestore.collection("tasks")
                    .document(task.id)
                    .set(taskData)
                    .await()
            }
        } catch (e: Exception) {
            throw e
        }
    }

    interface OnListCreatedListener {
        fun onListCreated()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
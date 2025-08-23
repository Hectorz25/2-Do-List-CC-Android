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
import com.examenconcredito.a2_dolistapp.databinding.FragmentEditListBottomSheetBinding
import com.examenconcredito.a2_dolistapp.databinding.ItemTaskEditBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class EditListBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentEditListBottomSheetBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private val auth by lazy { Firebase.auth }
    private val firestore by lazy { Firebase.firestore }

    private lateinit var listId: String
    private val tasks = mutableListOf<TaskEditItem>()
    private val deletedTaskIds = mutableListOf<String>()

    private data class TaskEditItem(val binding: ItemTaskEditBinding, val task: TaskEntity)

    companion object {
        const val TAG = "EditListBottomSheetFragment"
        private const val ARG_LIST_ID = "list_id"

        fun newInstance(listId: String): EditListBottomSheetFragment {
            val fragment = EditListBottomSheetFragment()
            val args = Bundle()
            args.putString(ARG_LIST_ID, listId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditListBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listId = arguments?.getString(ARG_LIST_ID) ?: ""
        if (listId.isEmpty()) {
            dismiss()
            return
        }

        setupListeners()
        loadListAndTasks()
    }

    private fun setupListeners() {
        binding.btnAddTask.setOnClickListener {
            addNewTaskInput()
        }

        binding.btnSave.setOnClickListener {
            updateListAndTasks()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun loadListAndTasks() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val taskList = db.taskListDao().getTaskListById(listId)
                val taskListTasks = db.taskDao().getTasksByList(listId)

                withContext(Dispatchers.Main) {
                    taskList?.let {
                        binding.etListName.setText(it.title)
                        binding.etListName.setSelection(it.title.length)
                    }

                    binding.containerTasks.removeAllViews()
                    tasks.clear()

                    taskListTasks.forEach { task ->
                        addTaskInput(task)
                    }

                    if (taskListTasks.isEmpty()) {
                        addNewTaskInput()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.text_load_error), Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        }
    }

    private fun addTaskInput(task: TaskEntity) {
        val taskBinding = ItemTaskEditBinding.inflate(layoutInflater, binding.containerTasks, false)

        taskBinding.etTask.setText(task.text)
        taskBinding.cbCompleted.isChecked = task.isCompleted
        updateTaskTextAppearance(taskBinding, task.isCompleted)

        taskBinding.cbCompleted.setOnCheckedChangeListener { _, isChecked ->
            updateTaskTextAppearance(taskBinding, isChecked)
        }

        taskBinding.btnRemoveTask.setOnClickListener {
            if (task.id.isNotBlank()) {
                deletedTaskIds.add(task.id)
            }
            binding.containerTasks.removeView(taskBinding.root)
            tasks.removeAll { it.binding == taskBinding }
        }

        binding.containerTasks.addView(taskBinding.root)
        tasks.add(TaskEditItem(taskBinding, task))
    }

    private fun addNewTaskInput() {
        val newTask = TaskEntity(
            id = "",
            listId = listId,
            text = "",
            isCompleted = false
        )
        addTaskInput(newTask)
        binding.scrollView.post { binding.scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun updateTaskTextAppearance(binding: ItemTaskEditBinding, isCompleted: Boolean) {
        if (isCompleted) {
            binding.etTask.paintFlags = android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            binding.etTask.setTextColor(requireContext().getColor(android.R.color.darker_gray))
        } else {
            binding.etTask.paintFlags = 0
            binding.etTask.setTextColor(requireContext().getColor(android.R.color.black))
        }
    }

    private fun updateListAndTasks() {
        val newTitle = binding.etListName.text.toString().trim()

        if (newTitle.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.text_enter_list_name), Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSave.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val taskList = db.taskListDao().getTaskListById(listId)
                if (taskList == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), getString(R.string.text_list_not_found), Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                    return@launch
                }

                val updatedList = taskList.copy(title = newTitle)
                db.taskListDao().updateTaskList(updatedList)

                // DELETE REMOVED TASKS
                deletedTaskIds.forEach { taskId ->
                    val taskToDelete = db.taskDao().getTaskById(taskId)
                    taskToDelete?.let { db.taskDao().deleteTask(it) }
                }

                // UPDATE EXISTING TASKS AND ADD NEW ONES
                tasks.forEach { taskItem ->
                    val taskText = taskItem.binding.etTask.text.toString().trim()
                    if (taskText.isNotEmpty()) {
                        if (taskItem.task.id.isNotBlank()) {
                            // UPDATE EXISTING TASK
                            val updatedTask = taskItem.task.copy(
                                text = taskText,
                                isCompleted = taskItem.binding.cbCompleted.isChecked
                            )
                            db.taskDao().updateTask(updatedTask) // USE UPDATE INSTEAD OF INSERT
                        } else {
                            // CREATE NEW TASK
                            val newTask = TaskEntity(
                                listId = listId,
                                text = taskText,
                                isCompleted = taskItem.binding.cbCompleted.isChecked
                            )
                            db.taskDao().insertTask(newTask)
                        }
                    } else if (taskItem.task.id.isNotBlank()) {
                        // DELETE EMPTY EXISTING TASKS
                        db.taskDao().deleteTask(taskItem.task)
                    }
                }

                // SYNC WITH FIREBASE IF USER IS LOGGED IN
                if (auth.currentUser != null && !isGuestUser()) {
                    try {
                        syncWithFirebase(updatedList)
                    } catch (e: Exception) {
                        println("Firebase sync failed: ${e.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.text_list_updated), Toast.LENGTH_SHORT).show()
                    dismiss()
                    (activity as? OnListUpdatedListener)?.onListUpdated()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnSave.isEnabled = true
                    // MOSTRAR EL ERROR REAL PARA DEBUGGING
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    println("UPDATE ERROR: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun isGuestUser(): Boolean {
        val user = auth.currentUser
        return user == null || user.email?.contains("invitado") == true
    }

    private suspend fun syncWithFirebase(updatedList: TaskListEntity) {
        try {
            val user = auth.currentUser ?: return

            val taskListData = hashMapOf(
                "id" to updatedList.id,
                "userId" to user.uid,
                "title" to updatedList.title,
                "updatedAt" to System.currentTimeMillis()
            )

            firestore.collection("task_lists")
                .document(updatedList.id)
                .set(taskListData)
                .await()

            for (taskId in deletedTaskIds) {
                try {
                    firestore.collection("tasks").document(taskId).delete().await()
                } catch (e: Exception) {
                    continue
                }
            }

            for (taskItem in tasks) {
                val taskText = taskItem.binding.etTask.text.toString().trim()
                if (taskText.isNotEmpty()) {
                    val taskData = hashMapOf(
                        "id" to taskItem.task.id,
                        "listId" to listId,
                        "text" to taskText,
                        "isCompleted" to taskItem.binding.cbCompleted.isChecked,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    firestore.collection("tasks").document(taskItem.task.id).set(taskData).await()
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }

    interface OnListUpdatedListener {
        fun onListUpdated()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
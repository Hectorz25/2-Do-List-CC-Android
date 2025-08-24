package com.examenconcredito.a2_dolistapp.ui.fragments

import android.content.Context
import android.content.DialogInterface
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.examenconcredito.a2_dolistapp.R
import com.examenconcredito.a2_dolistapp.data.database.AppDatabase
import com.examenconcredito.a2_dolistapp.data.entities.TaskEntity
import com.examenconcredito.a2_dolistapp.data.entities.TaskListEntity
import com.examenconcredito.a2_dolistapp.data.repository.DatabaseRepository
import com.examenconcredito.a2_dolistapp.databinding.FragmentEditListBottomSheetBinding
import com.examenconcredito.a2_dolistapp.databinding.ItemTaskEditBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.min

class EditListBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentEditListBottomSheetBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private val auth by lazy { Firebase.auth }
    private val firestore by lazy { Firebase.firestore }

    private lateinit var listId: String
    private val tasks = mutableListOf<TaskEditItem>()
    private val deletedTaskIds = mutableListOf<String>()
    private var isProcessingEnter = false
    private var isKeyboardVisible = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
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

        setupBottomSheetBehavior()
        setupKeyboardAdjustment()
        setupKeyboardListener()
        setupListeners()
        loadListAndTasks()

        binding.root.post {
            adjustBottomSheetHeight()
        }

        dialog?.setOnShowListener {
            focusOnTitleInput()
            adjustBottomSheetHeight()
        }
    }

    private fun setupKeyboardAdjustment() {
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val bottomSheetDialog = dialog as? BottomSheetDialog
        bottomSheetDialog?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isFitToContents = false
        }
    }

    private fun setupKeyboardListener() {
        dialog?.window?.decorView?.viewTreeObserver?.addOnGlobalLayoutListener {
            val rect = Rect()
            dialog?.window?.decorView?.getWindowVisibleDisplayFrame(rect)
            val screenHeight = dialog?.window?.decorView?.height ?: 0
            val keypadHeight = screenHeight - rect.bottom

            val keyboardNowVisible = keypadHeight > screenHeight * 0.15
            if (keyboardNowVisible != isKeyboardVisible) {
                isKeyboardVisible = keyboardNowVisible
                if (isKeyboardVisible) {
                    adjustBottomSheetHeightForKeyboard(keypadHeight)
                } else {
                    adjustBottomSheetHeight()
                }
            }
        }
    }

    private fun adjustBottomSheetHeightForKeyboard(keyboardHeight: Int) {
        val bottomSheetDialog = dialog as? BottomSheetDialog
        val bottomSheet = bottomSheetDialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.let {
            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels

            val desiredHeight = screenHeight - keyboardHeight - 100

            val layoutParams = it.layoutParams
            if (layoutParams.height != desiredHeight) {
                layoutParams.height = desiredHeight
                it.layoutParams = layoutParams
            }
        }
    }

    private fun setupBottomSheetBehavior() {
        val dialog = dialog as? BottomSheetDialog
        dialog?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isFitToContents = false

            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        state = BottomSheetBehavior.STATE_EXPANDED
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {

                }
            })
        }
    }

    private fun adjustBottomSheetHeight() {
        val bottomSheetDialog = dialog as? BottomSheetDialog
        val bottomSheet = bottomSheetDialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.let {
            it.post {
                val displayMetrics = resources.displayMetrics
                val screenHeight = displayMetrics.heightPixels

                binding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val contentHeight = binding.root.measuredHeight

                val desiredHeight = min(contentHeight, (screenHeight * 0.9).toInt())

                val layoutParams = it.layoutParams
                if (layoutParams.height != desiredHeight) {
                    layoutParams.height = desiredHeight
                    it.layoutParams = layoutParams
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.etListName.postDelayed({
            focusOnTitleInput()
        }, 100)
    }

    private fun setupListeners() {
        binding.btnAddTask.setOnClickListener {
            handleAddTaskButtonClick()
        }

        binding.btnSave.setOnClickListener {
            updateListAndTasks()
        }

        binding.btnCancel.setOnClickListener {
            hideKeyboard()
            binding.root.post {
                dismiss()
            }
        }

        binding.etListName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                focusOnFirstEmptyOrExistingTask()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun handleAddTaskButtonClick() {
        focusOnFirstEmptyOrCreateNewTask()
    }

    private fun focusOnFirstEmptyOrExistingTask() {
        val emptyTask = tasks.firstOrNull { it.binding.etTask.text.toString().trim().isEmpty() }
        if (emptyTask != null) {
            emptyTask.binding.etTask.requestFocus()
            showKeyboard(emptyTask.binding.etTask)
        } else if (tasks.isNotEmpty()) {
            tasks.first().binding.etTask.requestFocus()
            showKeyboard(tasks.first().binding.etTask)
        }
    }

    private fun focusOnFirstEmptyOrCreateNewTask() {
        val emptyTask = tasks.firstOrNull { it.binding.etTask.text.toString().trim().isEmpty() }

        if (emptyTask != null) {
            emptyTask.binding.etTask.requestFocus()
            emptyTask.binding.etTask.setSelection(emptyTask.binding.etTask.text?.length ?: 0)
            showKeyboard(emptyTask.binding.etTask)
        } else if (hasValidTasks()) {
            addNewTaskInput(true)
        } else {
            Toast.makeText(requireContext(), getString(R.string.text_complete_current_tasks_first), Toast.LENGTH_SHORT).show()
        }
    }

    private fun focusOnTitleInput() {
        binding.etListName.requestFocus()
        binding.etListName.setSelection(binding.etListName.text?.length ?: 0)

        binding.root.postDelayed({
            showKeyboard(binding.etListName)
        }, 200)
    }

    private fun showKeyboard(view: View) {
        view.postDelayed({
            try {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 100)
    }

    private fun hideKeyboard() {
        try {
            val activity = requireActivity()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            val windowToken = dialog?.window?.decorView?.windowToken
            if (windowToken != null) {
                imm.hideSoftInputFromWindow(windowToken, 0)
            } else {
                var view = activity.currentFocus
                if (view == null) {
                    view = activity.window.decorView
                }
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }

            clearAllFocus()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun clearAllFocus() {
        binding.etListName.clearFocus()
        tasks.forEach { it.binding.etTask.clearFocus() }
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
                        addNewTaskInput(false)
                    }

                    adjustBottomSheetHeight()
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

        taskBinding.etTask.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                if (!isProcessingEnter) {
                    isProcessingEnter = true
                    handleEnterKeyPress(taskBinding)
                    isProcessingEnter = false
                }
                return@setOnKeyListener true
            }
            false
        }

        taskBinding.etTask.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                if (!isProcessingEnter) {
                    isProcessingEnter = true
                    handleEnterKeyPress(taskBinding)
                    isProcessingEnter = false
                }
                return@setOnEditorActionListener true
            }
            false
        }

        taskBinding.etTask.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString() ?: ""
                if (text.contains("\n")) {
                    taskBinding.etTask.removeTextChangedListener(this)
                    val cleanText = text.replace("\n", "")
                    taskBinding.etTask.setText(cleanText)
                    taskBinding.etTask.setSelection(cleanText.length)

                    if (cleanText.trim().isNotEmpty() && !isProcessingEnter) {
                        isProcessingEnter = true
                        handleEnterKeyPress(taskBinding)
                        isProcessingEnter = false
                    }
                    taskBinding.etTask.addTextChangedListener(this)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        taskBinding.cbCompleted.setOnCheckedChangeListener { _, isChecked ->
            updateTaskTextAppearance(taskBinding, isChecked)
        }

        taskBinding.btnRemoveTask.setOnClickListener {
            if (canRemoveTask(task)) {
                if (task.id.isNotBlank()) {
                    deletedTaskIds.add(task.id)
                }
                removeTaskInput(taskBinding)
            } else {
                Toast.makeText(requireContext(), getString(R.string.text_list_need_task), Toast.LENGTH_SHORT).show()
            }
        }

        binding.containerTasks.addView(taskBinding.root)
        tasks.add(TaskEditItem(taskBinding, task))
    }

    private fun canRemoveTask(taskToRemove: TaskEntity): Boolean {
        val remainingValidTasks = tasks.count { taskItem ->
            taskItem.task != taskToRemove &&
                    taskItem.binding.etTask.text.toString().trim().isNotEmpty()
        }
        return remainingValidTasks > 0
    }

    private fun addNewTaskInput(shouldFocus: Boolean = true) {
        val newTask = TaskEntity(
            id = "",
            listId = listId,
            text = "",
            isCompleted = false
        )
        addTaskInput(newTask)

        if (shouldFocus) {
            val lastTaskBinding = tasks.last().binding
            lastTaskBinding.etTask.post {
                lastTaskBinding.etTask.requestFocus()
                lastTaskBinding.etTask.setSelection(lastTaskBinding.etTask.text?.length ?: 0)
                showKeyboard(lastTaskBinding.etTask)
            }
        }

        binding.scrollView.post { binding.scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun handleEnterKeyPress(currentTaskBinding: ItemTaskEditBinding) {
        val currentText = currentTaskBinding.etTask.text.toString().trim()

        if (currentText.isNotEmpty()) {
            val currentIndex = tasks.indexOfFirst { it.binding == currentTaskBinding }
            var nextEmptyTask: TaskEditItem? = null

            for (i in currentIndex + 1 until tasks.size) {
                if (tasks[i].binding.etTask.text.toString().trim().isEmpty()) {
                    nextEmptyTask = tasks[i]
                    break
                }
            }

            if (nextEmptyTask == null) {
                nextEmptyTask = tasks.firstOrNull {
                    it.binding.etTask.text.toString().trim().isEmpty() && it.binding != currentTaskBinding
                }
            }

            if (nextEmptyTask != null) {
                nextEmptyTask.binding.etTask.requestFocus()
                nextEmptyTask.binding.etTask.setSelection(nextEmptyTask.binding.etTask.text?.length ?: 0)
                showKeyboard(nextEmptyTask.binding.etTask)
            } else if (hasValidTasks()) {
                addNewTaskInput(true)
            }
        }
    }

    private fun updateTaskTextAppearance(binding: ItemTaskEditBinding, isCompleted: Boolean) {
        if (isCompleted) {
            binding.etTask.paintFlags = android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            binding.etTask.paintFlags = 0
        }
    }

    private fun removeTaskInput(taskBinding: ItemTaskEditBinding) {
        if (tasks.size > 1) {
            val removedIndex = tasks.indexOfFirst { it.binding == taskBinding }
            binding.containerTasks.removeView(taskBinding.root)
            tasks.removeAll { it.binding == taskBinding }

            if (tasks.isNotEmpty()) {
                val focusIndex = if (removedIndex >= tasks.size) tasks.size - 1 else removedIndex
                val taskToFocus = tasks[focusIndex].binding.etTask
                taskToFocus.requestFocus()
                taskToFocus.setSelection(taskToFocus.text?.length ?: 0)
                showKeyboard(taskToFocus)
            }
        } else {
            Toast.makeText(requireContext(), getString(R.string.text_list_need_task), Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasValidTasks(): Boolean {
        return tasks.any { it.binding.etTask.text.toString().trim().isNotEmpty() }
    }

    private fun updateListAndTasks() {
        val newTitle = binding.etListName.text.toString().trim()

        if (newTitle.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.text_enter_list_name), Toast.LENGTH_SHORT).show()
            return
        }

        if (!hasValidTasks()) {
            Toast.makeText(requireContext(), getString(R.string.text_list_need_task), Toast.LENGTH_SHORT).show()
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

                deletedTaskIds.forEach { taskId ->
                    val taskToDelete = db.taskDao().getTaskById(taskId)
                    taskToDelete?.let { db.taskDao().deleteTask(it) }
                }

                tasks.forEach { taskItem ->
                    val taskText = taskItem.binding.etTask.text.toString().trim()
                    if (taskText.isNotEmpty()) {
                        if (taskItem.task.id.isNotBlank()) {
                            val updatedTask = taskItem.task.copy(
                                text = taskText,
                                isCompleted = taskItem.binding.cbCompleted.isChecked
                            )
                            db.taskDao().updateTask(updatedTask)
                        } else {
                            val newTask = TaskEntity(
                                listId = listId,
                                text = taskText,
                                isCompleted = taskItem.binding.cbCompleted.isChecked
                            )
                            db.taskDao().insertTask(newTask)
                        }
                    } else if (taskItem.task.id.isNotBlank()) {
                        db.taskDao().deleteTask(taskItem.task)
                    }
                }

                // UPDATE LIST COMPLETION STATUS
                val repository = DatabaseRepository(db)
                repository.updateListCompletionStatus(listId)

                if (auth.currentUser != null && !isGuestUser()) {
                    try {
                        syncWithFirebase(updatedList)
                    } catch (e: Exception) {
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.btnSave.isEnabled = true
                    Toast.makeText(requireContext(), getString(R.string.text_list_updated), Toast.LENGTH_SHORT).show()
                    dismiss()
                    (activity as? OnListUpdatedListener)?.onListUpdated()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnSave.isEnabled = true
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
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

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        hideKeyboard()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
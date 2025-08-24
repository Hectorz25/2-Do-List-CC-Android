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
import com.examenconcredito.a2_dolistapp.data.utils.PreferenceHelper
import com.examenconcredito.a2_dolistapp.databinding.FragmentCreateListBottomSheetBinding
import com.examenconcredito.a2_dolistapp.databinding.ItemTaskInputBinding
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

class CreateListBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCreateListBottomSheetBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private val auth by lazy { Firebase.auth }
    private val firestore by lazy { Firebase.firestore }
    private val preferenceHelper by lazy { PreferenceHelper(requireContext()) }

    private val tasks = mutableListOf<TaskInput>()
    private var userId: String = ""
    private var isProcessingEnter = false
    private var isKeyboardVisible = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
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
        setupBottomSheetBehavior()
        setupKeyboardAdjustment()
        setupKeyboardListener()
        setupListeners()
        addFirstTaskInput()

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
                    // NO ACTION NEEDED
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

                // USE 90% OF SCREEN HEIGHT WHEN NO KEYBOARD IS VISIBLE
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

        binding.btnCreate.setOnClickListener {
            createListWithTasks()
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
            addTaskInput(true)
        } else {
            Toast.makeText(requireContext(), "Complete existing tasks first", Toast.LENGTH_SHORT).show()
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

            // HIDE KEYBOARD USING WINDOW TOKEN FOR BETTER RELIABILITY
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

    private fun addFirstTaskInput() {
        addTaskInput(false)
    }

    private fun addTaskInput(shouldFocus: Boolean = true) {
        val taskBinding = ItemTaskInputBinding.inflate(layoutInflater, binding.containerTasks, false)

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

        taskBinding.btnRemoveTask.setOnClickListener {
            if (tasks.size > 1) {
                removeTaskInput(taskBinding)
            } else {
                taskBinding.etTask.setText("")
                taskBinding.etTask.requestFocus()
                showKeyboard(taskBinding.etTask)
                Toast.makeText(requireContext(), getString(R.string.text_min_one_task), Toast.LENGTH_SHORT).show()
            }
        }

        binding.containerTasks.addView(taskBinding.root)
        tasks.add(TaskInput(taskBinding))

        if (shouldFocus) {
            taskBinding.etTask.post {
                taskBinding.etTask.requestFocus()
                taskBinding.etTask.setSelection(taskBinding.etTask.text?.length ?: 0)
                showKeyboard(taskBinding.etTask)
            }
        }

        binding.scrollView.post {
            binding.scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun handleEnterKeyPress(currentTaskBinding: ItemTaskInputBinding) {
        val currentText = currentTaskBinding.etTask.text.toString().trim()

        if (currentText.isNotEmpty()) {
            val currentIndex = tasks.indexOfFirst { it.binding == currentTaskBinding }
            var nextEmptyTask: TaskInput? = null

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
                addTaskInput(true)
            }
        }
    }

    private fun removeTaskInput(taskBinding: ItemTaskInputBinding) {
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
        }
    }

    private fun hasValidTasks(): Boolean {
        return tasks.any { it.binding.etTask.text.toString().trim().isNotEmpty() }
    }

    private fun createListWithTasks() {
        val listTitle = binding.etListName.text.toString().trim()

        if (listTitle.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.text_enter_list_name), Toast.LENGTH_SHORT).show()
            binding.etListName.requestFocus()
            return
        }

        if (!hasValidTasks()) {
            Toast.makeText(requireContext(), getString(R.string.text_add_one_task), Toast.LENGTH_SHORT).show()
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

                taskEntities.forEach { task ->
                    db.taskDao().insertTask(task)
                }

                if (!isGuestUser() && auth.currentUser != null) {
                    try {
                        syncWithFirebase(taskList, taskEntities)
                    } catch (e: Exception) {
                        // SILENTLY FAIL FOR FIREBASE SYNC ERRORS
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

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        hideKeyboard()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        hideKeyboard()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
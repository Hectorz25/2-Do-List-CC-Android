package com.examenconcredito.a2_dolistapp.data.repository

import com.examenconcredito.a2_dolistapp.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseRepository(private val db: AppDatabase) {

    suspend fun updateListCompletionStatus(listId: String) {
        withContext(Dispatchers.IO) {
            val completedTasks = db.taskDao().getCompletedTasksCount(listId)
            val totalTasks = db.taskDao().getTotalTasksCount(listId)

            val isCompleted = totalTasks > 0 && completedTasks == totalTasks

            db.taskListDao().updateListCompletionStatus(listId, isCompleted)
        }
    }

    suspend fun updateAllListsCompletionStatus(userId: String) {
        withContext(Dispatchers.IO) {
            val lists = db.taskListDao().getTaskListsByUser(userId)

            lists.forEach { list ->
                val completedTasks = db.taskDao().getCompletedTasksCount(list.id)
                val totalTasks = db.taskDao().getTotalTasksCount(list.id)

                val isCompleted = totalTasks > 0 && completedTasks == totalTasks

                if (list.isCompleted != isCompleted) {
                    db.taskListDao().updateListCompletionStatus(list.id, isCompleted)
                }
            }
        }
    }
}
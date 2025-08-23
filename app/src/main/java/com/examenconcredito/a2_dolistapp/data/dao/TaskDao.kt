package com.examenconcredito.a2_dolistapp.data.dao

import androidx.room.*
import com.examenconcredito.a2_dolistapp.data.entities.TaskEntity

@Dao
interface TaskDao {
    @Insert
    suspend fun insertTask(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE listId = :listId")
    suspend fun getTasksByList(listId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE listId = :listId")
    suspend fun deleteAllTasksByList(listId: String)

    @Query("DELETE FROM tasks WHERE listId IN (SELECT id FROM task_lists WHERE userId = :userId)")
    suspend fun deleteAllTasksByUser(userId: String)
}
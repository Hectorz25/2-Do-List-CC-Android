package com.examenconcredito.a2_dolistapp.data.dao

import androidx.room.*
import com.examenconcredito.a2_dolistapp.data.entities.TaskEntity

@Dao
interface TaskDao {
    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Query("SELECT * FROM tasks WHERE listId = :listId")
    suspend fun getTasksByList(listId: Int): List<TaskEntity>

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)
}
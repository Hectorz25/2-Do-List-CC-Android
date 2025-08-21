package com.examenconcredito.a2_dolistapp.data.dao

import androidx.room.*
import com.examenconcredito.a2_dolistapp.data.entities.TaskListEntity

@Dao
interface TaskListDao {
    @Insert
    suspend fun insertTaskList(list: TaskListEntity): Long

    @Query("SELECT * FROM task_lists WHERE userId = :userId")
    suspend fun getTaskListsByUser(userId: Int): List<TaskListEntity>

    @Delete
    suspend fun deleteTaskList(list: TaskListEntity)
}
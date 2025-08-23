package com.examenconcredito.a2_dolistapp.data.dao

import androidx.room.*
import com.examenconcredito.a2_dolistapp.data.entities.TaskListEntity

@Dao
interface TaskListDao {
    @Insert
    suspend fun insertTaskList(list: TaskListEntity)

    @Query("SELECT * FROM task_lists WHERE userId = :userId")
    suspend fun getTaskListsByUser(userId: String): List<TaskListEntity>

    @Query("SELECT * FROM task_lists WHERE id = :listId")
    suspend fun getTaskListById(listId: String): TaskListEntity?

    @Delete
    suspend fun deleteTaskList(list: TaskListEntity)

    @Update
    suspend fun updateTaskList(list: TaskListEntity)

    @Query("DELETE FROM task_lists WHERE userId = :userId")
    suspend fun deleteAllTaskListsByUser(userId: String)
}
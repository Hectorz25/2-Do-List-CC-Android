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

    @Query("""
        SELECT tl.* FROM task_lists tl
        WHERE tl.userId = :userId 
        AND tl.id IN (
            SELECT t.listId FROM tasks t 
            GROUP BY t.listId 
            HAVING COUNT(*) = SUM(CASE WHEN t.isCompleted THEN 1 ELSE 0 END) AND COUNT(*) > 0
        )
    """)
    suspend fun getCompletedTaskLists(userId: String): List<TaskListEntity>

    @Query("""
        SELECT tl.* FROM task_lists tl
        WHERE tl.userId = :userId 
        AND (
            tl.id NOT IN (
                SELECT t.listId FROM tasks t 
                GROUP BY t.listId 
                HAVING COUNT(*) = SUM(CASE WHEN t.isCompleted THEN 1 ELSE 0 END) AND COUNT(*) > 0
            )
            OR tl.id IN (
                SELECT t.listId FROM tasks t 
                GROUP BY t.listId 
                HAVING COUNT(*) = 0
            )
        )
    """)
    suspend fun getPendingTaskLists(userId: String): List<TaskListEntity>

    @Query("UPDATE task_lists SET isCompleted = :isCompleted WHERE id = :listId")
    suspend fun updateListCompletionStatus(listId: String, isCompleted: Boolean)
}
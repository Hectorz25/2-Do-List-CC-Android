package com.examenconcredito.a2_dolistapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "task_lists")
data class TaskListEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val title: String,
    val isCompleted: Boolean = false,
    val firebaseId: String? = null
)
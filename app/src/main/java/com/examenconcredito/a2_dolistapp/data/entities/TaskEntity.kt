package com.examenconcredito.a2_dolistapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val text: String,
    val isCompleted: Boolean = false,
    val firebaseId: String? = null
) {
    fun isNew(): Boolean = id.isBlank()
}
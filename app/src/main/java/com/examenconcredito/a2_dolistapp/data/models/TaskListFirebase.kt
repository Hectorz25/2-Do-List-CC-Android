package com.examenconcredito.a2_dolistapp.data.models

data class TaskListFirebase(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val tasks: List<TaskFirebase> = emptyList()
)

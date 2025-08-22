package com.examenconcredito.a2_dolistapp.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val last_name: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val login: Boolean = false
) {
    //EMPTY CONSTRUCTOR FOR FIRESTORE
    constructor() : this("", "", "", "", "", "", false)
}
package com.examenconcredito.a2_dolistapp.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.examenconcredito.a2_dolistapp.data.dao.TaskDao
import com.examenconcredito.a2_dolistapp.data.dao.TaskListDao
import com.examenconcredito.a2_dolistapp.data.dao.UserDao
import com.examenconcredito.a2_dolistapp.data.entities.TaskEntity
import com.examenconcredito.a2_dolistapp.data.entities.TaskListEntity
import com.examenconcredito.a2_dolistapp.data.entities.UserEntity

@Database(
    entities = [UserEntity::class, TaskListEntity::class, TaskEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun taskListDao(): TaskListDao
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "2Do_List_DB"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
package com.clipr.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ClipCandidateEntity::class,
        SubtitleWordEntity::class,
        ExportedShortEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CliprDatabase : RoomDatabase() {

    abstract fun cliprDao(): CliprDao

    companion object {
        private const val DB_NAME = "clipr_projects.db"

        @Volatile
        private var INSTANCE: CliprDatabase? = null

        fun getInstance(context: Context): CliprDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CliprDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

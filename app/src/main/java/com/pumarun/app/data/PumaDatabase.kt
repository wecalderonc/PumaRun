package com.pumarun.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RunEntity::class], version = 2, exportSchema = true)
abstract class PumaDatabase : RoomDatabase() {
    abstract fun runs(): RunDao
}

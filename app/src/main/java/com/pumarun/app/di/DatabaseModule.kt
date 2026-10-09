package com.pumarun.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pumarun.app.data.PumaDatabase
import com.pumarun.app.data.RunDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PumaDatabase =
        Room.databaseBuilder(context, PumaDatabase::class.java, "puma.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun runDao(database: PumaDatabase): RunDao = database.runs()
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE runs ADD COLUMN targetPaceSecPerKm REAL")
        db.execSQL("ALTER TABLE runs ADD COLUMN goalTimeMs INTEGER")
        db.execSQL("ALTER TABLE runs ADD COLUMN paceAlertCount INTEGER NOT NULL DEFAULT 0")
    }
}

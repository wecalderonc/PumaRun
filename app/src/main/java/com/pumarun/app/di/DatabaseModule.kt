package com.pumarun.app.di

import android.content.Context
import androidx.room.Room
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
        Room.databaseBuilder(context, PumaDatabase::class.java, "puma.db").build()

    @Provides
    fun runDao(database: PumaDatabase): RunDao = database.runs()
}

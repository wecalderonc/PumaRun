package com.pumarun.app.di

import com.pumarun.app.data.RoomSessionRepository
import com.pumarun.app.data.SessionRepository
import com.pumarun.app.location.FusedLocationSource
import com.pumarun.app.location.LocationSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindLocationSource(impl: FusedLocationSource): LocationSource

    @Binds
    abstract fun bindSessionRepository(impl: RoomSessionRepository): SessionRepository
}

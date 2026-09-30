package com.pumaconcolor.run.di

import com.pumaconcolor.run.data.NoOpSessionRepository
import com.pumaconcolor.run.data.SessionRepository
import com.pumaconcolor.run.location.FusedLocationSource
import com.pumaconcolor.run.location.LocationSource
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
    abstract fun bindSessionRepository(impl: NoOpSessionRepository): SessionRepository
}

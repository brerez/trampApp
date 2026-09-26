package com.example.tramapp.di

import com.example.tramapp.data.repository.TramRepository
import com.example.tramapp.domain.junction.TripSequenceSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class JunctionModule {
    @Binds
    abstract fun bindTripSequenceSource(repo: TramRepository): TripSequenceSource
}

package com.example.tramapp.di

import com.example.tramapp.data.repository.TramRepository
import com.example.tramapp.domain.junction.DestinationNodeSource
import com.example.tramapp.domain.junction.JunctionDepartureSource
import com.example.tramapp.domain.junction.JunctionStationSource
import com.example.tramapp.domain.junction.NextStopLookup
import com.example.tramapp.domain.junction.NextStopResolver
import com.example.tramapp.domain.junction.PreferencesDestinationNodeSource
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

    @Binds
    abstract fun bindJunctionDepartureSource(repo: TramRepository): JunctionDepartureSource

    @Binds
    abstract fun bindJunctionStationSource(repo: TramRepository): JunctionStationSource

    @Binds
    abstract fun bindNextStopLookup(resolver: NextStopResolver): NextStopLookup

    @Binds
    abstract fun bindDestinationNodeSource(source: PreferencesDestinationNodeSource): DestinationNodeSource
}

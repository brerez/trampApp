package com.example.tramapp.di

import android.content.Context
import androidx.room.Room
import com.example.tramapp.data.local.TramDatabase
import com.example.tramapp.data.local.dao.ScheduleDao
import com.example.tramapp.data.local.dao.StationDao
import com.example.tramapp.data.local.dao.TripRouteDao
import com.example.tramapp.data.local.dao.LineDirectionDao
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
    fun provideTramDatabase(@ApplicationContext context: Context): TramDatabase {
        return Room.databaseBuilder(
            context,
            TramDatabase::class.java,
            "tram_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideStationDao(database: TramDatabase): StationDao {
        return database.stationDao()
    }

    @Provides
    fun provideScheduleDao(database: TramDatabase): ScheduleDao {
        return database.scheduleDao()
    }

    @Provides
    fun provideDepartureDao(database: TramDatabase): com.example.tramapp.data.local.dao.DepartureDao {
        return database.departureDao()
    }

    @Provides
    fun provideTripRouteDao(database: TramDatabase): TripRouteDao {
        return database.tripRouteDao()
    }

    @Provides
    fun provideLineDirectionDao(database: TramDatabase): LineDirectionDao {
        return database.lineDirectionDao()
    }
}

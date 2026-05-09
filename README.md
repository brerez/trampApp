# TramApp

TramApp is an intelligent Android application for tracking nearby tram schedules, prioritizing favorite stations, and providing smart routing logic to key destinations (Home, Work, School).

## ?? Key Features

- **Smart Destination Routing**: Uses a sophisticated station-cache and direction-validation engine to identify trams heading toward your saved Home, Work, or School locations.
- **Grouped Station View**: Automatically groups nearby platforms by station name (e.g., merging Platforms A & B of "Kamenick") while maintaining clear internal separation.
- **Real-time Departures**: Powered by the Golemio API with intelligent offline caching.
- **Performance Optimized**: Features asynchronous data loading and built-in API rate limiting (429 protection).
- **Configurable Control**: Adjust station discovery radius and the maximum number of stations to track.
- **Visual Excellence**: Premium dark-mode UI with glassmorphism and dynamic destination highlighting.

## ?? Tech Stack

- **UI**: Jetpack Compose
- **Architecture**: MVVM with Clean Architecture principles
- **DI**: Hilt
- **Persistence**: Room Database & DataStore
- **Networking**: Retrofit & OkHttp
- **Logic**: Geometric vector analysis for direction validation

## ?? Testing

The project includes a robust test suite covering edge cases, concurrency, and real-world scenarios.
- **Unit Tests**: 73 passing tests covering repository logic, edge cases, and math utilities.
- **Virtual Time Testing**: `ThrottleUtilConcurrencyTest` uses virtual time for reliable testing of rate-limiting logic.
- **Prague Real-World Bounds**: `RealWorldBoundEdgeCaseTest` validates direction matching against real Prague coordinates and edge cases (like missing stop IDs).

## ?? Setup

1. Add your Golemio API key to `local.properties`:
   ```
   GOLEMIO_API_KEY=your_key_here
   ```
2. Build and run the project using Android Studio.

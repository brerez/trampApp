# Project Summary

## General Architecture
The TramApp is an Android application designed to show nearby tram stations with information about their schedules. The app uses the Model-View-ViewModel (MVVM) architecture pattern, which separates concerns between data handling, UI presentation, and business logic.

## Technologies Used
- **Kotlin**: Primary programming language.
- **Room**: For local database management.
- **Retrofit or OkHttp + Gson**: For making network requests to fetch tram schedule data from a remote API (GolemioService).
- **Gradle**: Build system for the project.

## Users
The primary users of this app are commuters who need real-time information about tram schedules in their vicinity.

## File and Class Responsibilities

### Main Files
- **MainActivity.kt**: Entry point of the application.
- **TramApp.kt**: Application class, possibly holding global settings or configurations.

### Data Layer
- **app/src/main/java/com/example/tramapp/data/local/TramDatabase.kt**: Room database implementation managing SQLite database.
- **app/src/main/java/com/example/tramapp/data/local/entity/**: Contains data entities (DepartureEntity.kt, LineDirectionEntity.kt, etc.).
- **app/src/main/java/com/example/tramapp/data/local/dao/**: Data access objects for interacting with the local database (DepartureDao.kt, LineDirectionDao.kt, etc.).

### Remote Data
- **app/src/main/java/com/example/tramapp/data/remote/GolemioService.kt**: Interface or service class for fetching remote data from Golemio API.

### Repository Pattern
- **app/src/main/java/com/example/tramapp/data/repository/TramRepository.kt**: Repository pattern class abstracting data sources and providing clean APIs to the UI.

### Use Cases
- **app/src/main/java/com/example/tramapp/domain/**: Contains use case classes for specific business logic operations (CalculateSmartHighlightsUseCase.kt, etc.).

### UI Layer
- **app/src/main/java/com/example/tramapp/ui/DashboardScreen.kt**: Screen displaying nearby tram stations.
- **app/src/main/java/com/example/tramapp/ui/DashboardViewModel.kt**: ViewModel associated with DashboardScreen, handling data lifecycle.
- **app/src/main/java/com/example/tramapp/ui/SettingsScreen.kt**: Screen for application settings.
- **app/src/main/java/com/example/tramapp/ui/SettingsViewModel.kt**: ViewModel associated with SettingsScreen.

### Dependency Injection
- **app/src/main/java/com/example/tramapp/di/**: Contains modules (DatabaseModule.kt, LocationModule.kt, NetworkModule.kt) for dependency injection using Dagger or a similar library.
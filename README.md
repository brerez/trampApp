# TramApp

TramApp is an intelligent Android application for tracking nearby tram schedules, prioritizing favorite stations, and providing smart routing logic to key destinations (Home, Work, School).

## Key Features

- **Smart Destination Routing**: Uses a sophisticated station-cache and direction-validation engine to identify trams heading toward your saved Home, Work, or School locations, with a single colored line-badge cue plus a "Towards home/work/school" subtitle.
- **Honest Per-Station Loading States**: Each station card reflects its real fetch state (loading / ready / empty) instead of a generic spinner, so a still-resolving station is never mistaken for one with no trams.
- **Grouped Station View**: Automatically groups nearby platforms by station name (e.g., merging Platforms A & B of "Kamenick") while maintaining clear internal separation.
- **Progressive Reveal**: Shows the 3 nearest stations initially with a "Load More" button to reveal further stations on demand.
- **Live Freshness & Status Header**: A real "Updated Xs ago" indicator (loading/error/online) replaces the old debug counter; a settings gear icon replaces the star icon that used to collide with per-row favorites.
- **Auto-Refresh**: Departures refresh automatically every 60 seconds, with a live countdown timer that ticks continuously, and a fixed manual refresh button that no longer double-fetches.
- **Amenity Glyphs**: Small wheelchair-accessible and air-conditioning glyphs on departure rows when known from the Golemio API.
- **Real-time Departures**: Powered by the Golemio API with intelligent offline caching.
- **Nearest-First, Cheap-Before-Expensive Fetching**: Visible stations refresh nearest-first with built-in API rate limiting (429 protection) and no duplicate in-flight fetches.
- **Configurable Control**: Adjust station discovery radius and the maximum number of stations to track.
- **Visual Excellence**: Premium dark-mode UI with glassmorphism and a collapsible map peek to keep the first station's departures above the fold.

## Tech Stack

- **UI**: Jetpack Compose
- **Architecture**: MVVM with Clean Architecture principles
- **DI**: Hilt
- **Persistence**: Room Database & DataStore
- **Networking**: Retrofit & OkHttp
- **Logic**: Geometric vector analysis for direction validation

## Testing

The project includes a robust test suite covering edge cases, concurrency, and real-world scenarios, plus an instrumented UI test harness for real-emulator verification (see `docs/verification-harness.md`).
- **Unit Tests**: covering repository logic, edge cases, amenity fields, ViewModel fetch ordering/state, and math utilities.
- **Virtual Time Testing**: `ThrottleUtilConcurrencyTest` uses virtual time for reliable testing of rate-limiting logic.
- **Prague Real-World Bounds**: `RealWorldBoundEdgeCaseTest` validates direction matching against real Prague coordinates and edge cases (like missing stop IDs).
- **Instrumented UI Tests**: `app/src/androidTest/` drives the app on a real emulator (AVD `Samsung_Galaxy_S22`), capturing screenshots for visual verification.

## Design Docs

- `docs/plans/2026-07-18-001-feat-dashboard-trust-rework-plan.md` — the Dashboard Trust Rework plan (per-station state machine, single relevance cue, freshness header).
- `docs/design-review-dashboard.md` — design review resolving the plan's visual/UX forks.
- `docs/verification-harness.md` — agent-runnable recipe for running unit and instrumented tests.

## ?? Setup

1. Add your Golemio API key to `local.properties`:
   ```
   GOLEMIO_API_KEY=your_key_here
   ```
2. Build and run the project using Android Studio.

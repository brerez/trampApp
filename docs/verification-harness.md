# Verification harness (U1)

Agent-runnable recipe for verifying trampApp changes, covering both JVM unit tests and
the real-emulator instrumented UI tests. Written for Windows + Git Bash from repo root
`C:\git\trampApp`; adjust paths for other shells.

## Preconditions

- Java 17 on PATH; `./gradlew` runnable from repo root.
- `local.properties` at repo root contains `GOLEMIO_API_KEY` (and `MAPS_API_KEY`) —
  required by `RealApiIntegrationTest`/`GolemioAmenityProbeTest` and by the app itself at
  runtime.
- An Android emulator is running for `connectedDebugAndroidTest` (UI-affecting changes
  only). AVD `Samsung_Galaxy_S22` is the reference target; once booted it appears as
  `emulator-5554` in `adb devices`.
  - adb: `/c/Users/erezb/AppData/Local/Android/Sdk/platform-tools/adb.exe`
  - emulator: `/c/Users/erezb/AppData/Local/Android/Sdk/emulator/emulator.exe`

## Every change: JVM unit tests

```
./gradlew :app:testDebugUnitTest --rerun-tasks
```

`--rerun-tasks` matters — Gradle otherwise caches the test task as UP-TO-DATE and skips
re-running (and printing stdout for) unchanged test classes. To target one class:

```
./gradlew :app:testDebugUnitTest --tests "com.example.tramapp.ui.DashboardViewModelStateTest" --rerun-tasks
```

**Gotcha:** `DashboardViewModel.init` starts unconditional `while (autoRefreshEnabled) { delay(...) }`
background refresh loops. Any unit test that constructs a `DashboardViewModel` MUST set
`autoRefreshEnabled = false` immediately after construction (before the first dispatcher
advance) — otherwise `runTest`'s implicit final idle-check hangs forever against those
loops. Avoid `advanceUntilIdle()` in VM tests generally; prefer `runCurrent()` and bounded
`advanceTimeBy()`.

## UI-affecting changes: instrumented tests on the emulator

1. Confirm the emulator is up: `adb devices` should list `emulator-5554` as `device`.
2. Run:
   ```
   ./gradlew :app:connectedDebugAndroidTest
   ```
3. Pass/fail: Gradle prints a per-test PASSED/FAILED summary and writes a report to
   `app/build/reports/androidTests/connected/debug/index.html`.
4. Screenshots: tests call `ScreenshotUtil.capture(rule, name)`, which writes to public
   shared storage at `/sdcard/Pictures/trampapp-screenshots/<name>.png` (deliberately not
   the app's private storage, which `connectedAndroidTest` wipes on auto-uninstall after
   the run). Pull them with:
   ```
   adb pull /sdcard/Pictures/trampapp-screenshots ./app/build/screenshots
   ```

## Test-tag vocabulary (established here, used by U5/U9-U11)

UI units attach `Modifier.testTag(...)` using these fixed names so instrumented tests can
target them without depending on visible text/copy:

| Tag             | Meaning                                                             |
|-----------------|----------------------------------------------------------------------|
| `station-card`  | A rendered, populated (`Ready`) station card.                        |
| `skeleton`      | A `Loading` placeholder row/card (never an empty-looking real card). |
| `empty-collapse`| Marker asserting a settled-`Empty` station renders no node at all — used as a negative assertion (`onNodeWithTag("empty-collapse").assertDoesNotExist()` style checks target station name/id instead, since a collapsed item has no node to tag). |
| `amenity-glyph` | An accessibility/AC amenity icon on a departure row.                 |

## Recipe integrity

From a clean checkout with the emulator already booted, `./gradlew :app:testDebugUnitTest --rerun-tasks`
then `./gradlew :app:connectedDebugAndroidTest` should reproduce a green run of both suites
using only the steps above.

## API 36 / edge-to-edge promotion target

The app now targets API 36 (`compileSdk = 36`, `targetSdk = 36`).
AVD `Samsung_Galaxy_S22` uses `system-images/android-36/google_apis/x86_64` (already API 36),
so it is the reference AVD for both API 36 promotion checks and edge-to-edge visual verification.

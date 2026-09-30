# AGENTS.md

Guidance for AI agents working on Puma, a GPS running app for Android (Kotlin, Jetpack Compose,
Hilt). Read `README.md` for the product overview; this file covers how to build, test and change it.

## Environment

- JDK 17 is required. On this machine it is Homebrew's: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17`.
- Android SDK: `/opt/homebrew/share/android-commandlinetools` (set in `local.properties`, which is
  gitignored; create it with `sdk.dir=...` on a new machine).
- `adb`: `$SDK/platform-tools/adb`. Emulator: `$SDK/emulator/emulator`. AVD name: `puma_test`
  (Pixel 7, Android 35, Google Play image, arm64).
- Gradle (wrapper 8.11.1) needs unrestricted network and local sockets; in a sandboxed shell it fails
  with "Could not determine a usable wildcard IP". Run it with full permissions.

## Commands

```bash
./gradlew assembleDebug        # build APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM unit tests (fast, no device)
./gradlew lintDebug            # lint; keep it at 0 errors
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Always run unit tests after touching `domain/` or `location/`. Library versions in
`gradle/libs.versions.toml` are pinned to a set known to work with AGP 8.7 / Kotlin 2.1; upgrade them
together, not one at a time.

## Architecture (where to change what)

| Want to change | File |
|---|---|
| What is spoken and when (milestones, halfway, goal, pace alerts, trainer cues) | `domain/AnnouncementScheduler.kt` |
| Trainer styles and their timing | `domain/CoachStyle.kt` |
| Wording of spoken sentences | `voice/Phrases.kt` + `res/values/strings.xml` and `res/values-es/strings.xml` |
| TTS engine, locale, audio ducking | `voice/VoiceCoach.kt` |
| GPS noise filtering | `location/LocationFilter.kt` |
| Distance/time/pace bookkeeping | `domain/SessionEngine.kt`, `domain/PaceCalculator.kt` |
| Run lifecycle (countdown, pause, stop) | `service/SessionManager.kt` |
| Foreground service and notification | `service/RunTrackingService.kt`, `service/RunNotification.kt` |
| Setup inputs, validation, saved settings | `ui/setup/*`, `data/SettingsRepository.kt` |
| Screens | `ui/setup`, `ui/active`, `ui/summary`, `ui/permissions` |

Key rules:

- `domain/` and `location/LocationFilter` are pure Kotlin (no Android APIs beyond `Intent` in
  `SessionConfig`) so they stay unit-testable. Keep new logic there and test it.
- `SessionManager` is the single source of truth (a Hilt singleton `StateFlow<SessionState>`).
  Screens only observe it; navigation follows `SessionStatus` in `ui/PumaNavHost.kt`.
- All scheduler timing uses active session time (`SessionState.elapsedMs`), never wall-clock, so
  pauses freeze timers.
- Distance uses a haversine in `TrackPoint.distanceTo`, not `Location.distanceTo`, so tests run on the JVM.

## Localization

- Every user-facing or spoken string must exist in both `values/strings.xml` (English) and
  `values-es/strings.xml` (Spanish). Use `plurals` for counted units.
- Trainer phrases are string arrays named `coach_<style>_<cue>` (style: soft, medium, hard, annoying;
  cue: push, encourage, back, almost, final). Adding a style means adding all five arrays in both
  languages plus a branch in `Phrases.coachArray`.
- `Phrases` loads resources for the TTS locale at runtime, which is why language splits are disabled in
  `app/build.gradle.kts`.

## Testing on the emulator

Start the emulator as a long-running background job (a child process started with `&` dies when the
shell returns):

```bash
$SDK/emulator/emulator -avd puma_test -no-snapshot -no-boot-anim -gpu auto
adb shell getprop sys.boot_completed   # wait for "1"
```

Simulate a run: start the run in the app (tap START), then feed GPS:

```bash
ADB=$SDK/platform-tools/adb python3 tools/feed_gps.py --km 1.3 --pace 5:20 \
  --slow-from 0.35 --slow-to 0.65 --slow-pace 7:00
```

Run the feeder as a background job too. Verify speech with `adb logcat -s PumaVoice` (every spoken
line is logged with its locale); verify ducking with `adb logcat -s MediaFocusControl`.

Gotchas learned the hard way:

- `adb emu geo fix` with a velocity makes the emulator keep dead-reckoning after the last fix, so
  distance keeps growing. `feed_gps.py` ends with a zero-velocity fix; do the same for manual fixes.
- A single manual `geo fix` far from the runner is a good test of the jump filter, but it will be
  counted if it looks like a plausible speed over the gap.
- Gboard may open a "Try out your stylus" tutorial when typing with `adb shell input text`; disable it
  with `adb shell settings put secure stylus_handwriting_enabled 0`.
- `KEYCODE_BACK` to hide the keyboard can close the activity. Tap a non-input element instead.
- Notification shade layout shifts; find buttons with `adb shell uiautomator dump` rather than fixed
  coordinates.
- Hold-to-stop needs a long press: `adb shell input swipe X Y X Y 2200`.
- Play Store images cannot change the system locale over adb. Use per-app locales:
  `adb shell cmd locale set-app-locales com.pumaconcolor.run --locales es-MX` (reset with `--locales ""`).
- Screenshots: `adb exec-out screencap -p > shot.png`.

## Conventions

- Match the existing style: small files, `StateFlow` for state, Hilt constructor injection.
- Comments only for constraints the code cannot show; no narration comments.
- v2 (not built yet): saved run history via a Room implementation of `data/SessionRepository`, map
  drawing, login. Don't add these piecemeal without being asked.

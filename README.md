# PumaRun

A GPS running app for Android. Set a distance goal and optionally a target (pace, speed, or finish
time), then run. PumaRun tracks distance with GPS in a foreground service and talks to you: distance
milestones with how far ahead/behind schedule you are, halfway, goal reached, pace warnings, and an
optional trainer voice that pushes you. UI and voice are in English or Spanish, following the device
language.

## Targets

- **Pace** (min:sec per km), **Speed** (km/h), or **Time**: a finish time for the goal distance, e.g.
  5 km in 30:00. PumaRun derives the pace (6:00 /km). The -30s / -15s / +15s / +30s buttons make it easy to
  aim a bit faster each day; the last target is remembered.
- During the run the screen shows how far ahead/behind schedule you are; the summary shows your time at
  the goal vs. the target time.

## Trainer voice

| Style | Push when slower than target | Encouragement on pace | Tone |
|---|---|---|---|
| Off | facts only, every 60 s | none | "You are below your target pace..." |
| Soft | every 90 s | every 4 min | "You can do this, just a bit faster." |
| Medium | every 45 s | every 2.5 min | "Come on!", "Strength!", "You can do it!" |
| Hard | every 25 s (after 10 s slow) | every 3 min | "Is that all you've got? Speed up!" |
| Annoying | every 12 s (after 8 s slow) | every 1 min | "Faster! Faster! Faster!" |

Every third push restates the numbers (current vs. target pace). All styles except Off also cheer
"almost there" at 85% and "last 200 meters" (goals of 2 km or more). The coach goes quiet once the goal
is reached. Phrases live in `res/values*/strings.xml` (`coach_*` arrays) and are picked at random
without repeating back to back.

## Build

Requirements: JDK 17, Android SDK 35 (Android Studio bundles both).

```bash
./gradlew assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew installDebug           # install on a connected device/emulator
./gradlew testDebugUnitTest      # unit tests
```

If building from the command line, point `local.properties` at your SDK (`sdk.dir=...`).
Contributor and AI-agent notes (emulator setup, test workflow, gotchas) are in [AGENTS.md](AGENTS.md).

## How it works

- `service/SessionManager` owns the active run: countdown, GPS collection, a 1 Hz ticker, and voice.
- `service/RunTrackingService` is a `location` foreground service that keeps the run alive with the
  screen off and shows an ongoing notification with Pause/Resume/Stop.
- `location/LocationFilter` drops inaccurate, stale and impossible fixes (including teleports after a
  signal gap) and ignores stationary drift.
- `domain/SessionEngine` accumulates distance and active time (pauses excluded).
- `domain/PaceCalculator` gives current pace over a 30 s rolling window.
- `ui/map/RunMap` shows where you are (OpenStreetMap, no API key) on setup, during the run
  (with the route of this session), and on the summary.
- `domain/AnnouncementScheduler` decides what to say and when; `domain/CoachStyle` holds each trainer's
  timing. `voice/Phrases` turns that into localized sentences.
- `voice/VoiceCoach` speaks via TextToSpeech and ducks music while talking. Every spoken line is logged
  under the `PumaVoice` logcat tag.

## Manual testing on an emulator

Scripted run (no emulator UI needed):

```bash
ADB=$ANDROID_HOME/platform-tools/adb python3 tools/feed_gps.py \
  --km 1.3 --pace 5:20 --slow-from 0.35 --slow-to 0.65 --slow-pace 7:00
adb logcat -s PumaVoice   # watch what the coach says
```

Start the run in the app, then start the feeder. `--start-km` continues a previous feed.
Alternatively `tools/make_gpx.py` writes a GPX file for Extended controls > Location > Routes.

To test Spanish without changing the whole device:
`adb shell cmd locale set-app-locales com.pumarun.app --locales es-MX` (reset with `--locales ""`).

Real-world checks: run with the screen locked and music playing (music should duck, not stop), and
pause/resume from the notification.

## Battery optimization

Some manufacturers (Xiaomi, Huawei, Samsung, OnePlus and others) aggressively kill background apps even
with a foreground service. If tracking stops with the screen off, set PumaRun's battery usage to
"Unrestricted" / disable battery optimization for the app. See https://dontkillmyapp.com for
device-specific steps.

## Recorded tracks

Finishing a run saves it on the phone (Room). The Tracks tab lists them; opening one shows the route
on the map. Deleting a track removes it from the phone.

## v2

Login and accounts. Run history and the live map are already in the app.

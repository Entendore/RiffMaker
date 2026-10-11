# Riff Maker

Piano-roll riff sketchpad for Android: guitar tab, sheet notation, MIDI/WAV/M4A
export, sharing. 100% offline - no INTERNET permission, no ads, no tracking.

## Build & run
1. Open this folder in Android Studio (it will offer to create the Gradle wrapper
   if you don't have the `gradle` CLI).
2. Run the `app` configuration on a device/emulator (minSdk 29).

## Tests
    ./gradlew testDebugUnitTest

## Before releasing
- Change `applicationId` / `namespace` in `app/build.gradle.kts` to your final ID.
- Replace `support_email` in `res/values/strings.xml`.
- Set release signing via gradle properties (RELEASE_STORE_FILE, etc.) or use
  Build > Generate Signed Bundle in Android Studio.

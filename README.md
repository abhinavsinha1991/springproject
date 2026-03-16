# Android Photo Cleanup

An Android app that scans Google Photos for duplicate and low-quality images and helps users clean them up.

## Project Structure

```
android-photo-cleanup/   # Android app module
.github/workflows/       # CI — build APK, unit tests, instrumentation tests
```

## Requirements

- Android Studio Hedgehog or newer
- JDK 17
- Android SDK API 33+
- A Google Cloud project with the Photos Library API enabled and OAuth 2.0 credentials configured

## Building

```bash
cd android-photo-cleanup
./gradlew assembleDebug
```

## Running Tests

```bash
# JVM unit tests
./gradlew test

# Instrumentation tests (requires a connected device or emulator)
./gradlew connectedDebugAndroidTest
```

## CI

GitHub Actions runs on every push and pull request:

1. Build debug APK
2. Run JVM unit tests
3. Run instrumentation tests on an API 33 emulator

Instrumentation tests only run if the APK build succeeds.

# Photo Cleanup — Android App

## Project structure
- `android-photo-cleanup/` — the entire Android project (Gradle root is here)
  - `app/` — main Android application module (`com.photocleanup`)
  - `detector-test/` — standalone JVM Kotlin module (JUnit 5, no Android deps)

## Tech stack
- Language: Kotlin (primary), some Java
- UI: Jetpack Compose + ViewBinding
- Min SDK: 26 (Android 8.0), Target/Compile SDK: 34
- JDK: 17
- Build: Gradle 8.3, Android Gradle Plugin 8.1.2
- Key libs: Retrofit 2, OkHttp3, Coil 2, Coroutines 1.7, WorkManager, Paging 3, MockK

## Build commands (run from `android-photo-cleanup/`)
| Goal | Command |
|---|---|
| Build debug APK | `./gradlew assembleDebug --no-daemon` |
| JVM unit tests | `./gradlew test --no-daemon` |
| Instrumentation tests | `./gradlew connectedDebugAndroidTest --no-daemon` (needs emulator) |
| Clean | `./gradlew clean` |

## CI jobs (`build-apk.yml`)
1. **build-and-test** — compiles APK + runs JVM unit tests (no emulator needed)
2. **instrumentation** — runs Espresso tests on API 29 x86 emulator (pixel_2 profile); only on `push` or PRs labelled `run-e2e`

## Common CI noise (not real failures)
- `Warning: This version only understands SDK XML versions up to 3` — harmless sdkmanager warning
- `ERROR | Unable to connect to adb daemon on port: 5037` — normal at emulator startup
- Gradle cache miss warnings — transient GitHub Actions infra issue

## Secrets required in CI
- `DEBUG_KEYSTORE_BASE64` — base64-encoded debug keystore
- `GRADLE_ENCRYPTION_KEY` — Gradle build cache encryption key
- `GH_PAT` — GitHub PAT (used only in `setup-keystore.yml`)

## Fixing CI failures
When the `build-and-test` job fails, CI writes a `ci-failure-logs.txt` file to the
branch root and commits it automatically. Pushes that only change this file are
ignored by the workflow (`paths-ignore`), so it never triggers a re-run.

To investigate and fix:
1. `git pull` — pick up the log commit
2. Read `ci-failure-logs.txt` — contains the Gradle error output
3. Fix the relevant source file(s)
4. `git rm ci-failure-logs.txt` — remove it before committing
5. Commit and push — this triggers CI normally; on the next green build CI
   removes any lingering log file automatically

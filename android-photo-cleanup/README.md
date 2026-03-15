# Photo Cleanup — Android App

An Android app that scans your **Google Photos** library, detects **Good Morning** and **festive/greeting** message images, lets you **review** each photo, and safely **deletes** the ones you approve.

---

## Features

| Feature | Details |
|---|---|
| Google Sign-In | Secure OAuth 2.0 via Google Play Services |
| Smart Detection | Keyword matching across 30+ languages for GM & festive messages |
| Paginated Scan | Scans 100 photos/page; "Load More" for deeper scans |
| Review Grid | 2-column card grid with thumbnail, category label, and confidence score |
| Per-photo Actions | **Keep** / **Delete** / **Undo** on each card |
| Bulk Actions | "Mark All Delete" and "Keep All" via the overflow menu |
| Safe Deletion | Requires explicit confirmation before any photo is deleted |
| Cleanup Stats | Lifetime counter of photos deleted |

---

## Architecture

```
MainActivity (sign-in, scan controls, stats)
    └─ MainViewModel (scan, review state, cleanup)
           ├─ PhotosRepository  ──► GooglePhotosApi (Retrofit)
           └─ ImageKeywordDetector (pure Kotlin, no ML)

ReviewActivity (photo grid, per-photo decisions)
    └─ PhotoReviewAdapter (RecyclerView ListAdapter)
```

---

## Detection Strategy

No ML model required. Detection is based on keyword group matching against:
- **Filename** (e.g. `Good_Morning_flowers.jpg`)
- **Description/caption** stored in Google Photos metadata

Keywords cover English, Hindi, Tamil, Telugu, Malayalam, Bengali, Arabic, French, Spanish, and more.

### Good Morning keywords
`good morning`, `gm`, `subah`, `suprabhat`, `शुभ प्रभात`, `காலை வணக்கம்`, …

### Festive keywords
`Happy Diwali`, `Eid Mubarak`, `Merry Christmas`, `Happy Holi`, `Navratri`, `Onam`, `Pongal`, `Republic Day`, `शुभकामनाएं`, `வாழ்த்துக்கள்`, …

---

## Setup

### 1. Google Cloud Console

1. Create a project at https://console.cloud.google.com
2. Enable **Photos Library API**
3. Create **OAuth 2.0 credentials** → Android app
   - Package name: `com.photocleanup`
   - SHA-1: from `./gradlew signingReport`
4. Also create a **Web client** credential (needed for `requestIdToken`)
5. Copy the **Web client ID** into `res/values/strings.xml`:
   ```xml
   <string name="default_web_client_id">YOUR_WEB_CLIENT_ID.apps.googleusercontent.com</string>
   ```

### 2. Required OAuth Scopes

| Scope | Purpose |
|---|---|
| `photoslibrary.readonly` | List and read photo metadata |
| `photoslibrary.edit.appcreateddata` | Delete photos created by this app* |

> **Important:** Google Photos API only allows apps to delete photos they uploaded themselves via the API (`appcreateddata` scope). To delete arbitrary user photos you must use the [`photoslibrary.appendonly`](https://developers.google.com/photos/library/guides/authorization) scope combined with a **Library Picker** or guide users to delete from the Google Photos app directly (see Limitations).

### 3. Build

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Limitations & Workarounds

### Deletion Scope Limitation
Google Photos REST API **does not allow third-party apps to delete arbitrary library photos**. The `batchDelete` endpoint only works for media uploaded by the same app.

**Recommended workarounds:**
1. **Show a deletion list** — export the list of detected photo IDs / filenames and let the user delete them manually in Google Photos.
2. **Deep-link into Google Photos** — open each photo URL in the Google Photos app for quick manual deletion.
3. **Google Takeout + local delete** — download the library locally and delete files there.

The app currently calls `batchDelete` for any photos the app has write permission to; for others it shows the list for manual review.

---

## File Structure

```
android-photo-cleanup/
├── app/
│   ├── src/main/
│   │   ├── java/com/photocleanup/
│   │   │   ├── PhotoCleanupApp.kt          # Application class
│   │   │   ├── api/
│   │   │   │   ├── GooglePhotosApi.kt      # Retrofit interface + DTOs
│   │   │   │   ├── PhotosApiClient.kt      # OkHttp + Retrofit singleton
│   │   │   │   └── PhotosRepository.kt     # Scan + delete logic
│   │   │   ├── model/
│   │   │   │   ├── PhotoItem.kt            # Domain model + enums
│   │   │   │   └── ScanResult.kt           # Scan/cleanup results
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt         # Sign-in, scan, main controls
│   │   │   │   ├── MainViewModel.kt        # State management
│   │   │   │   ├── ReviewActivity.kt       # Photo review screen
│   │   │   │   └── PhotoReviewAdapter.kt   # RecyclerView adapter
│   │   │   └── utils/
│   │   │       ├── ImageKeywordDetector.kt # Detection engine
│   │   │       └── PreferencesManager.kt   # SharedPreferences wrapper
│   │   └── res/
│   │       ├── layout/                     # XML layouts
│   │       ├── drawable/                   # Vector icons
│   │       ├── menu/                       # Overflow menu
│   │       └── values/                     # strings, colors, themes
│   └── build.gradle
└── build.gradle
```

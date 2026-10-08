<div align="center">

<img src="app/src/main/res/drawable-nodpi/app_icon.png" alt="APK Organizer" width="140" />

# APK Organizer

**Scan, organize, and manage all your APK files — effortlessly.**

A lightweight yet powerful Android utility built with **Kotlin + Jetpack Compose**.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white&style=for-the-badge)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?logo=jetpackcompose&logoColor=white&style=for-the-badge)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white&style=for-the-badge)](https://www.android.com)
[![Version](https://img.shields.io/badge/Version-1.0.9-blue?style=for-the-badge)](https://github.com/Sajedur0/APK-Organizer/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)
[![Stars](https://img.shields.io/github/stars/Sajedur0/APK-Organizer?style=for-the-badge&logo=github)](https://github.com/Sajedur0/APK-Organizer/stargazers)
[![Issues](https://img.shields.io/github/issues/Sajedur0/APK-Organizer?style=for-the-badge&logo=github)](https://github.com/Sajedur0/APK-Organizer/issues)

</div>

---

> **v1.0.9 — Kotlin rewrite.** The entire app (UI, state and logic) was ported
> from Flutter/Dart to native Kotlin with Jetpack Compose, keeping the design,
> behavior and performance characteristics of the Flutter version intact.

## Features

<table>
<tr>
<td width="50%" valign="top">

### 🔍 Scanning
- Recursive background scan across device storage
- Live progress updates during scan
- Directory filter for targeted scanning

### 📦 Batch Operations
- Multi-select mode for bulk actions
- Batch install, rename, move, delete
- Batch backup & uninstall installed apps

### 🔎 Search & Sort
- Real-time search with debounce
- Sort by name, size, date, or version
- Searchable directory filter

</td>
<td width="50%" valign="top">

### 🧠 Smart Organize
- Auto-rename files to `AppName_VersionName.apk`
- Detect & remove duplicates in one pass
- Cancellable with progress dialog + summary report

### 📄 APK Details
- Version info & SDK range (min → target)
- Supported ABIs & SHA-256 signature
- Full permission list

### 🎨 Theme
- Light, Dark & System mode
- Persistent preference
- Quick toggle from app bar

</td>
</tr>
</table>

### 🔄 In-App Updates
- Google Play **In-App Updates** (`com.google.android.play:app-update-ktx`)
- **Flexible** (<3 days staleness) — dismissible background download
- **Immediate** (≥3 days or Play priority) — blocking full-screen flow
- Auto `completeUpdate()` on `DOWNLOADED` for seamless restart

### ⚡ Performance
- Batched scan progress — the list updates a few times per second, not per file
- Directories are walked once, APKs are parsed on a bounded worker pool
- Cached icons decoded at display size; O(1) lookups for selection, rename,
  move and delete (no full-list rescans)
- Stoppable scans that keep the files found so far

### Also Includes
- **Installed Apps** — Browse user & system apps with detail page
- **Backup** — Export installed apps as APK files (single & batch)
- **Rename** — Auto or manual rename with undo support
- **Move** — Visual file browser with conflict auto-suffix & undo
- **Share** — Share APK files via system share sheet
- **Auto Updates** — Native Play UI only, no custom dialogs

---

## Permissions

| Permission | Purpose |
|:---|:---|
| `MANAGE_EXTERNAL_STORAGE` | Scan APK files across all storage (Android 11+) |
| `READ_EXTERNAL_STORAGE` | Read APK files (Android 10 and below) |
| `REQUEST_INSTALL_PACKAGES` | Install APK files directly |
| `REQUEST_DELETE_PACKAGES` | Uninstall apps |
| `QUERY_ALL_PACKAGES` | List installed apps for APK parsing |

---

## Tech Stack

| Layer | Technology |
|:---|:---|
| **Language** | Kotlin 2.3 |
| **UI** | Jetpack Compose + Material 3 (Compose BOM) |
| **Concurrency** | Kotlin Coroutines (bounded worker pools) |
| **Storage** | SharedPreferences |
| **Updates** | Google Play In-App Updates (`app-update-ktx`) |
| **Min SDK** | Android 7.0 (API 24) |
| **Target / Compile SDK** | 37 |

---

## Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Ladybug or newer)
- JDK 21
- Android SDK with `compileSdk 37`

### Build from Source

```bash
# Clone the repository
git clone https://github.com/Sajedur0/APK-Organizer.git
cd APK-Organizer

# Generate the Gradle wrapper (one time; or just open the project in Android Studio)
gradle wrapper --gradle-version 9.2

# Build a release APK (uses key.properties + your keystore when present)
./gradlew assembleRelease
```

The APK will be at `app/build/outputs/apk/release/app-release.apk`.

> The Gradle wrapper jar is not committed. Opening the project in Android
> Studio handles the Gradle/SDK setup automatically.

### Install from Play Store

<a href="https://play.google.com/store/apps/details?id=com.apkorganizer">
  <img src="https://upload.wikimedia.org/wikipedia/commons/7/78/Google_Play_Store_badge_EN.svg" alt="Get it on Google Play" height="60" />
</a>

---

## Project Structure

```
app/src/main/kotlin/com/apkorganizer/
├── MainActivity.kt           # Single-activity host (launchers, receivers, update flow)
├── App.kt                    # Root composable (theming + overlay navigation)
├── data/                     # Data layer
│   ├── ApkManager.kt         # The engine: scanning, install, rename, move, icons…
│   ├── ApkFile.kt            # APK model (sort keys, rename rules, identity)
│   ├── ApkDetailInfo.kt      # Parsed APK detail (permissions, ABIs, signature)
│   ├── InstalledApp.kt       # Installed app model
│   └── DirectoryEntry.kt     # Directory browser model
├── services/                 # Business logic
│   ├── ScannerService.kt     # Streaming scan with cancellation
│   ├── RenamerService.kt     # Auto-rename (AppName_Version.apk)
│   ├── DuplicateHandler.kt   # Duplicate analysis & removal (keep newest)
│   ├── FileOperations.kt     # Batch move with conflict suffixes
│   ├── PreferencesService.kt # Theme + sort persistence
│   ├── AppUpdateService.kt   # Play In-App Update (flexible → immediate, staleness-aware)
│   └── LoggerService.kt      # Ring-buffer logger
├── ui/
│   ├── theme/AppTheme.kt     # Exact M3 color schemes + AppRadius constants
│   ├── screens/              # Home, APK detail, installed/system apps, privacy
│   ├── dialogs/              # About, summary
│   └── widgets/              # Tiles, chips, sheets, snackbars, loading, search
└── utils/                    # FormatUtil, runParallel, VersionUtil
```

---

## Author

**Sajedur0** — [@Sajedur0](https://github.com/Sajedur0)

---

## License

This project is licensed under the [MIT License](LICENSE).

---

<div align="center">

**If you find this project useful, consider giving it a ⭐**

</div>

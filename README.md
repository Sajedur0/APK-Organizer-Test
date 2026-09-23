<div align="center">

<img src="icon.png" alt="APK Organizer" width="140" />

# APK Organizer

**Scan, organize, and manage all your APK files — effortlessly.**

A lightweight yet powerful Android utility built with Flutter.

[![Flutter](https://img.shields.io/badge/Flutter-3.11%2B-02569B?logo=flutter&logoColor=white&style=for-the-badge)](https://flutter.dev)
[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white&style=for-the-badge)](https://www.android.com)
[![Version](https://img.shields.io/badge/Version-1.0.9-blue?style=for-the-badge)](https://github.com/Sajedur0/APK-Organizer/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)
[![Stars](https://img.shields.io/github/stars/Sajedur0/APK-Organizer?style=for-the-badge&logo=github)](https://github.com/Sajedur0/APK-Organizer/stargazers)
[![Issues](https://img.shields.io/github/issues/Sajedur0/APK-Organizer?style=for-the-badge&logo=github)](https://github.com/Sajedur0/APK-Organizer/issues)

</div>

---

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

### 🔄 In-App Updates
- Google Play `in_app_update_android: ^1.1.5` integration
- **Flexible** (<3 days staleness) — dismissible background download
- **Immediate** (≥3 days or Play priority) — blocking full-screen flow
- Auto `completeFlexibleUpdate()` on `downloaded` for seamless restart

</td>
</tr>
</table>

### ⚡ Performance
- Batched scan progress — the list updates a few times per second, not per file
- Directories are walked once, APKs are parsed on a background worker pool
- Cached icons decoded at display size; fixed-extent lists for stutter-free scroll
- O(1) lookups for selection, rename, move and delete (no full-list rescans)
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
| **Framework** | Flutter (Dart) |
| **Native Bridge** | MethodChannel / EventChannel (Kotlin) |
| **State Management** | StatefulWidget + setState |
| **Storage** | SharedPreferences |
| **Min SDK** | Android 7.0 (API 24) — required by `in_app_update_android` (needs ≥21) |
| **Compile SDK** | 37 |

---

## Getting Started

### Prerequisites

- [Flutter SDK](https://flutter.dev/docs/get-started/install) `^3.12.0`
- Android SDK with `compileSdk 37`

### Build from Source

```bash
# Clone the repository
git clone https://github.com/Sajedur0/APK-Organizer.git
cd APK-Organizer

# Install dependencies
flutter pub get

# Build a release APK
flutter build apk --release
```

The signed APK will be at `build/app/outputs/flutter-apk/app-release.apk`.

### Install from Play Store

<a href="https://play.google.com/store/apps/details?id=com.apkorganizer">
  <img src="https://upload.wikimedia.org/wikipedia/commons/7/78/Google_Play_Store_badge_EN.svg" alt="Get it on Google Play" height="60" />
</a>

---

## Project Structure

```
lib/
├── main.dart                 # App entry point
├── app_theme.dart            # Light / Dark theme definitions
├── src/
│   └── entry_point.dart      # ApkManagerApp widget
├── models/                   # Data models
├── screens/                  # UI screens
│   ├── home_page.dart
│   ├── apk_detail_page.dart
│   ├── installed_apps_page.dart
│   └── installed_app_detail_page.dart
├── services/                 # Business logic & native bridge
│   ├── apk_manager_service.dart
│   ├── scanner_service.dart
│   ├── file_operations.dart
│   ├── duplicate_handler.dart
│   ├── renamer_service.dart
│   ├── preferences_service.dart
│   ├── logger_service.dart
│   ├── app_update_service.dart   # Play In-App Update (flexible → immediate, staleness-aware)
│   └── update_service.dart       # Legacy shim → delegates to AppUpdateService
├── widgets/                  # Reusable UI components
├── dialogs/                  # Dialog widgets
└── utils/                    # Utilities & helpers
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

# Changelog

All notable changes to this project are documented in this file.

## [1.0.9] — Kotlin rewrite
- The entire app was rewritten in **Kotlin with Jetpack Compose (Material 3)**,
  replacing the Flutter/Dart UI and the MethodChannel bridge with a pure
  native implementation
- Design, behavior and performance characteristics are preserved 1:1: the exact
  color schemes and corner radii, snackbar/action timing, scan batching
  (140 ms / 24-file progress, 130 ms UI flush), bounded worker pools
  (rename 4 / move 3 / backup 2), 900 ms install spacing, undo snackbars and
  the staleness-aware Play In-App Update flow
- What used to live in the Flutter-side services is now `ApkManager` +
  coroutine-based services; the UI is a single-activity Compose app
- Much faster scans: directories are walked in one pass, APKs are parsed on a
  bounded worker pool, and progress is delivered in throttled batches instead of
  one event per file
- Smooth list rendering: cached sort/search keys, O(1) lookups for selection,
  rename, move and delete, and downscaled icon decoding
- A scan can be stopped from the list or the floating action button and
  keeps everything found so far
- Smart Organize is idempotent: correctly named files (including `_1` conflict
  names) are skipped, and the summary reports them separately
- Batch backup of installed apps runs in parallel with visible progress
- Icons are cached and decoded at display size, so lists scroll without the
  PNG decode stutter

## [1.0.9] — Flutter (previous implementation)
- Streaming scan with live progress and cancellation
- Smart Organize (auto-rename + duplicate removal) with summary report
- Batch operations, undoable rename/move, directory browser with folder
  creation
- Google Play in-app updates (flexible → immediate escalation)

## [1.0.8]
- Improved in-app updates with a dismissible download at first, and a blocking update if postponed
- Updates now resume correctly after restart and are more reliable in the background
- Uses only the native Play Store update UI and avoids crashes without Play Store

## [1.0.7]
- General performance and stability improvements

## [1.0.6]
- Fixed an issue with in-app updates
- Improved overall performance

## [1.0.5]
- Added background scanning with live progress updates
- Added Smart Organize to auto-rename files and remove duplicates in one tap
- Added multi-select for batch install, rename, move, and delete
- Added detailed APK info and improved filtering and sorting
- Updated design with Material 3 and added dark mode
- Improved permission handling and installation, fixed duplicate detection

## [1.0.4]
- Added backup for installed apps as APK files
- Added privacy policy and improved directory browser
- Refined APK list design and made search smoother
- Fixed crashes on devices without Play Services and for special file names

## [1.0.3]
- Added browsing for system apps
- Added auto and manual rename with suggested names
- Improved navigation drawer and APK detail sheet
- Fixed permission flow on Android 10 and file errors during quick scans

## [1.0.2]
- Added APK details and Rate Us and About screens
- Fixed freezes during large directory scans
- Fixed memory leak in scan progress
- General performance and stability improvements

## [1.0.1]
- Added search, sorting, and installed apps list
- Improved APK scanning speed and reliability
- Improved scan progress display
- Fixed crashes on permission denial and on Android 7 and above

## [1.0.0]
- Initial release with APK scanning across device storage
- Added install, rename, move, and delete for APK files
- Added light theme with Material Design

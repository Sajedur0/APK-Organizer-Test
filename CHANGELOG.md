# Changelog

All notable changes to this project are documented in this file.

## [1.4.0] — Midnight Azure glass + logic tune-up
- **Professional glass palette** — the green/sand glass theme is replaced by
  "Midnight Azure": a deep navy canvas, azure primary, teal secondary and soft
  indigo tertiary, with frosted-white surfaces in dark mode and crisp white
  glass over a pale blue canvas in light mode. All palette values now live in
  one `AppPalette` object so schemes, gradients and the pre-Android-12 glass
  fallbacks cannot drift apart. The aurora glows are azure + teal, and the
  specular sheen is slightly crisper.
- **Race-safe scanner guard** — the "scan already running" check is now an
  atomic compare-and-set, so two simultaneous starts can never both scan.
- **Single-pass duplicate removal** — `removeDuplicates` groups files once
  instead of scanning for duplicates and then grouping again, and keeps a
  deterministic group order. `DuplicateGroup` caches its keep/delete choice.
- **Lighter `ApkFile` caches** — derived values use publication-mode lazies
  (no per-property lock on thousands of instances) and the search text is
  built with a single `StringBuilder`.

## [1.3.2] — Aurora glass polish + logic optimizations
- **Aurora backdrop** — the two sage/sand glows behind the glass now slowly
  drift along small orbits and breathe in size/alpha on a 12s loop, so the
  frosted canvas feels lit from within. The animation lives in its own leaf
  composable, so only the backdrop layer redraws each frame; the screens on
  top never recompose because of it.
- **Specular sheen** — a subtle glossy highlight band is now painted across
  every frosted surface (top/selection bars, snackbar, drawer), the Smart
  Insights hero card, the APK list tiles and the drawer header, giving the
  surfaces real "glass" depth instead of a flat translucent fill.
- **Batch removal is now O(n)** — deleting many files (batch delete, Smart
  Organize, duplicate cleanup) rebuilds the list and position index once
  instead of re-indexing the whole list per removed file (was O(n × k)).
- **Faster scans** — the scanner reuses the `ApkFile` instances it already
  built for progress batches when assembling the final result (no second
  parse of every file), and the home list adopts them with a sort + re-index
  instead of rebuilding from scratch.
- **Cheaper sorting** — sort comparators no longer lower-case file names on
  every comparison (cached per file), removing thousands of allocations when
  sorting large lists.
- **Parallel duplicate detection** — `detectDuplicates` now parses archives
  across the worker pool instead of sequentially.
- **Fresh directory filter cache** — the filter's folder cache is keyed to a
  structural version counter, so it can no longer go stale after a
  same-count move/rename.
- **Sharing** checks file existence in a single background hop instead of
  one dispatcher switch per file.

## [1.3.1] — Deep blur for the Transparent Glossy system
- **Heavier frosted blur everywhere** so nothing behind the glass stays
  visible: top bars, selection bars, the snackbar and the navigation drawer
  now combine a painted canvas replica, a live snapshot of the content behind
  blurred with a 52dp radius (Android 12+), a thick translucent glass tint
  and a glossy rim
- **Real window blur behind dialogs & bottom sheets** (Android 12+): the app
  behind confirmations, rename, filters, directory browser, details and
  summary sheets melts into a soft haze
- **Thicker glass tokens** — translucent container alphas are raised in both
  themes so cards and panels are properly frosted instead of clear
- **Graceful fallback** — below Android 12 dialogs use opaque frosted slabs
  so the background never shows through on any device

## [1.3.0] — Glassmorphism redesign
- **Frosted glass design language** inspired by modern glassmorphism UI:
  deep-forest green canvas with soft sage/sand glows, translucent white
  container tokens, 1px light strokes and generous 24dp radii
- **New palettes** — dark "Leafora forest" (sage primary, mint secondary,
  sand tertiary) and a frosted light theme of white glass over pale sage
- **GlassBackdrop** behind every screen: vertical green gradient plus two
  radial glows; all scaffolds and top app bars are now transparent so the
  glass floats on the canvas
- Hero Smart Insights card, drawer header, brand mark, empty states and
  detail headers get the translucent emerald→sage gradient with a light
  glass border
- Light/dark toggle still works — both themes are fully glass-styled
- No behavior changes — all logic and operations remain identical

## [1.2.0] — Visual redesign (v2)
- **New design language** — a deep-emerald → bright-teal signature gradient,
  softer radii (cards 18, controls 14, dialogs 24, sheets 30) and a cleaner
  tonal surface style across light & dark themes
- **Redesigned home screen** — branded app bar with live file-count &
  total-size subtitle, gradient hero Smart Insights card with white-on
  gradient stats, floating "Scan Now" FAB and a rounded scan-progress card
- **Gradient drawer header** with file-count and total-size chips
- **New list tiles** — larger 56dp icons, softer borders, stronger
  selection state (1.5dp accent border), duplicate badge chips
- **Floating selection bars** (home + installed apps) — rounded, elevated
  cards that slide in and out
- APK detail, installed-app detail, empty states and the permission screen
  all adopt the gradient identity mark
- No behavior changes — state management and every operation work exactly
  as in 1.1.0

## [1.1.0] — Smart Insights & polish
- **Smart Insights card** — a live overview above the APK list: total file
  count, combined size and distinct app count, computed in a single O(n)
  pass whenever the list changes (never on search/sort keystrokes)
- **Duplicate awareness everywhere** — the insights card shows how many
  redundant copies exist and exactly how much space removing them would
  free; every redundant file gets a small "Duplicate" badge in the list,
  using the exact same keep-newest rules as duplicate removal, so the UI
  can never disagree with what cleanup actually deletes
- **One-tap duplicate cleanup** — a "Clean" action on the insights card
  removes redundant copies (newest kept) with confirmation, progress
  dialog and a summary that reports the **space reclaimed**
- **Smart Organize summary** now also reports the space reclaimed by
  duplicate removal
- **Folder memory** — the last folder used for a move or backup is
  remembered and offered as a "Recently used folder" shortcut at the top
  of the directory picker
- **Faster batch delete** — deleting many selected files now runs on a
  bounded worker pool instead of one file at a time
- Selection action bar slides in/out with animation and its five action
  chips scroll horizontally on narrow screens
- Refreshed empty state and a total-size badge in the navigation drawer
- Fixed several compile-blocking issues from the initial Kotlin rewrite
  (invalid icon references, a broken modifier chain in the directory
  filter sheet, an invalid `Canvas` size modifier and missing imports) so
  the project builds cleanly again

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

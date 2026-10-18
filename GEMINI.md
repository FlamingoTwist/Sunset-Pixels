# GEMINI.md — Sunset Soundwaves Agent Guide

**Sunset Soundwaves** (`com.retro.pixelplayer`) is an Android retro 16-bit cassette-deck music player built with Kotlin, Jetpack Compose, and Media3 (`ExoPlayer`). It blends local device audio files and Spotify playlists/saved tracks into physical-feeling cassettes stored on a wooden **Artist Shelf** and in a cardboard **Playlist Shoe Box**.

This document captures the architectural nuances, non-obvious workarounds, and visual/audio invariants that agents must preserve when modifying the app.

---

## 1. Build & Release Environment

- **JDK Path (Windows)**: Use JetBrains Runtime 21 at `C:\Users\neila\.jdks\jbr-21.0.11`.
- **Gradle Binary**: Use `C:\Users\neila\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat` (the repo does not rely on a checked-in `gradlew.bat`).
- **Standard Build Command (PowerShell)**:
  ```powershell
  $env:JAVA_HOME = "C:\Users\neila\.jdks\jbr-21.0.11"
  & "C:\Users\neila\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat" assembleDebug
  ```
- **Distributable APK Convention**:
  Whenever shipping changes to GitHub (`https://github.com/FlamingoTwist/Sunset-Pixels`), always:
  1. Copy `app\build\outputs\apk\debug\app-debug.apk` to `Sunset-Pixels.apk` and `Sunset-Soundwaves.apk` in the repo root.
  2. Force-add both APKs (`git add -f Sunset-Pixels.apk Sunset-Soundwaves.apk` since `*.apk` is in `.gitignore`).
  3. Update the GitHub Release asset:
     ```powershell
     gh release upload v0.2.0 "s:\AmosApps\Music Player\Sunset-Pixels.apk" "s:\AmosApps\Music Player\Sunset-Soundwaves.apk" --clobber
     ```
  *Current Stable Release*: `v0.2.0` (all subsequent development targets `0.3`).

---

## 2. Project Layout & Key Files

- `app/src/main/java/com/retro/pixelplayer/MainActivity.kt`
  - Contains the root `PixelPlayerApp` state machine, `ExoPlayer` setup, OAuth redirect handling (`pixelplayer://callback`), and the `DeckScreen` (both Landscape and Portrait layouts).
- `app/src/main/java/com/retro/pixelplayer/spotify/SpotifyManager.kt`
  - Handles Spotify OAuth 2.0 PKCE authentication, token auto-refresh, cloud library sync (with Spotify Feb 2026 API fallbacks), full-length audio stream resolution, and local disk caching (`spotify_library_cache_v2.json`).
- `app/src/main/java/com/retro/pixelplayer/ui/components/CassetteDeck.kt`
  - Renders the cassette deck door (`deck_door_empty_clean`), dynamic cassette label/shell, pixelated album cover art (`FilterQuality.None`), and rotating reel spokes (`cassette_reel_spokes`) with dynamic magnetic tape pack radii.
- `app/src/main/java/com/retro/pixelplayer/ui/components/LcdDisplay.kt`
  - Renders the backlit mint-green LCD frame (`lcd_display_clean`) with pixel text overlays (track title, artist, source badge, elapsed/total time, transport state).
- `app/src/main/java/com/retro/pixelplayer/ui/components/PixelArtButton.kt` & `MechanicalButton.kt`
  - Custom pressable/latched button components with mechanical sound & haptic feedback (`MechanicalFeedback.kt`).
- `app/src/main/java/com/retro/pixelplayer/ui/screens/TapeSelectionScreen.kt`
  - The dual-tab cassette browser: **Artist Shelf** (grouped by artist with expandable album sub-tapes) and **Shoe Box** (playlists / homemade mixtapes).
- `app/src/main/java/com/retro/pixelplayer/util/PixelArtConverter.kt`
  - Converts high-res album/playlist artwork into crunchy 48×48 16-bit palette-quantized bitmaps.

---

## 3. Critical Nuances & Gotchas

### A. Spotify Playback: Official High-Quality Streams via Spotify Android App Remote SDK
1. **Authentic Songs vs Fuzzy Search Covers**:
   - Earlier revisions attempted fuzzy text lookups across third-party engines (SoundCloud, JioSaavn), which frequently resolved to amateur bedroom covers, karaoke, or nightcore.
   - Every Spotify track in Sunset Pixels retains its authentic Spotify URI (`track.spotifyUri`, e.g. `spotify:track:6rqhFgbbKwnb9MLmUQDhG6`).
   - The app integrates the official **Spotify Android App Remote SDK** (`app/libs/spotify-app-remote-release-0.8.0.aar` + `com.google.code.gson:gson:2.10.1`), managed via `SpotifyRemoteManager.kt`.
2. **How Spotify Remote Playback Operates**:
   - `SpotifyRemoteManager.connect(context)` binds via IPC to the installed Spotify app (`com.spotify.music`).
   - Android 11+ Package Visibility: `<queries><package android:name="com.spotify.music" /></queries>` in `AndroidManifest.xml` is mandatory for the app to detect and bind to Spotify.
   - Playing a Spotify track invokes `SpotifyRemoteManager.play(context, track.spotifyUri)`.
   - `PlayerApi.subscribeToPlayerState()` streams real-time authoritative player position, duration, `isPaused`, and track transitions.
   - Local tracks (`content://` or `file://`) continue playing natively through `ExoPlayer`, while Spotify tracks stream through the Spotify app.
   - Transport controls (Play, Pause, Stop, Seek, Skip, Shuffle, Repeat) seamlessly route to `SpotifyRemoteManager` when a Spotify track is active.

### B. Spotify Feb 2026 Web API Restrictions & Embed Scraper Fallback
- Spotify's Feb 2026 Development Mode changes restricted several endpoints (`/v1/playlists/{id}/tracks` can return `403 Forbidden` or redact track payloads for non-owned/algorithmic playlists, and `/items` replaces `/tracks` in newer endpoints).
- `SpotifyManager.fetchPlaylistTracksWithFallback()` handles this automatically with a 3-tier strategy:
  1. `/v1/playlists/{id}/items`
  2. `/v1/playlists/{id}/tracks`
  3. **Public Embed HTML Scraper** (`https://open.spotify.com/embed/playlist/{id}` -> parses `<script id="__NEXT_DATA__">` JSON ` state.data.entity.trackList`).
- Do not remove the embed scraper fallback—it is the only way algorithmic/editorial Spotify playlists populate tracks reliably in Dev Mode.

### C. Instant Startup Cassette Shelf (`spotify_library_cache_v2.json`)
- Users expect their Spotify cassettes and last inserted tape to be present **immediately on frame 1** when reopening the app after force-closing.
- `SpotifyManager.saveLibraryToCache()` serializes all synced playlist tapes, artist tapes, sub-tapes, track lists, and Base64-encoded 48×48 pixelated PNG cover bitmaps to `File(context.filesDir, "spotify_library_cache_v2.json")`.
- In `MainActivity.kt`, `initialSpotifyCache = remember { SpotifyManager.loadCachedLibrary(context) }` runs synchronously during initial composition so `spotifyArtistTapes`, `spotifyPlaylistTapes`, and `currentTape` (via `getLastTapeId(context)`) are populated before the first frame renders. A quiet background sync then refreshes the library without wiping existing UI state.

### D. Compose `pointerInput` Closure Trap (`PixelArtButton` & `MechanicalButton`)
- Both `PixelArtButton` and `MechanicalButton` use `.pointerInput(isLatched) { detectTapGestures(...) }`.
- Because `isLatched` is constant (`false`) for transport buttons like **Rewind**, **Fast-Forward**, and **Skip**, `pointerInput` does **not** restart when state variables in `MainActivity` (such as `currentPositionMs`, `totalDurationMs`, or `activeTrackIndex`) change.
- **Rule**: Inside `PixelArtButton` and `MechanicalButton`, **always** wrap `onDownPress`, `onHoldRepeat`, and `onClick` with `rememberUpdatedState(...)`.
- Furthermore, in `MainActivity.kt`, `onSeek(deltaMs)` computes the target seek position directly from `exoPlayer.currentPosition` and `exoPlayer.duration` rather than relying on snapshot state alone:
  - **Single Tap** on Rewind / Fast-Forward: seeks `-10_000L` / `+10_000L`.
  - **Press and Hold (`onHoldRepeat`)**: after a `260ms` hold threshold, scrubs `-5_000L` / `+5_000L` every `160ms` with a mechanical tick, and suppresses the release `onClick` so it doesn't double-jump.

### E. Pixel-Art Visual & Layout Invariants
1. **Clean Sliced Sprite Assets**:
   - The Walkman UI uses surgical pixel-art slices in `app/src/main/res/drawable-nodpi/` (`deck_door_empty_clean.png`, `cassette_reel_spokes.png`, `lcd_display_clean.png`, `btn_*_unpressed.png` / `btn_*_pressed.png`, `switch_*_off.png` / `switch_*_on.png`).
   - Do **not** replace `deck_door_empty_clean` or `lcd_display_clean` with baked-in text sprites; all dynamic text and rotating reels are composited in Compose.
2. **Reel Spoke Rotation & True Hub Centers (`CassetteDeck.kt`)**:
   - `spinner_hub_left.png` and `spinner_hub_right.png` are centered at `(55, 55)` in a 111×111 square so they rotate cleanly around `TransformOrigin.Center` without wobble or eccentricity.
   - The true geometric reel centers in `deck_cassette_base.png` sit at `(339f / 1024f, 271f / 593f)` for the left spool and `(681f / 1024f, 271f / 593f)` for the right spool.
3. **Nearest-Neighbor Filtering**:
   - Whenever rendering pixel art `ImageBitmap`s (especially `artworkBitmap` on cassettes), always pass `filterQuality = FilterQuality.None` to keep crisp pixel edges.
4. **Dual Orientation Support**:
   - `MainActivity.kt` has no orientation lock in `AndroidManifest.xml` and branches on `configuration.orientation == Configuration.ORIENTATION_LANDSCAPE` inside `DeckScreen`. Any new HUD element or button added to `DeckScreen` must be wired in **both** the Landscape and Portrait branches.
5. **Full-Resolution Background Scaling & Aspect Ratio Invariants**:
   - The orange chassis texture scales to fit the 100% full resolution of the device screen (`Modifier.fillMaxSize()`, `ContentScale.FillBounds`).
   - In Landscape, the app renders `chassis_texture_bg` (1024×460, aspect ratio ~2.22:1).
   - In Portrait, the app renders `chassis_texture_portrait` (460×1024, aspect ratio ~1:2.22). This ensures the texture orientation matches the device without stretching or blurring across orientations.
   - Safe drawing insets (`Modifier.windowInsetsPadding(WindowInsets.safeDrawing)`) are applied **strictly to the foreground content layer** (buttons, header bar, deck door, LCD display) so that buttons never collide with Android system navigation bars (`▢`, `○`, `◁`) or camera notches, while the chassis texture extends edge-to-edge behind the navigation bar.

### F. Android Auto / Automotive Head-Unit Support
- Declared in `AndroidManifest.xml` via `<meta-data android:name="com.google.android.gms.car.application" android:resource="@xml/automotive_app_desc" />` and `androidx.car.app.minCarApiLevel`.
- `PlaybackService.kt` extends `androidx.media3.session.MediaLibraryService` with intent filters for `androidx.media3.session.MediaLibraryService` and `android.media.browse.MediaBrowserService`.
- Media hierarchy exposes `root` -> `Artist Shelf 📚` and `Shoe Box (Playlists) 📦` -> individual cassette albums/playlists -> playable tracks. Selecting a track on the car touchscreen triggers headless background playback for both local and Spotify cassettes.

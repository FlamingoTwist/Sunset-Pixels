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
  1. Copy `app\build\outputs\apk\debug\app-debug.apk` to `Sunset-Cassette.apk` in the repo root.
  2. Force-add the APK (`git add -f Sunset-Cassette.apk` since `*.apk` is in `.gitignore`).
  3. Update the GitHub Release asset:
     ```powershell
     gh release upload v0.4.0 "s:\AmosApps\Music Player\Sunset-Cassette.apk" --clobber
     ```
  *Current Stable Release*: `v0.3.0` (all subsequent development targets `0.4`).

- **Direct Physical Device Deployment via ADB (Wireless & USB)**:
  - **ADB Binary Path**: Windows does not always have `adb` in the system `PATH`. Always invoke ADB using:
    ```powershell
    $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    ```
  - **Device Auto-Detection**:
    The developer's physical phone connects via Android Wireless Debugging (TLS/mDNS identifier starting with `adb-` or standard serial number). Avoid pushing to the emulator (`emulator-5554`) unless requested.
  - **Single-Command Deploy & Launch (PowerShell)**:
    ```powershell
    $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    # Find physical device (prioritise wireless adb-* or non-emulator device)
    $targetDevice = (& $adb devices | Select-String -Pattern "(\S+)\s+device\b" | ForEach-Object { $_.Matches.Groups[1].Value } | Where-Object { $_ -notlike "emulator*" } | Select-Object -First 1)

    if ($targetDevice) {
        Write-Host "Deploying to device: $targetDevice"
        & $adb -s $targetDevice install -r "Sunset-Cassette.apk"
        # Instant-launch main activity headlessly
        & $adb -s $targetDevice shell monkey -p com.retro.pixelplayer -c android.intent.category.LAUNCHER 1
    } else {
        Write-Warning "No physical Android device found connected to ADB."
    }
    ```
  - **Why Android Studio's Gemini Fails**:
    Android Studio's internal assistant often attempts to invoke raw `adb` in environments where `PATH` is stripped, or fails because multiple devices exist (phone + emulator) without passing the explicit `-s <device_id>` selector, or attempts Gradle `installDebug` which fails if target device disambiguation is missing. Always use explicit `$adb -s $targetDevice install -r ...`.

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

### A. Spotify Integration Scope & Playback via Spotify Android App Remote SDK
1. **Scope: Local First & Spotify Playlists**:
   - The **Artist Shelf**, **Album Views**, **Song Lists**, and **Mixtape Recording** are strictly for **local audio files** (`MediaStore` / device files).
   - Spotify integration is restricted to importing user playlists into the cardboard **Playlist Shoe Box** (`spotifyPlaylistTapes`).
   - Spotify Web API player endpoints, fuzzy searches, artist lookups, and third-party scrapers/proxies are completely abandoned and removed.
   - Users create/edit Spotify playlists inside the official Spotify app.
2. **Authentic Spotify App Remote Playback**:
   - When a Spotify playlist tape is inserted and played, `SpotifyRemoteManager.play(context, playlistUri)` sends the authentic playlist URI to the Spotify App Remote SDK.
   - `PlayerApi.subscribeToPlayerState()` streams real-time authoritative player position, duration, `isPaused`, and track transitions.
   - Local tracks continue playing natively through `ExoPlayer`, while Spotify playlist tapes stream through the Spotify app.
   - Transport controls (Play, Pause, Stop, Seek, Skip, Shuffle, Repeat) seamlessly route to `SpotifyRemoteManager` when a Spotify tape is active.

### B. Sunset Record Shop ("The Shop")
- Dig through crates of your own local music collection: **ARTISTS**, **ALBUMS**, and **SONGS**.
- Instant real-time CRT search across title, artist, and album.
- Every song has `▶ PLAY` to instantly cue/play and `🔴 REC` to record onto a handmade mixtape.
- Zero network dependencies: 100% offline, snappy, and authentic.

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
3. **Ejection Layer Hierarchy**:
   - When ejecting a cassette, the deck inner cavity/chassis (`deck_cassette_inner`) remains strictly stationary inside the deck frame.
   - Only the moving cassette elements translate vertically: the dynamic cassette shell (`cassette_shell_*`), the off-white label (`cassette_label_clean`), the album artwork, and the rotating reels/tape packs.
4. **Cassette Colorways & Randomization**:
   - Defined in `CassetteShellColor` enum: `CREAM` (classic beige), `STUDIO_OBSIDIAN` (charcoal/black), `SPORTS_YELLOW` (retro sports yellow), `MIAMI_MINT` (pastel mint/teal), and `SYNTHWAVE_CRIMSON` (bold 80s crimson).
   - Master Aseprite source: `artwork/cassette_colourways.aseprite`.
   - All colorways share a consistent off-white label. When a cassette is selected or loaded, a colorway is chosen randomly (or hashed from the tape ID) to keep the shelf feeling fresh and physical.
5. **Nearest-Neighbor Filtering**:
   - Whenever rendering pixel art `ImageBitmap`s (especially `artworkBitmap` on cassettes), always pass `filterQuality = FilterQuality.None` to keep crisp pixel edges.
6. **Dual Orientation Support**:
   - `MainActivity.kt` has no orientation lock in `AndroidManifest.xml` and branches on `configuration.orientation == Configuration.ORIENTATION_LANDSCAPE` inside `DeckScreen`. Any new HUD element or button added to `DeckScreen` must be wired in **both** the Landscape and Portrait branches.
7. **Full-Resolution Background Scaling & Warm Yellow-Orange Chassis**:
   - The chassis texture scales to fit the 100% full resolution of the device screen (`Modifier.fillMaxSize()`, `ContentScale.FillBounds`).
   - In Landscape, the app renders `chassis_texture_bg` (1024×460, aspect ratio ~2.22:1).
   - In Portrait, the app renders `chassis_texture_portrait` (460×1024, aspect ratio ~1:2.22).
   - Chassis textures use a warm retro amber/mustard yellow-orange tone with distress speckling.
   - Safe drawing insets (`Modifier.windowInsetsPadding(WindowInsets.safeDrawing)`) are applied **strictly to the foreground content layer** (buttons, header bar, deck door, LCD display) so that buttons never collide with Android system navigation bars (`▢`, `○`, `◁`) or camera notches, while the chassis texture extends edge-to-edge behind the navigation bar.

### F. Android Auto / Automotive Head-Unit Support
- Declared in `AndroidManifest.xml` via `<meta-data android:name="com.google.android.gms.car.application" android:resource="@xml/automotive_app_desc" />` and `androidx.car.app.minCarApiLevel`.
- `PlaybackService.kt` extends `androidx.media3.session.MediaLibraryService` with intent filters for `androidx.media3.session.MediaLibraryService` and `android.media.browse.MediaBrowserService`.
- Media hierarchy exposes `root` -> `Artist Shelf 📚` and `Shoe Box (Playlists) 📦` -> individual cassette albums/playlists -> playable tracks. Selecting a track on the car touchscreen triggers headless background playback for both local and Spotify cassettes.

### G. Strict Invariant: Authentic Playback Only (No 30-Second Previews, No SoundCloud Fallbacks, No Masking Failures)
- **ZERO 30-Second Snippets**: Never fall back to Spotify embed `audioPreview`, iTunes `previewUrl`, or any truncated 30-second audio files.
- **ZERO SoundCloud / Third-Party Cover Fallbacks**: Never scrape or fuzzy-match tracks against SoundCloud, YouTube, JioSaavn, or any third-party engine. Every Spotify track MUST play the genuine track from Spotify.
- **Fail Honestly & Transparently**: Never conceal a playback or connection failure by quietly substituting inferior audio. If App Remote or Spotify Connect is disconnected or requires authorization, display the status honestly to the user (e.g., on the LCD screen or in the Spotify Dialog) so they can reconnect or authorize.
- **Headless & Background Invariant**: Never bring the Spotify app UI to the foreground (`startActivity` targeting `com.spotify.music` with `ACTION_VIEW` is strictly forbidden). Connection must always bind headlessly in the background (`showAuthView = false`).

### H. Never Dispatch Unsolicited Media Key Broadcasts (`KEYCODE_MEDIA_PLAY`) on Connect, Resume, or Screen Wake
- **The "Pocket Playback" Bug**:
  - Never broadcast `Intent.ACTION_MEDIA_BUTTON` with `KEYCODE_MEDIA_PLAY` or call `AudioManager.dispatchMediaKeyEvent()` as a "wake-up" heuristic during app startup, lifecycle `ON_RESUME`, or IPC connection checks.
  - Doing so causes Android and background media services (like Spotify) to interpret the key event as an unconditional user command to immediately blast music out loud.
  - When the user locks/unlocks their phone, puts it in their pocket, or switches back to Sunset, `ON_RESUME` executes. If a play broadcast is emitted while App Remote IPC is unauthenticated or still binding, Spotify plays audio independently in the pocket while Sunset's LCD remains disconnected and blank.
- **Strict Read-Only Connection Rule**:
  - `SpotifyRemoteManager.connect()` and `refreshState()` must remain 100% read-only IPC queries via `SpotifyAppRemote.connect()`.
  - Playback commands must **only** be issued in response to direct, explicit user actions (pressing the physical Walkman **PLAY** button or tapping a specific track in the cassette tracklist).


### I. Android Studio Visual XML Layout Pipeline (`res/layout/activity_deck.xml` & `res/layout-land/activity_deck.xml`)
> [!CAUTION]
> **XML LAYOUT FILES ARE COMPLETELY UNTOUCHABLE**: Never, under any circumstances, edit, format, modify, or rewrite `res/layout/activity_deck.xml`, `res/layout-land/activity_deck.xml`, or any XML layout file unless the user explicitly and specifically instructs you to edit an XML file. All positioning, sizing, and styling in XML belongs 100% to the user in Android Studio.

1. **The Native Layout Editor is the Source of Truth**:
   - UI layout and visual placement are designed natively in Android Studio's built-in Visual Layout Editor (`Design` / `Split` view).
   - Layout files:
     - Portrait: `app/src/main/res/layout/activity_deck.xml`
     - Landscape: `app/src/main/res/layout-land/activity_deck.xml`
   - Both layouts use `RelativeLayout` configured with `tools:device="id:pixel_8"` for 1:1 match with real device dimensions (412×915 dp).
   - The former custom browser tool (`tools/skin_editor/index.html`) and `default_skin.json` are superseded by this native Android Studio workflow. Never regenerate or rely on `default_skin.json`.
2. **Runtime Coordinate Extraction (`SkinData.kt`)**:
   - `SkinManager.loadSkin(context)` inflates `R.layout.activity_deck` dynamically under Portrait (`Configuration.ORIENTATION_PORTRAIT`) and Landscape (`Configuration.ORIENTATION_LANDSCAPE`) contexts.
   - For every child view, `SkinData.kt` extracts:
     - Exact DP dimensions: `width` and `height` (from `lp.width / density` and `lp.height / density`).
     - Positioning: `x` and `y` DP coordinates, respecting `layout_marginStart`, `layout_marginTop`, and `layout_alignParentEnd` (`canvasW - marginEnd - width`).
     - Fractional viewport coordinates (`relX, relY, relWidth, relHeight`) for proportional scaling across diverse screen resolutions.
   - Dual-alias support: `@+id/cassette_bay` and `@+id/cassette_door` map to the same element for backwards compatibility.
3. **Transport Button Handling & Dual-Purpose Stop/Eject**:
   - Each transport button (`btn_skip_prev`, `btn_rewind`, `btn_play`, `btn_fast_fwd`, `btn_skip_next`, `btn_stop`, `btn_eject`) is positioned strictly by its individual XML coordinates.
   - **Colocated Stop/Eject**: If `btn_stop` and `btn_eject` are placed at the same coordinates in the XML layout, the player treats them as the authentic Walkman dual-purpose mechanical button: displaying `STOP` during playback (`onStop`), and swapping to `EJECT` when stopped (`onEject`), preventing duplicate or overlapping buttons. If positioned distinctly (> 20dp apart), both render independently.
4. **Strict Visibility-Only Toggling (Zero Code-Based Positioning or Rescaling)**:
   - **Never touch or edit the user's XML layout files** (`activity_deck.xml`). All visual placement, margins, widths, and heights belong solely to the designer in Android Studio's Visual Editor.
   - **Zero Code-Based Offsetting**: Never hardcode offsets, translations, or scale factors in Kotlin. Every element (`deck_door_open`, `deck_door_closed`, `cassette_bay`, buttons) is placed strictly at its exact XML coordinates.
   - **Pure Visibility Switching**: State changes (playing, paused, stopped, ejected) are handled purely by toggling the visibility of the corresponding XML elements (`deck_door_closed` visible when playing/inserted; `deck_door_open` visible when open/ejected). The open door appears exactly where and how it was sized in the editor without any movement or resizing.

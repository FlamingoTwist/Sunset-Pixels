package com.retro.pixelplayer

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.retro.pixelplayer.R
import com.retro.pixelplayer.audio.LocalMusicScanner
import com.retro.pixelplayer.audio.TapeItem
import com.retro.pixelplayer.audio.TapeType
import com.retro.pixelplayer.audio.Track
import com.retro.pixelplayer.feedback.MechanicalFeedback
import com.retro.pixelplayer.spotify.SpotifyManager
import com.retro.pixelplayer.ui.components.CassetteDeck
import com.retro.pixelplayer.ui.components.LcdDisplay
import com.retro.pixelplayer.ui.components.MechanicalButton
import com.retro.pixelplayer.ui.components.PixelArtButton
import com.retro.pixelplayer.ui.components.PixelArtSwitch
import com.retro.pixelplayer.ui.components.SpotifyAuthDialog
import com.retro.pixelplayer.ui.components.TapeSelectionScreen
import com.retro.pixelplayer.ui.theme.PixelPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CurrentAppView {
    DECK,           // The physical cassette tape player
    TAPE_SELECTION  // The Wooden Shelf / Cardboard Shoe Box
}

class MainActivity : ComponentActivity() {

    private lateinit var feedback: MechanicalFeedback
    private var incomingAuthIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
        feedback = MechanicalFeedback(this)
        incomingAuthIntent = intent

        setContent {
            RetroAppRoot(
                feedback = feedback,
                authIntent = incomingAuthIntent,
                onAuthIntentConsumed = { incomingAuthIntent = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingAuthIntent = intent
    }
}

@Composable
fun RetroAppRoot(
    feedback: MechanicalFeedback,
    authIntent: Intent? = null,
    onAuthIntentConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Local music library
    val allTracks = remember { LocalMusicScanner.loadLocalTracks(context) }
    val localArtistTapes = remember { LocalMusicScanner.buildArtistTapes(context, allTracks) }
    val localPlaylistTapes = remember { LocalMusicScanner.buildPlaylistTapes(allTracks) }

    // Spotify state & synced tapes
    var isSpotifyLoggedIn by remember { mutableStateOf(SpotifyManager.isLoggedIn(context)) }
    var spotifyUserName by remember { mutableStateOf(SpotifyManager.getSavedUserName(context)) }
    var spotifySyncStatus by remember {
        mutableStateOf(
            if (isSpotifyLoggedIn) "READY TO SYNC SPOTIFY TAPES" else "LOG IN TO IMPORT YOUR SPOTIFY TAPES"
        )
    }
    var isSpotifySyncing by remember { mutableStateOf(false) }
    var showSpotifyDialog by remember { mutableStateOf(false) }

    var spotifyArtistTapes by remember { mutableStateOf<Map<String, List<TapeItem>>>(emptyMap()) }
    var spotifyPlaylistTapes by remember { mutableStateOf<List<TapeItem>>(emptyList()) }

    // Combined Local + Spotify Tapes for the Shelf & Shoe Box
    val artistTapes = remember(localArtistTapes, spotifyArtistTapes) {
        val merged = mutableMapOf<String, List<TapeItem>>()
        merged.putAll(localArtistTapes)
        spotifyArtistTapes.forEach { (artist, tapes) ->
            val existing = merged[artist] ?: emptyList()
            merged[artist] = existing + tapes
        }
        merged
    }

    val playlistTapes = remember(localPlaylistTapes, spotifyPlaylistTapes) {
        spotifyPlaylistTapes + localPlaylistTapes
    }

    fun triggerSpotifySync() {
        if (isSpotifySyncing) return
        isSpotifySyncing = true
        coroutineScope.launch {
            val res = SpotifyManager.syncSpotifyLibrary(context)
            isSpotifySyncing = false
            res.onSuccess { syncData ->
                spotifyUserName = syncData.userName
                spotifyArtistTapes = syncData.artistTapes
                spotifyPlaylistTapes = syncData.playlistTapes
                spotifySyncStatus = syncData.statusMessage
            }.onFailure { err ->
                spotifySyncStatus = "SYNC ERROR: ${err.message?.take(36)}"
            }
        }
    }

    // Handle OAuth 2.0 PKCE Callback from pixelplayer://spotify-callback
    LaunchedEffect(authIntent) {
        val data = authIntent?.data
        if (data != null && data.scheme == "pixelplayer" && data.host == "spotify-callback") {
            showSpotifyDialog = true
            isSpotifySyncing = true
            spotifySyncStatus = "VERIFYING SPOTIFY LOGIN..."
            val result = SpotifyManager.handleAuthCallback(context, authIntent)
            onAuthIntentConsumed()
            result.onSuccess { name ->
                isSpotifyLoggedIn = true
                spotifyUserName = name
                spotifySyncStatus = "LINKED! FETCHING TAPES..."
                triggerSpotifySync()
            }.onFailure { err ->
                isSpotifySyncing = false
                spotifySyncStatus = "AUTH FAILED: ${err.message?.take(38)}"
            }
        }
    }

    // Auto-sync Spotify tapes on startup if already logged in
    LaunchedEffect(Unit) {
        if (isSpotifyLoggedIn) {
            triggerSpotifySync()
        }
    }

    // Active loaded Tape
    var currentTape by remember {
        mutableStateOf<TapeItem?>(
            playlistTapes.firstOrNull() ?: artistTapes.values.firstOrNull()?.firstOrNull()
        )
    }

    var activeTrackIndex by remember { mutableIntStateOf(0) }
    var currentScreen by remember { mutableStateOf(CurrentAppView.DECK) }

    // ExoPlayer engine
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    var isTapeInserted by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isSpotifyRemotePlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    val activeTracks = currentTape?.tracks ?: emptyList()
    val activeTrack: Track? = if (activeTracks.isNotEmpty()) {
        activeTracks[activeTrackIndex % activeTracks.size]
    } else {
        null
    }

    // Handle Android system back button
    BackHandler(enabled = currentScreen == CurrentAppView.TAPE_SELECTION) {
        currentScreen = CurrentAppView.DECK
    }

    // Listen to ExoPlayer playback events
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing || isSpotifyRemotePlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Timer updater coroutine for LCD display (works for both ExoPlayer and Spotify Connect)
    LaunchedEffect(isPlaying, isSpotifyRemotePlaying) {
        while (isPlaying) {
            if (isSpotifyRemotePlaying && !exoPlayer.isPlaying) {
                delay(1000)
                if (isPlaying && !isPaused) {
                    currentPositionMs = (currentPositionMs + 1000L).coerceAtMost(totalDurationMs)
                }
            } else {
                currentPositionMs = exoPlayer.currentPosition
                totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                delay(400)
            }
        }
    }

    fun playTrack(track: Track?) {
        if (track == null || !isTapeInserted) return
        val scheme = track.contentUri.scheme ?: ""

        if (track.spotifyUri != null) {
            // Send command to Spotify Connect in background
            coroutineScope.launch {
                SpotifyManager.playSpotifyTrack(context, track.spotifyUri)
            }
        }

        if (scheme == "http" || scheme == "https" || scheme == "content" || scheme == "file") {
            isSpotifyRemotePlaying = false
            val mediaItem = MediaItem.fromUri(track.contentUri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            isPaused = false
        } else if (track.spotifyUri != null) {
            // Pure Spotify Connect track without direct HTTP stream
            exoPlayer.stop()
            isSpotifyRemotePlaying = true
            isPlaying = true
            isPaused = false
            currentPositionMs = 0L
            totalDurationMs = track.durationMs.coerceAtLeast(180000L)
        }
    }

    if (showSpotifyDialog) {
        SpotifyAuthDialog(
            isLoggedIn = isSpotifyLoggedIn,
            userName = spotifyUserName,
            syncStatus = spotifySyncStatus,
            isSyncing = isSpotifySyncing,
            feedback = feedback,
            onLoginClicked = { clientId ->
                SpotifyManager.startLogin(context, clientId)
            },
            onSyncClicked = {
                triggerSpotifySync()
            },
            onLogoutClicked = {
                SpotifyManager.logout(context)
                isSpotifyLoggedIn = false
                spotifyArtistTapes = emptyMap()
                spotifyPlaylistTapes = emptyList()
                spotifySyncStatus = "LOGGED OUT OF SPOTIFY"
            },
            onDismiss = {
                showSpotifyDialog = false
            }
        )
    }

    // Animated view switcher between Tape Deck and Tape Shelf
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState == CurrentAppView.TAPE_SELECTION) {
                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> -width } + fadeOut()
                )
            } else {
                (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> width } + fadeOut()
                )
            }
        },
        label = "ScreenTransition"
    ) { screen ->
        when (screen) {
            CurrentAppView.TAPE_SELECTION -> {
                TapeSelectionScreen(
                    artistTapes = artistTapes,
                    playlistTapes = playlistTapes,
                    feedback = feedback,
                    isSpotifyLoggedIn = isSpotifyLoggedIn,
                    onSpotifyClicked = { showSpotifyDialog = true },
                    onTapeSelected = { selectedTape ->
                        currentTape = selectedTape
                        activeTrackIndex = 0
                        isTapeInserted = true
                        currentScreen = CurrentAppView.DECK

                        // Auto-cue first track
                        selectedTape.tracks.firstOrNull()?.let { firstTrack ->
                            playTrack(firstTrack)
                        }
                    },
                    onBackToPlayer = {
                        currentScreen = CurrentAppView.DECK
                    }
                )
            }

            CurrentAppView.DECK -> {
                DeckScreen(
                    currentTape = currentTape,
                    activeTrack = activeTrack,
                    activeTrackIndex = activeTrackIndex,
                    totalTracksInTape = activeTracks.size,
                    isTapeInserted = isTapeInserted,
                    isPlaying = isPlaying,
                    isPaused = isPaused,
                    isSpotifyLoggedIn = isSpotifyLoggedIn,
                    currentPositionMs = currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    feedback = feedback,
                    onSpotifyClicked = { showSpotifyDialog = true },
                    onPlay = {
                        if (isTapeInserted) {
                            if (isPaused) {
                                if (isSpotifyRemotePlaying) {
                                    isPlaying = true
                                    isPaused = false
                                    activeTrack?.spotifyUri?.let { uri ->
                                        coroutineScope.launch { SpotifyManager.playSpotifyTrack(context, uri) }
                                    }
                                } else {
                                    exoPlayer.play()
                                    isPaused = false
                                }
                            } else {
                                playTrack(activeTrack)
                            }
                        }
                    },
                    onPause = {
                        if (isPlaying) {
                            if (isSpotifyRemotePlaying) {
                                isPlaying = false
                                isPaused = true
                                coroutineScope.launch { SpotifyManager.pauseSpotify(context) }
                            } else {
                                exoPlayer.pause()
                                isPaused = true
                            }
                        }
                    },
                    onStop = {
                        if (isSpotifyRemotePlaying) {
                            coroutineScope.launch { SpotifyManager.pauseSpotify(context) }
                        }
                        isSpotifyRemotePlaying = false
                        isPlaying = false
                        exoPlayer.stop()
                        exoPlayer.seekTo(0)
                        isPaused = false
                        currentPositionMs = 0L
                    },
                    onSkipPrev = {
                        if (activeTracks.isNotEmpty()) {
                            activeTrackIndex = if (activeTrackIndex - 1 < 0) activeTracks.size - 1 else activeTrackIndex - 1
                            val prevTrack = activeTracks[activeTrackIndex]
                            if (isPlaying) {
                                playTrack(prevTrack)
                            }
                        }
                    },
                    onSkip = {
                        if (activeTracks.isNotEmpty()) {
                            activeTrackIndex = (activeTrackIndex + 1) % activeTracks.size
                            val nextTrack = activeTracks[activeTrackIndex]
                            if (isPlaying) {
                                playTrack(nextTrack)
                            }
                        }
                    },
                    onEject = {
                        feedback.onTapeLatch()
                        if (isSpotifyRemotePlaying) {
                            coroutineScope.launch { SpotifyManager.pauseSpotify(context) }
                        }
                        isSpotifyRemotePlaying = false
                        isPlaying = false
                        isTapeInserted = false
                        exoPlayer.stop()
                        isPaused = false
                        currentPositionMs = 0L
                        // Switch immediately to tape collection!
                        currentScreen = CurrentAppView.TAPE_SELECTION
                    },
                    onTapeDeckTapped = {
                        feedback.onTapeLatch()
                        if (isTapeInserted) {
                            if (isSpotifyRemotePlaying) {
                                coroutineScope.launch { SpotifyManager.pauseSpotify(context) }
                            }
                            isSpotifyRemotePlaying = false
                            isPlaying = false
                            isTapeInserted = false
                            exoPlayer.stop()
                            isPaused = false
                        }
                        currentScreen = CurrentAppView.TAPE_SELECTION
                    },
                    onBrowseTapesClicked = {
                        feedback.onTick()
                        currentScreen = CurrentAppView.TAPE_SELECTION
                    },
                    onShuffleToggle = { enabled ->
                        exoPlayer.shuffleModeEnabled = enabled
                        if (currentTape?.isSpotify == true) {
                            coroutineScope.launch { SpotifyManager.setSpotifyShuffle(context, enabled) }
                        }
                    },
                    onRepeatToggle = { enabled ->
                        exoPlayer.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
                        if (currentTape?.isSpotify == true) {
                            coroutineScope.launch { SpotifyManager.setSpotifyRepeat(context, enabled) }
                        }
                    },
                    onSeek = { targetMs ->
                        exoPlayer.seekTo(targetMs)
                        currentPositionMs = targetMs
                        if (isSpotifyRemotePlaying) {
                            coroutineScope.launch { SpotifyManager.seekSpotify(context, targetMs) }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DeckScreen(
    currentTape: TapeItem?,
    activeTrack: Track?,
    activeTrackIndex: Int,
    totalTracksInTape: Int,
    isTapeInserted: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    isSpotifyLoggedIn: Boolean = false,
    currentPositionMs: Long,
    totalDurationMs: Long,
    feedback: MechanicalFeedback,
    onSpotifyClicked: () -> Unit = {},
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkip: () -> Unit,
    onEject: () -> Unit,
    onTapeDeckTapped: () -> Unit,
    onBrowseTapesClicked: () -> Unit,
    onShuffleToggle: (Boolean) -> Unit,
    onRepeatToggle: (Boolean) -> Unit,
    onSeek: (Long) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isShuffleOn by remember { mutableStateOf(false) }
    var isRepeatOn by remember { mutableStateOf(false) }

    val isTapeRunning = isPlaying || isPaused

    val tapeTitle = when {
        !isTapeInserted -> "NO CASSETTE"
        currentTape != null -> currentTape.title
        else -> "EMPTY TAPE"
    }

    val tapeSubtitle = when {
        !isTapeInserted -> "TAP DECK TO BROWSE"
        currentTape?.isHomeMade == true -> "HOMEMADE PLAYLIST"
        currentTape?.type == TapeType.ARTIST_ALL -> "ARTIST DISCOGRAPHY"
        currentTape?.type == TapeType.ALBUM -> "OFFICIAL ALBUM TAPE"
        else -> "LOCAL TAPE"
    }

    val trackTitle = when {
        !isTapeInserted -> "PLEASE INSERT TAPE"
        activeTrack != null -> activeTrack.title
        else -> "EMPTY C-90"
    }

    val artistText = when {
        !isTapeInserted -> "----"
        activeTrack != null -> activeTrack.artist
        else -> "UNKNOWN"
    }

    val statusText = when {
        !isTapeInserted -> "EJECTED"
        isPlaying -> "PLAYING"
        isPaused -> "PAUSED"
        else -> "STOPPED"
    }

    val formattedTime = if (isTapeInserted && (isPlaying || isPaused)) {
        val posSec = (currentPositionMs / 1000) % 60
        val posMin = (currentPositionMs / 1000) / 60
        val durSec = (totalDurationMs / 1000) % 60
        val durMin = (totalDurationMs / 1000) / 60
        String.format("%02d:%02d / %02d:%02d", posMin, posSec, durMin, durSec)
    } else {
        "--:-- / --:--"
    }

    val tapeLabelColor = currentTape?.let { Color(it.labelColorHex) } ?: PixelPalette.TapeLabelPink
    val tapeShellColor = if (currentTape?.isHomeMade == true) Color(0xFFF5F5DC) else Color(0xFFDC7828)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PixelPalette.RoomDark
    ) {
        if (isLandscape) {
            // HORIZONTAL (Landscape) Layout - Exact match to user's master layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "◆ SUNSET PIXELS ◆",
                        color = PixelPalette.ChassisHighlight,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MechanicalButton(
                            label = if (isSpotifyLoggedIn) "🟢 LINKED" else "🟢 SPOTIFY",
                            baseColor = if (isSpotifyLoggedIn) Color(0xFF1DB954) else Color(0xFF1B5E20),
                            highlightColor = if (isSpotifyLoggedIn) Color(0xFF4ADE80) else Color(0xFF2E7D32),
                            shadowColor = Color(0xFF0A2910),
                            labelColor = if (isSpotifyLoggedIn) Color(0xFF0A140E) else Color(0xFFE0E0E0),
                            width = 88.dp,
                            height = 26.dp,
                            onDownPress = { feedback.onTick() },
                            onClick = { onSpotifyClicked() }
                        )

                        MechanicalButton(
                            label = "TAPES 📚",
                            baseColor = Color(0xFF4E342E),
                            highlightColor = Color(0xFF6D4C41),
                            shadowColor = Color(0xFF3E2723),
                            width = 82.dp,
                            height = 26.dp,
                            onDownPress = { feedback.onTick() },
                            onClick = { onBrowseTapesClicked() }
                        )
                    }
                }

                // Main Chassis Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PixelPalette.ChassisBody)
                        .border(5.dp, PixelPalette.ChassisBorder, RoundedCornerShape(16.dp))
                        .padding(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.chassis_texture_bg),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Half: Cassette Door + Speaker Vents
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            CassetteDeck(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1024f / 593f),
                                isTapeInserted = isTapeInserted,
                                isPlaying = isPlaying,
                                tapeTitle = tapeTitle,
                                tapeSubtitle = tapeSubtitle,
                                labelColor = tapeLabelColor,
                                shellColor = tapeShellColor,
                                isHomeMade = currentTape?.isHomeMade ?: false,
                                artworkBitmap = currentTape?.artworkBitmap,
                                onEjectOrInsert = { onTapeDeckTapped() }
                            )

                            Image(
                                painter = painterResource(id = R.drawable.chassis_speaker_vents),
                                contentDescription = "Speaker Vents",
                                modifier = Modifier
                                    .size(width = 120.dp, height = 28.dp)
                                    .padding(top = 4.dp)
                            )
                        }

                        // Right Half: LCD Display, Transport Buttons with Labels, and Switches
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            LcdDisplay(
                                trackTitle = trackTitle,
                                artist = artistText,
                                source = when {
                                    currentTape?.isSpotify == true -> "SPOTIFY"
                                    currentTape?.isHomeMade == true -> "MIXTAPE"
                                    else -> "ALBUM"
                                },
                                timeText = formattedTime,
                                statusText = statusText
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                TransportButtonLabelsRow(isTapeRunning = isTapeRunning)

                                TransportButtonsRow(
                                    isPlaying = isPlaying,
                                    isTapeRunning = isTapeRunning,
                                    currentPositionMs = currentPositionMs,
                                    totalDurationMs = totalDurationMs,
                                    feedback = feedback,
                                    onPlay = onPlay,
                                    onStop = onStop,
                                    onSkipPrev = onSkipPrev,
                                    onSkip = onSkip,
                                    onEject = onEject,
                                    onSeek = onSeek
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SwitchesRow(
                                    isShuffleOn = isShuffleOn,
                                    isRepeatOn = isRepeatOn,
                                    feedback = feedback,
                                    onShuffleToggle = {
                                        isShuffleOn = !isShuffleOn
                                        onShuffleToggle(isShuffleOn)
                                    },
                                    onRepeatToggle = {
                                        isRepeatOn = !isRepeatOn
                                        onRepeatToggle(isRepeatOn)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // VERTICAL (Portrait) Layout - Modular stack of authentic sections
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "◆ SUNSET PIXELS ◆",
                        color = PixelPalette.ChassisHighlight,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MechanicalButton(
                            label = if (isSpotifyLoggedIn) "🟢 LINKED" else "🟢 SPOTIFY",
                            baseColor = if (isSpotifyLoggedIn) Color(0xFF1DB954) else Color(0xFF1B5E20),
                            highlightColor = if (isSpotifyLoggedIn) Color(0xFF4ADE80) else Color(0xFF2E7D32),
                            shadowColor = Color(0xFF0A2910),
                            labelColor = if (isSpotifyLoggedIn) Color(0xFF0A140E) else Color(0xFFE0E0E0),
                            width = 86.dp,
                            height = 30.dp,
                            onDownPress = { feedback.onTick() },
                            onClick = { onSpotifyClicked() }
                        )

                        MechanicalButton(
                            label = "TAPES 📚",
                            baseColor = Color(0xFF4E342E),
                            highlightColor = Color(0xFF6D4C41),
                            shadowColor = Color(0xFF3E2723),
                            width = 80.dp,
                            height = 30.dp,
                            onDownPress = { feedback.onTick() },
                            onClick = { onBrowseTapesClicked() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Distressed Orange Walkman Chassis
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(PixelPalette.ChassisBody)
                        .border(5.dp, PixelPalette.ChassisBorder, RoundedCornerShape(18.dp))
                        .padding(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.chassis_texture_bg),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Cassette Door with AUTO REVERSE and warm top lamp
                        CassetteDeck(
                            isTapeInserted = isTapeInserted,
                            isPlaying = isPlaying,
                            tapeTitle = tapeTitle,
                            tapeSubtitle = tapeSubtitle,
                            labelColor = tapeLabelColor,
                            shellColor = tapeShellColor,
                            isHomeMade = currentTape?.isHomeMade ?: false,
                            artworkBitmap = currentTape?.artworkBitmap,
                            onEjectOrInsert = { onTapeDeckTapped() }
                        )

                        // Authentic Mint-Green LCD Screen
                        LcdDisplay(
                            trackTitle = trackTitle,
                            artist = artistText,
                            source = when {
                                currentTape?.isSpotify == true -> "SPOTIFY"
                                currentTape?.isHomeMade == true -> "MIXTAPE"
                                else -> "ALBUM"
                            },
                            timeText = formattedTime,
                            statusText = statusText
                        )

                        // Buttons section: Amber labels row + 6 buttons
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            TransportButtonLabelsRow(isTapeRunning = isTapeRunning)

                            TransportButtonsRow(
                                isPlaying = isPlaying,
                                isTapeRunning = isTapeRunning,
                                currentPositionMs = currentPositionMs,
                                totalDurationMs = totalDurationMs,
                                feedback = feedback,
                                onPlay = onPlay,
                                onStop = onStop,
                                onSkipPrev = onSkipPrev,
                                onSkip = onSkip,
                                onEject = onEject,
                                onSeek = onSeek
                            )
                        }

                        // Bottom Row: Speaker Vents on left, Rocker Switches on right
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.chassis_speaker_vents),
                                contentDescription = "Speaker Vents",
                                modifier = Modifier.size(width = 110.dp, height = 28.dp)
                            )

                            SwitchesRow(
                                isShuffleOn = isShuffleOn,
                                isRepeatOn = isRepeatOn,
                                feedback = feedback,
                                onShuffleToggle = {
                                    isShuffleOn = !isShuffleOn
                                    onShuffleToggle(isShuffleOn)
                                },
                                onRepeatToggle = {
                                    isRepeatOn = !isRepeatOn
                                    onRepeatToggle(isRepeatOn)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Helpful vintage tape instructions
                Text(
                    text = if (isTapeRunning) {
                        "■ Press STOP to halt the tape. Press EJECT ⏏ when stopped to browse tapes!"
                    } else {
                        "⏏ Press EJECT to open the Shelf & Shoe Box.\nSelect an Artist or Playlist to load their tape!"
                    },
                    color = Color.White.copy(alpha = 0.55f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TransportButtonLabelsRow(isTapeRunning: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = "SKIP",
            modifier = Modifier.width(46.dp),
            textAlign = TextAlign.Center,
            color = PixelPalette.ButtonLabelAmber,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp
        )
        Text(
            text = "REWIND",
            modifier = Modifier.width(46.dp),
            textAlign = TextAlign.Center,
            color = PixelPalette.ButtonLabelAmber,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp
        )
        Text(
            text = "PLAY",
            modifier = Modifier.width(58.dp),
            textAlign = TextAlign.Center,
            color = PixelPalette.ButtonLabelAmber,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp
        )
        Text(
            text = "FAST\nFWD",
            modifier = Modifier.width(46.dp),
            textAlign = TextAlign.Center,
            color = PixelPalette.ButtonLabelAmber,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 7.sp,
            lineHeight = 8.sp
        )
        Text(
            text = "SKIP\nFWD",
            modifier = Modifier.width(46.dp),
            textAlign = TextAlign.Center,
            color = PixelPalette.ButtonLabelAmber,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 7.sp,
            lineHeight = 8.sp
        )
        Text(
            text = if (isTapeRunning) "STOP" else "EJECT",
            modifier = Modifier.width(45.dp),
            textAlign = TextAlign.Center,
            color = PixelPalette.ButtonLabelAmber,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp
        )
    }
}

@Composable
private fun TransportButtonsRow(
    isPlaying: Boolean,
    isTapeRunning: Boolean,
    currentPositionMs: Long,
    totalDurationMs: Long,
    feedback: MechanicalFeedback,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkip: () -> Unit,
    onEject: () -> Unit,
    onSeek: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. SKIP PREV (|◀◀)
        PixelArtButton(
            unpressedResId = R.drawable.btn_skip_prev_unpressed,
            pressedResId = R.drawable.btn_skip_prev_pressed,
            width = 46.dp,
            height = 50.dp,
            onDownPress = { feedback.onTick() },
            onClick = { onSkipPrev() }
        )

        // 2. REWIND (◀◀)
        PixelArtButton(
            unpressedResId = R.drawable.btn_rewind_unpressed,
            pressedResId = R.drawable.btn_rewind_pressed,
            width = 46.dp,
            height = 50.dp,
            onDownPress = { feedback.onRewindPress() },
            onClick = {
                val newPos = (currentPositionMs - 5000L).coerceAtLeast(0L)
                onSeek(newPos)
            }
        )

        // 3. PLAY (▶) - latched down when playing!
        PixelArtButton(
            unpressedResId = R.drawable.btn_play_unpressed,
            pressedResId = R.drawable.btn_play_pressed,
            isLatched = isPlaying,
            width = 58.dp,
            height = 51.dp,
            onDownPress = { feedback.onPlayPress() },
            onClick = { onPlay() }
        )

        // 4. FAST FORWARD (▶▶)
        PixelArtButton(
            unpressedResId = R.drawable.btn_fast_fwd_unpressed,
            pressedResId = R.drawable.btn_fast_fwd_pressed,
            width = 46.dp,
            height = 50.dp,
            onDownPress = { feedback.onRewindPress() },
            onClick = {
                val newPos = (currentPositionMs + 5000L).coerceAtMost(totalDurationMs)
                onSeek(newPos)
            }
        )

        // 5. SKIP NEXT (▶▶|)
        PixelArtButton(
            unpressedResId = R.drawable.btn_skip_next_unpressed,
            pressedResId = R.drawable.btn_skip_next_pressed,
            width = 46.dp,
            height = 50.dp,
            onDownPress = { feedback.onTick() },
            onClick = { onSkip() }
        )

        // 6. STOP (■) when running / EJECT (⏏) when stopped!
        if (isTapeRunning) {
            PixelArtButton(
                unpressedResId = R.drawable.btn_stop_unpressed,
                pressedResId = R.drawable.btn_stop_pressed,
                width = 45.dp,
                height = 50.dp,
                onDownPress = { feedback.onStopPress() },
                onClick = { onStop() }
            )
        } else {
            PixelArtButton(
                unpressedResId = R.drawable.btn_eject_unpressed,
                pressedResId = R.drawable.btn_eject_pressed,
                width = 45.dp,
                height = 50.dp,
                onDownPress = { feedback.onTapeLatch() },
                onClick = { onEject() }
            )
        }
    }
}

@Composable
private fun SwitchesRow(
    isShuffleOn: Boolean,
    isRepeatOn: Boolean,
    feedback: MechanicalFeedback,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // SHUFFLE SWITCH
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "SHUFFLE",
                color = PixelPalette.ButtonLabelAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp
            )
            PixelArtSwitch(
                isOn = isShuffleOn,
                offResId = R.drawable.switch_shuffle_off,
                onResId = R.drawable.switch_shuffle_on,
                width = 44.dp,
                height = 23.dp,
                onToggle = {
                    feedback.onTick()
                    onShuffleToggle()
                }
            )
        }

        // REPEAT SWITCH
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "REPEAT",
                color = PixelPalette.ButtonLabelAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp
            )
            PixelArtSwitch(
                isOn = isRepeatOn,
                offResId = R.drawable.switch_repeat_off,
                onResId = R.drawable.switch_repeat_on,
                width = 44.dp,
                height = 23.dp,
                onToggle = {
                    feedback.onTick()
                    onRepeatToggle()
                }
            )
        }
    }
}


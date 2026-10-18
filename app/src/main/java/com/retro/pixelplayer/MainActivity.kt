package com.retro.pixelplayer

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.tooling.preview.Preview
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
import com.retro.pixelplayer.spotify.SpotifyRemoteManager
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

    // Load cached Spotify library synchronously on frame 1 so cassettes are populated immediately on reopen!
    val initialSpotifyCache = remember { SpotifyManager.loadCachedLibrary(context) }

    var isSpotifyLoggedIn by remember { mutableStateOf(SpotifyManager.isLoggedIn(context)) }
    var spotifyUserName by remember {
        mutableStateOf(initialSpotifyCache?.userName ?: SpotifyManager.getSavedUserName(context))
    }
    var spotifySyncStatus by remember {
        mutableStateOf(
            initialSpotifyCache?.statusMessage
                ?: if (isSpotifyLoggedIn) "READY TO SYNC SPOTIFY TAPES" else "LOG IN TO IMPORT YOUR SPOTIFY TAPES"
        )
    }
    var isSpotifySyncing by remember { mutableStateOf(false) }
    var showSpotifyDialog by remember { mutableStateOf(false) }

    var spotifyArtistTapes by remember {
        mutableStateOf<Map<String, List<TapeItem>>>(initialSpotifyCache?.artistTapes ?: emptyMap())
    }
    var spotifyPlaylistTapes by remember {
        mutableStateOf<List<TapeItem>>(initialSpotifyCache?.playlistTapes ?: emptyList())
    }

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

    // Active loaded Tape (restores last selected tape from SharedPreferences if available)
    var currentTape by remember {
        val savedId = SpotifyManager.getLastTapeId(context)
        val allAvailable = playlistTapes + artistTapes.values.flatten()
        val matched = if (savedId != null) allAvailable.firstOrNull { it.id == savedId } else null
        mutableStateOf<TapeItem?>(
            matched ?: playlistTapes.firstOrNull() ?: artistTapes.values.firstOrNull()?.firstOrNull()
        )
    }

    fun triggerSpotifySync() {
        if (isSpotifySyncing) return
        isSpotifySyncing = true
        spotifySyncStatus = "CONNECTING TO SPOTIFY..."
        coroutineScope.launch {
            val res = SpotifyManager.syncSpotifyLibrary(
                context = context,
                onProgress = { progressMsg ->
                    spotifySyncStatus = progressMsg
                }
            )
            isSpotifySyncing = false
            res.onSuccess { syncData ->
                spotifyUserName = syncData.userName
                spotifyArtistTapes = syncData.artistTapes
                spotifyPlaylistTapes = syncData.playlistTapes
                spotifySyncStatus = syncData.statusMessage

                // If the user had a Spotify tape selected before closing the app (or no tape yet), restore/update it
                val savedId = SpotifyManager.getLastTapeId(context)
                val allSynced = syncData.playlistTapes + syncData.artistTapes.values.flatten()
                if (savedId != null) {
                    allSynced.firstOrNull { it.id == savedId }?.let { currentTape = it }
                } else if (currentTape == null) {
                    currentTape = syncData.playlistTapes.firstOrNull()
                }
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
                isSpotifySyncing = false
                triggerSpotifySync()
            }.onFailure { err ->
                isSpotifySyncing = false
                spotifySyncStatus = "AUTH FAILED: ${err.message?.take(38)}"
            }
        }
    }

    // Background refresh of Spotify tapes on startup if logged in
    LaunchedEffect(Unit) {
        if (isSpotifyLoggedIn) {
            triggerSpotifySync()
        }
    }

    var activeTrackIndex by remember { mutableIntStateOf(0) }
    var currentScreen by remember { mutableStateOf(CurrentAppView.DECK) }

    // ExoPlayer engine configured with custom HTTP User-Agent for full-length audio streams
    val exoPlayer = remember {
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(SpotifyManager.STREAM_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            context,
            httpDataSourceFactory
        )
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    var isTapeInserted by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isSpotifyRemotePlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }

    val spotifyPlayerState by SpotifyRemoteManager.playerState.collectAsState()
    val isSpotifyRemoteConnected by SpotifyRemoteManager.isConnected.collectAsState()

    // Eagerly connect to Spotify App Remote if Spotify is logged in and installed
    DisposableEffect(isSpotifyLoggedIn) {
        if (isSpotifyLoggedIn && SpotifyRemoteManager.isInstalled(context)) {
            SpotifyRemoteManager.connect(context)
        }
        onDispose {
            SpotifyRemoteManager.disconnect()
        }
    }

    val activeTracks = currentTape?.tracks ?: emptyList()
    val activeTrack: Track? = if (activeTracks.isNotEmpty()) {
        activeTracks[activeTrackIndex % activeTracks.size]
    } else {
        null
    }

    // React to authoritative playback updates from the Spotify App Remote SDK
    LaunchedEffect(spotifyPlayerState) {
        val state = spotifyPlayerState ?: return@LaunchedEffect
        if (isSpotifyRemotePlaying) {
            isPlaying = !state.isPaused
            isPaused = state.isPaused
            currentPositionMs = state.playbackPosition
            val dur = state.track?.duration ?: 0L
            if (dur > 0L) {
                totalDurationMs = dur
            }

            // Sync active cassette track if Spotify auto-advanced to next track
            val stateUri = state.track?.uri
            if (!stateUri.isNullOrBlank() && activeTracks.isNotEmpty()) {
                val matchIdx = activeTracks.indexOfFirst { it.spotifyUri == stateUri }
                if (matchIdx >= 0 && matchIdx != activeTrackIndex) {
                    activeTrackIndex = matchIdx
                }
            }
        }
    }

    fun playTrack(track: Track?) {
        if (track == null || !isTapeInserted) return
        val scheme = track.contentUri.scheme ?: ""
        val isSpotifyTrack = (scheme == "spotify") || (track.spotifyUri != null) || (currentTape?.isSpotify == true)

        if (isSpotifyTrack) {
            // Play official authentic track directly through Spotify App Remote!
            exoPlayer.stop()
            isSpotifyRemotePlaying = true
            isPlaying = true
            isPaused = false
            currentPositionMs = 0L
            totalDurationMs = track.durationMs.coerceAtLeast(30_000L)

            val uriToPlay = track.spotifyUri
                ?: if (scheme == "spotify") track.contentUri.toString()
                else "spotify:track:${track.id}"

            SpotifyRemoteManager.play(context, uriToPlay)
        } else {
            // Local device audio track
            if (isSpotifyRemotePlaying) {
                SpotifyRemoteManager.pause(context)
            }
            isSpotifyRemotePlaying = false
            val mediaItem = MediaItem.fromUri(track.contentUri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            isPaused = false
        }
    }

    // Handle Android system back button
    BackHandler(enabled = currentScreen == CurrentAppView.TAPE_SELECTION) {
        currentScreen = CurrentAppView.DECK
    }

    // Listen to ExoPlayer playback events (including auto-advancing to next track on cassette)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (playing) {
                    isPlaying = true
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.w("PixelPlayer", "ExoPlayer error: ${error.message}")
                val tr = currentTape?.tracks?.getOrNull(activeTrackIndex) ?: return
                SpotifyManager.invalidateCachedStream(tr)
                coroutineScope.launch {
                    val fallbackUri = SpotifyManager.resolveFallbackPreviewUri(tr)
                    if (fallbackUri != null) {
                        val item = MediaItem.fromUri(fallbackUri)
                        exoPlayer.setMediaItem(item)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = exoPlayer.duration
                    if (dur > 0) {
                        totalDurationMs = dur
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    val tracks = currentTape?.tracks ?: emptyList()
                    if (tracks.isNotEmpty()) {
                        if (exoPlayer.repeatMode == Player.REPEAT_MODE_ONE) {
                            exoPlayer.seekTo(0L)
                            exoPlayer.play()
                        } else {
                            activeTrackIndex = if (exoPlayer.shuffleModeEnabled && tracks.size > 1) {
                                tracks.indices.random()
                            } else {
                                (activeTrackIndex + 1) % tracks.size
                            }
                            playTrack(tracks[activeTrackIndex])
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val dur = exoPlayer.duration
                if (dur > 0) {
                    totalDurationMs = dur
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Timer updater coroutine for LCD display (smooth 250ms updates for responsive Fast-Forward/Rewind & reel rotation)
    LaunchedEffect(isPlaying, isPaused, isSpotifyRemotePlaying) {
        while (isPlaying && !isPaused) {
            if (isSpotifyRemotePlaying) {
                delay(250)
                if (isPlaying && !isPaused) {
                    currentPositionMs = (currentPositionMs + 250L).coerceAtMost(totalDurationMs)
                }
            } else {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration
                if (dur > 0) {
                    totalDurationMs = dur
                }
                delay(250)
            }
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
                        SpotifyManager.saveLastTapeId(context, selectedTape.id)
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
                                    SpotifyRemoteManager.resume(context)
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
                                SpotifyRemoteManager.pause(context)
                            } else {
                                exoPlayer.pause()
                                isPaused = true
                            }
                        }
                    },
                    onStop = {
                        if (isSpotifyRemotePlaying) {
                            SpotifyRemoteManager.pause(context)
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
                            if (isSpotifyRemotePlaying) {
                                currentPositionMs = 0L
                                totalDurationMs = prevTrack.durationMs.coerceAtLeast(30_000L)
                                val uri = prevTrack.spotifyUri ?: if (prevTrack.contentUri.scheme == "spotify") prevTrack.contentUri.toString() else "spotify:track:${prevTrack.id}"
                                SpotifyRemoteManager.skipPrevious(context, uri)
                            } else if (isPlaying) {
                                playTrack(prevTrack)
                            }
                        }
                    },
                    onSkip = {
                        if (activeTracks.isNotEmpty()) {
                            activeTrackIndex = (activeTrackIndex + 1) % activeTracks.size
                            val nextTrack = activeTracks[activeTrackIndex]
                            if (isSpotifyRemotePlaying) {
                                currentPositionMs = 0L
                                totalDurationMs = nextTrack.durationMs.coerceAtLeast(30_000L)
                                val uri = nextTrack.spotifyUri ?: if (nextTrack.contentUri.scheme == "spotify") nextTrack.contentUri.toString() else "spotify:track:${nextTrack.id}"
                                SpotifyRemoteManager.skipNext(context, uri)
                            } else if (isPlaying) {
                                playTrack(nextTrack)
                            }
                        }
                    },
                    onEject = {
                        feedback.onTapeLatch()
                        if (isSpotifyRemotePlaying) {
                            SpotifyRemoteManager.pause(context)
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
                                SpotifyRemoteManager.pause(context)
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
                        if (isSpotifyRemotePlaying) {
                            SpotifyRemoteManager.setShuffle(context, enabled)
                        }
                    },
                    onRepeatToggle = { enabled ->
                        exoPlayer.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
                        if (isSpotifyRemotePlaying) {
                            SpotifyRemoteManager.setRepeat(context, enabled)
                        }
                    },
                    onSeek = { deltaMs ->
                        if (isSpotifyRemotePlaying) {
                            val dur = totalDurationMs.coerceAtLeast(30_000L)
                            val targetMs = (currentPositionMs + deltaMs).coerceIn(0L, dur)
                            currentPositionMs = targetMs
                            SpotifyRemoteManager.seekTo(context, targetMs)
                        } else {
                            val dur = if (exoPlayer.duration > 0) exoPlayer.duration else totalDurationMs.coerceAtLeast(30_000L)
                            val targetMs = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, dur)
                            exoPlayer.seekTo(targetMs)
                            currentPositionMs = targetMs
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

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // FULL-RESOLUTION BACKGROUND: Always scales to fit the entire physical phone screen
        // In Landscape, use chassis_texture_bg (1024x460); in Portrait, use chassis_texture_portrait (460x1024)
        // so the aspect ratio matches the device screen and the texture is never stretched or blurry.
        val bgResId = if (isLandscape) R.drawable.chassis_texture_bg else R.drawable.chassis_texture_portrait
        Image(
            painter = painterResource(id = bgResId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // FOREGROUND CONTENT LAYER: windowInsetsPadding avoids Android navigation buttons / camera cutouts
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (isLandscape) {
                // HORIZONTAL (Landscape) Layout - Proportional coordinate match to master 1024x460 design
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
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
                                height = 25.dp,
                                onDownPress = { feedback.onTick() },
                                onClick = { onSpotifyClicked() }
                            )

                            MechanicalButton(
                                label = "TAPES 📚",
                                baseColor = Color(0xFF4E342E),
                                highlightColor = Color(0xFF6D4C41),
                                shadowColor = Color(0xFF3E2723),
                                width = 82.dp,
                                height = 25.dp,
                                onDownPress = { feedback.onTick() },
                                onClick = { onBrowseTapesClicked() }
                            )
                        }
                    }

                    // Main Chassis Deck & Controls mapped to master 1024x404 interior coordinate grid
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val cw = maxWidth
                        val ch = maxHeight

                    // 1. Cassette Door (Master: x=34, y=87, w=496, h=284; shunted slightly left to x=18)
                    val deckW = minOf(cw * (496f / 1024f), ch * (284f / 404f) * (1024f / 593f))
                    val deckX = cw * (18f / 1024f)
                    val deckY = ch * (30f / 404f)

                    CassetteDeck(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = deckX, y = deckY)
                            .width(deckW),
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

                    // 2. Speaker Vents (Master: x=40, y=398, w=210, h=50; shunted left to x=24)
                    val ventsX = cw * (24f / 1024f)
                    val ventsY = ch * (342f / 404f)
                    val ventsW = cw * (210f / 1024f)
                    val ventsH = ch * (50f / 404f)

                    Image(
                        painter = painterResource(id = R.drawable.chassis_speaker_vents),
                        contentDescription = "Speaker Vents",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = ventsX, y = ventsY)
                            .size(width = ventsW, height = ventsH),
                        contentScale = ContentScale.FillBounds
                    )

                    // 3. Mint-Green LCD Display (Master: x=565, y=76, w=392, h=163; shunted left to x=547)
                    val lcdW = minOf(cw * (392f / 1024f), ch * (163f / 404f) * (392f / 163f))
                    val lcdX = cw * (547f / 1024f)
                    val lcdY = ch * (19f / 404f)

                    LcdDisplay(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = lcdX, y = lcdY)
                            .width(lcdW),
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

                    // 4. Transport Buttons Bank + Labels (Master: x=536..1018, w=482, bottom=368; shunted left to x=518..1000)
                    val buttonsX = cw * (518f / 1024f)
                    val buttonsW = cw * (482f / 1024f)
                    val buttonsTopY = ch * (195f / 404f)

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = buttonsX, y = buttonsTopY)
                            .width(buttonsW),
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

                    // 5. Shuffle & Repeat Switches (Master: x=778..912, y=398..448; shunted left to x=760)
                    val switchesX = cw * (760f / 1024f)
                    val switchesY = ch * (336f / 404f)
                    val switchW = cw * (60f / 1024f)
                    val switchH = ch * (31f / 404f)

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = switchesX, y = switchesY)
                    ) {
                        SwitchesRow(
                            isShuffleOn = isShuffleOn,
                            isRepeatOn = isRepeatOn,
                            feedback = feedback,
                            switchWidth = switchW,
                            switchHeight = switchH,
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
                        painter = painterResource(id = R.drawable.chassis_texture_portrait),
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
}

@Composable
private fun TransportButtonLabelsRow(isTapeRunning: Boolean) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        val rowW = maxWidth
        val wSkip = rowW * (73f / 482f)
        val wRewind = rowW * (72f / 482f)
        val wPlay = rowW * (94f / 482f)
        val wFastFwd = rowW * (73f / 482f)
        val wSkipNext = rowW * (73f / 482f)
        val wEject = rowW * (70f / 482f)
        val labelSize = (rowW.value * 0.025f).coerceIn(7.5f, 10.5f).sp
        val twoLineSize = (rowW.value * 0.022f).coerceIn(6.8f, 9.2f).sp

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "SKIP",
                modifier = Modifier.width(wSkip),
                textAlign = TextAlign.Center,
                color = PixelPalette.ButtonLabelAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = labelSize
            )
            Text(
                text = "REWIND",
                modifier = Modifier.width(wRewind),
                textAlign = TextAlign.Center,
                color = PixelPalette.ButtonLabelAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = labelSize
            )
            Text(
                text = "PLAY",
                modifier = Modifier.width(wPlay),
                textAlign = TextAlign.Center,
                color = PixelPalette.ButtonLabelAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = labelSize
            )
            Text(
                text = "FAST\nFORWARD",
                modifier = Modifier.width(wFastFwd),
                textAlign = TextAlign.Center,
                color = PixelPalette.ButtonLabelAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = twoLineSize,
                lineHeight = twoLineSize
            )
            Text(
                text = "SKIP\nFORWARD",
                modifier = Modifier.width(wSkipNext),
                textAlign = TextAlign.Center,
                color = PixelPalette.ButtonLabelAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = twoLineSize,
                lineHeight = twoLineSize
            )
            Text(
                text = if (isTapeRunning) "STOP" else "EJECT",
                modifier = Modifier.width(wEject),
                textAlign = TextAlign.Center,
                color = PixelPalette.ButtonLabelAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = labelSize
            )
        }
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
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val rowW = maxWidth
        // Exact button proportions from master 1024x460 design (total span = 482px, sum of widths = 455px)
        val wSkip = rowW * (73f / 482f)
        val wRewind = rowW * (72f / 482f)
        val wPlay = rowW * (94f / 482f)
        val wFastFwd = rowW * (73f / 482f)
        val wSkipNext = rowW * (73f / 482f)
        val wEject = rowW * (70f / 482f)

        val hStd = rowW * (80f / 482f)
        val hPlay = rowW * (82f / 482f)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. SKIP PREV (|◀◀)
            PixelArtButton(
                unpressedResId = R.drawable.btn_skip_prev_unpressed,
                pressedResId = R.drawable.btn_skip_prev_pressed,
                width = wSkip,
                height = hStd,
                onDownPress = { feedback.onTick() },
                onClick = { onSkipPrev() }
            )

            // 2. REWIND (◀◀)
            PixelArtButton(
                unpressedResId = R.drawable.btn_rewind_unpressed,
                pressedResId = R.drawable.btn_rewind_pressed,
                width = wRewind,
                height = hStd,
                onDownPress = { feedback.onRewindPress() },
                onHoldRepeat = {
                    feedback.onTick()
                    onSeek(-5_000L)
                },
                onClick = {
                    onSeek(-10_000L)
                }
            )

            // 3. PLAY (▶) - latched down when playing!
            PixelArtButton(
                unpressedResId = R.drawable.btn_play_unpressed,
                pressedResId = R.drawable.btn_play_pressed,
                isLatched = isPlaying,
                width = wPlay,
                height = hPlay,
                onDownPress = { feedback.onPlayPress() },
                onClick = { onPlay() }
            )

            // 4. FAST FORWARD (▶▶)
            PixelArtButton(
                unpressedResId = R.drawable.btn_fast_fwd_unpressed,
                pressedResId = R.drawable.btn_fast_fwd_pressed,
                width = wFastFwd,
                height = hStd,
                onDownPress = { feedback.onRewindPress() },
                onHoldRepeat = {
                    feedback.onTick()
                    onSeek(5_000L)
                },
                onClick = {
                    onSeek(10_000L)
                }
            )

            // 5. SKIP NEXT (▶▶|)
            PixelArtButton(
                unpressedResId = R.drawable.btn_skip_next_unpressed,
                pressedResId = R.drawable.btn_skip_next_pressed,
                width = wSkipNext,
                height = hStd,
                onDownPress = { feedback.onTick() },
                onClick = { onSkip() }
            )

            // 6. STOP (■) when running / EJECT (⏏) when stopped!
            if (isTapeRunning) {
                PixelArtButton(
                    unpressedResId = R.drawable.btn_stop_unpressed,
                    pressedResId = R.drawable.btn_stop_pressed,
                    width = wEject,
                    height = hStd,
                    onDownPress = { feedback.onStopPress() },
                    onClick = { onStop() }
                )
            } else {
                PixelArtButton(
                    unpressedResId = R.drawable.btn_eject_unpressed,
                    pressedResId = R.drawable.btn_eject_pressed,
                    width = wEject,
                    height = hStd,
                    onDownPress = { feedback.onTapeLatch() },
                    onClick = { onEject() }
                )
            }
        }
    }
}

@Composable
private fun SwitchesRow(
    isShuffleOn: Boolean,
    isRepeatOn: Boolean,
    feedback: MechanicalFeedback,
    switchWidth: androidx.compose.ui.unit.Dp = 46.dp,
    switchHeight: androidx.compose.ui.unit.Dp = 24.dp,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                width = switchWidth,
                height = switchHeight,
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
                width = switchWidth,
                height = switchHeight,
                onToggle = {
                    feedback.onTick()
                    onRepeatToggle()
                }
            )
        }
    }
}

@Preview(
    name = "Walkman Deck - Landscape",
    widthDp = 1024,
    heightDp = 460,
    showBackground = true
)
@Composable
private fun DeckScreenLandscapePreview() {
    val sampleTrack = com.retro.pixelplayer.audio.Track(
        id = 1L,
        title = "Neon Sunset",
        artist = "Neon Dreamer",
        album = "Sunset Pixels",
        durationMs = 215_000L,
        contentUri = Uri.EMPTY
    )
    val sampleTape = com.retro.pixelplayer.audio.TapeItem(
        id = "preview_1",
        title = "Sunset Vibes '84",
        subtitle = "OFFICIAL ALBUM TAPE",
        type = com.retro.pixelplayer.audio.TapeType.ALBUM,
        tracks = listOf(sampleTrack),
        labelColorHex = 0xFFFF7043
    )
    DeckScreen(
        currentTape = sampleTape,
        activeTrack = sampleTrack,
        activeTrackIndex = 0,
        totalTracksInTape = 1,
        isTapeInserted = true,
        isPlaying = true,
        isPaused = false,
        isSpotifyLoggedIn = true,
        currentPositionMs = 65_000L,
        totalDurationMs = 215_000L,
        feedback = MechanicalFeedback(LocalContext.current),
        onPlay = {},
        onPause = {},
        onStop = {},
        onSkipPrev = {},
        onSkip = {},
        onEject = {},
        onSeek = {},
        onShuffleToggle = {},
        onRepeatToggle = {},
        onTapeDeckTapped = {},
        onSpotifyClicked = {},
        onBrowseTapesClicked = {}
    )
}

@Preview(
    name = "Walkman Deck - Portrait",
    widthDp = 412,
    heightDp = 915,
    showBackground = true
)
@Composable
private fun DeckScreenPortraitPreview() {
    val sampleTrack = com.retro.pixelplayer.audio.Track(
        id = 1L,
        title = "Neon Sunset",
        artist = "Neon Dreamer",
        album = "Sunset Pixels",
        durationMs = 215_000L,
        contentUri = Uri.EMPTY
    )
    val sampleTape = com.retro.pixelplayer.audio.TapeItem(
        id = "preview_1",
        title = "Sunset Vibes '84",
        subtitle = "OFFICIAL ALBUM TAPE",
        type = com.retro.pixelplayer.audio.TapeType.ALBUM,
        tracks = listOf(sampleTrack),
        labelColorHex = 0xFFFF7043
    )
    DeckScreen(
        currentTape = sampleTape,
        activeTrack = sampleTrack,
        activeTrackIndex = 0,
        totalTracksInTape = 1,
        isTapeInserted = true,
        isPlaying = true,
        isPaused = false,
        isSpotifyLoggedIn = true,
        currentPositionMs = 65_000L,
        totalDurationMs = 215_000L,
        feedback = MechanicalFeedback(LocalContext.current),
        onPlay = {},
        onPause = {},
        onStop = {},
        onSkipPrev = {},
        onSkip = {},
        onEject = {},
        onSeek = {},
        onShuffleToggle = {},
        onRepeatToggle = {},
        onTapeDeckTapped = {},
        onSpotifyClicked = {},
        onBrowseTapesClicked = {}
    )
}



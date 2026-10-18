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
import com.retro.pixelplayer.skin.SkinManager
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
    var isTapeShuddering by remember { mutableStateOf(false) }
    var lcdErrorMessage by remember { mutableStateOf<String?>(null) }
    var playSessionId by remember { mutableIntStateOf(0) }

    fun triggerTapePlaybackError(message: String) {
        isPlaying = false
        isPaused = false
        isSpotifyRemotePlaying = false
        currentPositionMs = 0L
        exoPlayer.stop()

        lcdErrorMessage = message
        isTapeShuddering = true

        coroutineScope.launch {
            feedback?.onTick()
            delay(120)
            feedback?.onTick()
            delay(600)
            isTapeShuddering = false
            delay(3500)
            if (lcdErrorMessage == message) {
                lcdErrorMessage = null
            }
        }
    }

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
            // Check if Spotify is installed
            if (!SpotifyRemoteManager.isInstalled(context)) {
                triggerTapePlaybackError("NO SPOTIFY APP")
                return
            }

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

            val thisSession = ++playSessionId
            SpotifyRemoteManager.play(
                context = context,
                spotifyUri = uriToPlay,
                onError = { errMsg ->
                    triggerTapePlaybackError(errMsg)
                }
            )

            // Watchdog: If Spotify doesn't respond or connect within 3.5 seconds, halt and warn user
            coroutineScope.launch {
                delay(3500)
                if (playSessionId == thisSession && isSpotifyRemotePlaying && isPlaying && !isPaused) {
                    val state = SpotifyRemoteManager.playerState.value
                    if (state == null || state.isPaused) {
                        triggerTapePlaybackError("SPOTIFY NOT RUNNING")
                    }
                }
            }
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
    LaunchedEffect(isPlaying, isPaused, isSpotifyRemotePlaying, isSpotifyRemoteConnected, spotifyPlayerState) {
        while (isPlaying && !isPaused) {
            if (isSpotifyRemotePlaying) {
                // For Spotify, only advance timer if Spotify is actually connected and actively reporting playing
                if (isSpotifyRemoteConnected && spotifyPlayerState?.isPaused == false) {
                    delay(250)
                    if (isPlaying && !isPaused) {
                        currentPositionMs = (currentPositionMs + 250L).coerceAtMost(totalDurationMs)
                    }
                } else {
                    delay(250)
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
                    isShuddering = isTapeShuddering,
                    errorMessage = lcdErrorMessage,
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
    feedback: MechanicalFeedback? = null,
    isShuddering: Boolean = false,
    errorMessage: String? = null,
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
            val context = LocalContext.current
            val skin = remember { SkinManager.loadSkin(context) }

            if (isLandscape) {
                // HORIZONTAL (Landscape) Layout - Driven by Skin JSON
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
                                onDownPress = { feedback?.onTick() },
                                onClick = { onSpotifyClicked() }
                            )

                            MechanicalButton(
                                label = "TAPES 📚",
                                baseColor = Color(0xFF4E342E),
                                highlightColor = Color(0xFF6D4C41),
                                shadowColor = Color(0xFF3E2723),
                                width = 82.dp,
                                height = 25.dp,
                                onDownPress = { feedback?.onTick() },
                                onClick = { onBrowseTapesClicked() }
                            )
                        }
                    }

                    // Main Chassis Deck & Controls mapped to Skin layout coordinates
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val cw = maxWidth
                        val ch = maxHeight
                        val vp = skin.landscape

                        // 1. Cassette Door
                        val doorEl = vp.getElement("cassette_door")
                        val deckW = cw * (doorEl?.relWidth ?: 0.5246f)
                        val deckX = cw * (doorEl?.relX ?: 0.0383f)
                        val deckY = ch * (doorEl?.relY ?: 0.1092f)

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
                            isShuddering = isShuddering,
                            onEjectOrInsert = { onTapeDeckTapped() }
                        )

                        // 2. Speaker Vents
                        val ventsEl = vp.getElement("speaker_vents")
                        val ventsX = cw * (ventsEl?.relX ?: 0.1202f)
                        val ventsY = ch * (ventsEl?.relY ?: 0.8519f)
                        val ventsW = cw * (ventsEl?.relWidth ?: 0.3716f)
                        val ventsH = ch * (ventsEl?.relHeight ?: 0.0874f)

                        Image(
                            painter = painterResource(id = R.drawable.chassis_speaker_vents),
                            contentDescription = "Speaker Vents",
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = ventsX, y = ventsY)
                                .size(width = ventsW, height = ventsH),
                            contentScale = ContentScale.FillBounds
                        )

                        // 3. Mint-Green LCD Display
                        val lcdEl = vp.getElement("lcd_display")
                        val lcdW = cw * (lcdEl?.relWidth ?: 0.3716f)
                        val lcdX = cw * (lcdEl?.relX ?: 0.5792f)
                        val lcdY = ch * (lcdEl?.relY ?: 0.1553f)

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
                            statusText = statusText,
                            errorMessage = errorMessage
                        )

                        // 4. Transport Buttons Bank + Labels
                        val btnPrev = vp.getElement("btn_skip_prev")
                        val buttonsX = cw * (btnPrev?.relX ?: 0.5814f)
                        val buttonsW = cw * 0.375f
                        val buttonsTopY = ch * (btnPrev?.relY ?: 0.6602f)

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

                        // 5. Shuffle & Repeat Switches
                        val shuffleEl = vp.getElement("switch_shuffle")
                        val switchesX = cw * (shuffleEl?.relX ?: 0.6656f)
                        val switchesY = ch * (shuffleEl?.relY ?: 0.8495f)
                        val switchW = cw * (shuffleEl?.relWidth ?: 0.0656f)
                        val switchH = ch * (shuffleEl?.relHeight ?: 0.0728f)

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
                // VERTICAL (Portrait) Layout - Driven by Skin JSON
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
                            .padding(horizontal = 6.dp, vertical = 4.dp),
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
                                onDownPress = { feedback?.onTick() },
                                onClick = { onSpotifyClicked() }
                            )

                            MechanicalButton(
                                label = "TAPES 📚",
                                baseColor = Color(0xFF4E342E),
                                highlightColor = Color(0xFF6D4C41),
                                shadowColor = Color(0xFF3E2723),
                                width = 80.dp,
                                height = 30.dp,
                                onDownPress = { feedback?.onTick() },
                                onClick = { onBrowseTapesClicked() }
                            )
                        }
                    }

                    // Main Chassis Coordinate Area
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val cw = maxWidth
                        val ch = maxHeight
                        val vp = skin.portrait

                        // Cassette Door
                        val doorEl = vp.getElement("cassette_door")
                        val deckX = cw * (doorEl?.relX ?: 0.0534f)
                        val deckY = ch * (doorEl?.relY ?: 0.1301f)
                        val deckW = cw * (doorEl?.relWidth ?: 0.8738f)

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
                            isShuddering = isShuddering,
                            onEjectOrInsert = { onTapeDeckTapped() }
                        )

                        // Mint-Green LCD Display
                        val lcdEl = vp.getElement("lcd_display")
                        val lcdX = cw * (lcdEl?.relX ?: 0.0534f)
                        val lcdY = ch * (lcdEl?.relY ?: 0.4273f)
                        val lcdW = cw * (lcdEl?.relWidth ?: 0.8738f)

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
                            statusText = statusText,
                            errorMessage = errorMessage
                        )

                        // Shuffle & Repeat Rocker Switches
                        val shuffleEl = vp.getElement("switch_shuffle")
                        val repeatEl = vp.getElement("switch_repeat")
                        val shuffleX = cw * (shuffleEl?.relX ?: 0.1286f)
                        val shuffleY = ch * (shuffleEl?.relY ?: 0.6514f)
                        val switchW = cw * (shuffleEl?.relWidth ?: 0.1553f)
                        val switchH = ch * (shuffleEl?.relHeight ?: 0.0350f)
                        val repeatX = cw * (repeatEl?.relX ?: 0.7209f)
                        val repeatY = ch * (repeatEl?.relY ?: 0.6546f)

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = shuffleX, y = shuffleY)
                        ) {
                            PixelArtButton(
                                unpressedResId = if (isShuffleOn) R.drawable.switch_shuffle_on else R.drawable.switch_shuffle_off,
                                pressedResId = if (isShuffleOn) R.drawable.switch_shuffle_on else R.drawable.switch_shuffle_off,
                                width = switchW,
                                height = switchH,
                                onDownPress = { feedback?.onTick() },
                                onClick = {
                                    isShuffleOn = !isShuffleOn
                                    onShuffleToggle(isShuffleOn)
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = repeatX, y = repeatY)
                        ) {
                            PixelArtButton(
                                unpressedResId = if (isRepeatOn) R.drawable.switch_repeat_on else R.drawable.switch_repeat_off,
                                pressedResId = if (isRepeatOn) R.drawable.switch_repeat_on else R.drawable.switch_repeat_off,
                                width = switchW,
                                height = switchH,
                                onDownPress = { feedback?.onTick() },
                                onClick = {
                                    isRepeatOn = !isRepeatOn
                                    onRepeatToggle(isRepeatOn)
                                }
                            )
                        }

                        // Transport Buttons Row + Labels
                        val skipPrevEl = vp.getElement("btn_skip_prev")
                        val buttonsX = cw * (skipPrevEl?.relX ?: 0.0704f)
                        val buttonsY = ch * (skipPrevEl?.relY ?: 0.7224f)
                        val buttonsW = cw * 0.83f

                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = buttonsX, y = buttonsY)
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

                        // Speaker Vents
                        val ventsEl = vp.getElement("speaker_vents")
                        val ventsX = cw * (ventsEl?.relX ?: 0.0631f)
                        val ventsY = ch * (ventsEl?.relY ?: 0.8426f)
                        val ventsW = cw * (ventsEl?.relWidth ?: 0.8738f)
                        val ventsH = ch * (ventsEl?.relHeight ?: 0.0459f)

                        Image(
                            painter = painterResource(id = R.drawable.chassis_speaker_vents),
                            contentDescription = "Speaker Vents",
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = ventsX, y = ventsY)
                                .size(width = ventsW, height = ventsH),
                            contentScale = ContentScale.FillBounds
                        )
                    }
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
    feedback: MechanicalFeedback? = null,
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
                onDownPress = { feedback?.onTick() },
                onClick = { onSkipPrev() }
            )

            // 2. REWIND (◀◀)
            PixelArtButton(
                unpressedResId = R.drawable.btn_rewind_unpressed,
                pressedResId = R.drawable.btn_rewind_pressed,
                width = wRewind,
                height = hStd,
                onDownPress = { feedback?.onRewindPress() },
                onHoldRepeat = {
                    feedback?.onTick()
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
                onDownPress = { feedback?.onPlayPress() },
                onClick = { onPlay() }
            )

            // 4. FAST FORWARD (▶▶)
            PixelArtButton(
                unpressedResId = R.drawable.btn_fast_fwd_unpressed,
                pressedResId = R.drawable.btn_fast_fwd_pressed,
                width = wFastFwd,
                height = hStd,
                onDownPress = { feedback?.onRewindPress() },
                onHoldRepeat = {
                    feedback?.onTick()
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
                onDownPress = { feedback?.onTick() },
                onClick = { onSkip() }
            )

            // 6. STOP (■) when running / EJECT (⏏) when stopped!
            if (isTapeRunning) {
                PixelArtButton(
                    unpressedResId = R.drawable.btn_stop_unpressed,
                    pressedResId = R.drawable.btn_stop_pressed,
                    width = wEject,
                    height = hStd,
                    onDownPress = { feedback?.onStopPress() },
                    onClick = { onStop() }
                )
            } else {
                PixelArtButton(
                    unpressedResId = R.drawable.btn_eject_unpressed,
                    pressedResId = R.drawable.btn_eject_pressed,
                    width = wEject,
                    height = hStd,
                    onDownPress = { feedback?.onTapeLatch() },
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
    feedback: MechanicalFeedback? = null,
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
                    feedback?.onTick()
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
                    feedback?.onTick()
                    onRepeatToggle()
                }
            )
        }
    }
}

@Preview(
    name = "Pixel 8 - Landscape",
    device = "spec:parent=pixel_8,orientation=landscape",
    showSystemUi = true
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
        feedback = null,
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
    name = "Pixel 8 - Portrait",
    device = "id:pixel_8",
    showSystemUi = true
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
        feedback = null,
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



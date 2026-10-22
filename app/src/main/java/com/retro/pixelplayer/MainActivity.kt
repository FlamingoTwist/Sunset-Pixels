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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.statusBars
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
import com.retro.pixelplayer.ui.components.pixelPainterResource

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.retro.pixelplayer.R
import com.retro.pixelplayer.audio.LocalMusicScanner
import com.retro.pixelplayer.audio.MixtapeManager
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
import com.retro.pixelplayer.ui.components.RecordStoreScreen
import com.retro.pixelplayer.ui.components.RecordToMixtapeDialog
import com.retro.pixelplayer.ui.components.ShelfMode
import com.retro.pixelplayer.ui.components.SpotifyAuthDialog
import com.retro.pixelplayer.ui.components.TapeInlayScreen
import com.retro.pixelplayer.ui.components.TapeSelectionScreen
import com.retro.pixelplayer.ui.theme.PixelPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CurrentAppView {
    DECK,           // The physical cassette tape player
    TAPE_SELECTION, // The Wooden Shelf / Cardboard Shoe Box
    TAPE_INLAY,     // The J-Card Inside Sleeve tracklist
    RECORD_STORE    // Record Store cassette crate search
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

    var spotifyPlaylistTapes by remember {
        mutableStateOf<List<TapeItem>>(initialSpotifyCache?.playlistTapes ?: emptyList())
    }

    // User-created handmade mixtapes
    var userMixtapes by remember { mutableStateOf(MixtapeManager.loadMixtapes(context)) }

    // Artist Shelf is exclusively for Local Files!
    val artistTapes = remember(localArtistTapes) {
        localArtistTapes
    }

    val playlistTapes = remember(localPlaylistTapes, spotifyPlaylistTapes, userMixtapes) {
        userMixtapes + spotifyPlaylistTapes + localPlaylistTapes
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

    // State for Recording to Mixtape & J-Card Inlay
    var trackToRecord by remember { mutableStateOf<Track?>(null) }
    var showRecordDialog by remember { mutableStateOf(false) }
    var selectedInlayTape by remember { mutableStateOf<TapeItem?>(null) }
    var shelfTargetMode by remember { mutableStateOf(ShelfMode.SHOEBOX_TAPES) }
    var shelfTargetArtist by remember { mutableStateOf<String?>(null) }
    var tapeSelectionResetKey by remember { mutableIntStateOf(0) }
    var lastPlayCommandTime by remember { mutableLongStateOf(0L) }

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
                spotifyPlaylistTapes = syncData.playlistTapes
                spotifySyncStatus = syncData.statusMessage

                // If the user had a Spotify tape selected before closing the app (or no tape yet), restore/update it
                val savedId = SpotifyManager.getLastTapeId(context)
                val allSynced = syncData.playlistTapes
                if (savedId != null) {
                    allSynced.firstOrNull { it.id == savedId }?.let { currentTape = it }
                } else if (currentTape == null) {
                    currentTape = syncData.playlistTapes.firstOrNull()
                }
            }.onFailure { err ->
                android.util.Log.w("SpotifySync", "Background sync notice: ${err.message}")
                val hasCached = spotifyPlaylistTapes.isNotEmpty()
                spotifySyncStatus = if (hasCached) {
                    "SPOTIFY READY • ${spotifyPlaylistTapes.size} MIXTAPES"
                } else {
                    "SYNC PENDING • TAP SYNC WHEN CONNECTED"
                }
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

    val cassetteVariants = remember {
        listOf(
            R.drawable.deck_cassette_shell_cream,
            R.drawable.deck_cassette_shell_obsidian,
            R.drawable.deck_cassette_shell_yellow,
            R.drawable.deck_cassette_shell_mint,
            R.drawable.deck_cassette_shell_crimson
        )
    }
    var currentTapeShellResId by remember { mutableStateOf(cassetteVariants.random()) }
    var isTapeInserted by remember { mutableStateOf(true) }
    var isTapeInserting by remember { mutableStateOf(false) }
    var isTapeEjecting by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isSpotifyRemotePlaying by remember { mutableStateOf(false) }
    var isExplicitlyStopped by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var isCueingTrack by remember { mutableStateOf(false) }
    var favouriteTapeIds by remember { mutableStateOf(SpotifyManager.getFavouriteTapeIds(context)) }
    val spotifyPlayerState by SpotifyRemoteManager.playerState.collectAsState()
    val isSpotifyRemoteConnected by SpotifyRemoteManager.isConnected.collectAsState()

    // Connect and refresh Spotify playback state whenever app is resumed
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isSpotifyLoggedIn) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (isSpotifyLoggedIn && SpotifyRemoteManager.isInstalled(context)) {
                    SpotifyRemoteManager.refreshState(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (isSpotifyLoggedIn && SpotifyRemoteManager.isInstalled(context)) {
            SpotifyRemoteManager.refreshState(context)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val activeTracks = currentTape?.tracks ?: emptyList()
    val activeTrack: Track? = if (activeTracks.isNotEmpty()) {
        activeTracks[activeTrackIndex % activeTracks.size]
    } else {
        null
    }

    fun playTrack(track: Track?, tape: TapeItem? = null, trackIndex: Int? = null) {
        if (track == null) return
        isExplicitlyStopped = false
        isTapeInserted = true
        isCueingTrack = true
        lastPlayCommandTime = System.currentTimeMillis()
        val effectiveTape = tape ?: currentTape
        val scheme = track.contentUri.scheme ?: ""
        val isLocalFile = scheme == "content" || scheme == "file"
        val isSpotifyTrack = !isLocalFile && ((scheme == "spotify") || (track.spotifyUri != null) || (effectiveTape?.isSpotify == true))

        if (isSpotifyTrack) {
            // Play authentic Spotify playlist tape directly through Spotify App Remote!
            exoPlayer.stop()
            isSpotifyRemotePlaying = true
            isPlaying = true
            isPaused = false
            currentPositionMs = 0L
            totalDurationMs = track.durationMs.coerceAtLeast(30_000L)

            val trackUri = track.spotifyUri ?: if (scheme == "spotify") track.contentUri.toString() else null
            val playlistUri = effectiveTape?.spotifyContextUri

            if (!isSpotifyRemoteConnected && SpotifyRemoteManager.isInstalled(context)) {
                SpotifyRemoteManager.connect(context, showAuthView = false)
            }

            val uriToPlay = trackUri ?: playlistUri ?: ""
            if (uriToPlay.isNotBlank()) {
                SpotifyRemoteManager.play(
                    context = context,
                    uri = uriToPlay
                )
            }
        } else {
            // Local device audio track
            if (isSpotifyRemotePlaying) {
                SpotifyRemoteManager.pause(context)
            }
            isSpotifyRemotePlaying = false
            isPlaying = true
            isPaused = false
            exoPlayer.stop()
            val mediaItem = MediaItem.fromUri(track.contentUri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    var lastAutoAdvanceTime by remember { mutableLongStateOf(0L) }

    fun advanceToNextTrack() {
        val now = System.currentTimeMillis()
        if (now - lastAutoAdvanceTime < 1500L) return
        lastAutoAdvanceTime = now
        if (isSpotifyRemotePlaying || currentTape?.isSpotify == true) {
            SpotifyRemoteManager.skipNext(context)
            return
        }
        val tracks = currentTape?.tracks ?: emptyList()
        if (tracks.isNotEmpty()) {
            val nextIndex = if (exoPlayer.repeatMode == Player.REPEAT_MODE_ONE) {
                activeTrackIndex
            } else if (exoPlayer.shuffleModeEnabled && tracks.size > 1) {
                val available = tracks.indices.filter { it != activeTrackIndex }
                available.randomOrNull() ?: ((activeTrackIndex + 1) % tracks.size)
            } else {
                (activeTrackIndex + 1) % tracks.size
            }
            activeTrackIndex = nextIndex
            isCueingTrack = true
            playTrack(tracks[nextIndex])
        }
    }

    // Cueing spinner timeout safeguard
    LaunchedEffect(isCueingTrack, activeTrackIndex) {
        if (isCueingTrack) {
            delay(400L)
            isCueingTrack = false
        }
    }

    // React to authoritative playback updates from the Spotify App Remote SDK
    LaunchedEffect(spotifyPlayerState) {
        val state = spotifyPlayerState ?: return@LaunchedEffect
        val isSpotifyTape = currentTape?.isSpotify == true
        if (isSpotifyRemotePlaying || isSpotifyTape) {
            val dur = state.track?.duration ?: 0L
            if (dur > 0L) {
                totalDurationMs = dur
            }

            if (isExplicitlyStopped) {
                isPlaying = false
                isPaused = false
                if (!state.isPaused) {
                    isExplicitlyStopped = false
                    isPlaying = true
                    isSpotifyRemotePlaying = true
                }
            } else {
                isPlaying = !state.isPaused
                isPaused = state.isPaused
                if (!state.isPaused) {
                    isSpotifyRemotePlaying = true
                }
            }
            currentPositionMs = state.playbackPosition

            val stateUri = state.track?.uri
            if (!stateUri.isNullOrBlank() && activeTracks.isNotEmpty()) {
                val matchIdx = activeTracks.indexOfFirst { it.spotifyUri == stateUri }
                if (matchIdx >= 0) {
                    activeTrackIndex = matchIdx
                }
            }
            isCueingTrack = false
        }
    }

    // Handle Android system back button
    BackHandler(enabled = currentScreen != CurrentAppView.DECK) {
        currentScreen = when (currentScreen) {
            CurrentAppView.TAPE_INLAY -> CurrentAppView.TAPE_SELECTION
            CurrentAppView.RECORD_STORE -> CurrentAppView.DECK
            else -> CurrentAppView.DECK
        }
    }

    // Listen to ExoPlayer playback events (including auto-advancing to next track on cassette)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (!isSpotifyRemotePlaying) {
                    isPlaying = playing
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.w("PixelPlayer", "Local audio playback error: ${error.message}")
                isPlaying = false
                isPaused = false
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
                    val nextPos = currentPositionMs + 250L
                    currentPositionMs = nextPos.coerceAtMost(totalDurationMs)
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
            isRemoteConnected = isSpotifyRemoteConnected,
            feedback = feedback,
            onLoginClicked = { clientId ->
                SpotifyManager.startLogin(context, clientId)
            },
            onSyncClicked = {
                triggerSpotifySync()
            },
            onConnectRemote = {
                feedback.onTick()
                android.widget.Toast.makeText(context, "Authorizing Spotify Playback...", android.widget.Toast.LENGTH_SHORT).show()
                SpotifyRemoteManager.connect(context, showAuthView = true)
            },
            onLogoutClicked = {
                SpotifyManager.logout(context)
                SpotifyRemoteManager.disconnect()
                isSpotifyLoggedIn = false
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
                    favouriteTapeIds = favouriteTapeIds,
                    onToggleFavourite = { id ->
                        SpotifyManager.toggleFavouriteTape(context, id)
                        favouriteTapeIds = SpotifyManager.getFavouriteTapeIds(context)
                    },
                    feedback = feedback,
                    isSpotifyLoggedIn = isSpotifyLoggedIn,

                    onSpotifyClicked = { showSpotifyDialog = true },
                    onViewTapeInlay = { tape ->
                        selectedInlayTape = tape
                        currentScreen = CurrentAppView.TAPE_INLAY
                    },
                    onCreateNewMixtape = {
                        val newTape = MixtapeManager.createMixtape(context, "MIXTAPE VOL. ${userMixtapes.size + 1}")
                        userMixtapes = MixtapeManager.loadMixtapes(context)
                        selectedInlayTape = newTape
                        currentScreen = CurrentAppView.TAPE_INLAY
                    },
                    onOpenRecordStore = {
                        currentScreen = CurrentAppView.RECORD_STORE
                    },
                    initialMode = shelfTargetMode,
                    initialArtist = shelfTargetArtist,
                    resetKey = tapeSelectionResetKey,
                    onTapeSelected = { selectedTape ->
                        currentTape = selectedTape
                        currentTapeShellResId = cassetteVariants.random()
                        SpotifyManager.saveLastTapeId(context, selectedTape.id)
                        activeTrackIndex = 0
                        isTapeInserted = true
                        isTapeInserting = true
                        currentScreen = CurrentAppView.DECK

                        // Auto-cue first track
                        selectedTape.tracks.firstOrNull()?.let { firstTrack ->
                            playTrack(firstTrack, tape = selectedTape)
                        }
                    },
                    onPlayArtistRadio = { artistName, tapes ->
                        val radioTape = tapes.firstOrNull { it.type == TapeType.ARTIST_ALL } ?: tapes.firstOrNull()
                        if (radioTape != null) {
                            currentTape = radioTape
                            currentTapeShellResId = cassetteVariants.random()
                            SpotifyManager.saveLastTapeId(context, radioTape.id)
                            activeTrackIndex = 0
                            isTapeInserted = true
                            isTapeInserting = true
                            currentScreen = CurrentAppView.DECK

                            radioTape.tracks.firstOrNull()?.let { firstTrack ->
                                playTrack(firstTrack, tape = radioTape)
                            }
                        }
                    },
                    onPlayTrackFromTape = { tapeToPlay, trackIndex ->
                        currentTape = tapeToPlay
                        currentTapeShellResId = cassetteVariants.random()
                        SpotifyManager.saveLastTapeId(context, tapeToPlay.id)
                        activeTrackIndex = trackIndex
                        isTapeInserted = true
                        isTapeInserting = true
                        currentScreen = CurrentAppView.DECK

                        tapeToPlay.tracks.getOrNull(trackIndex)?.let { track ->
                            playTrack(track, tape = tapeToPlay, trackIndex = trackIndex)
                        }
                    },
                    onAddToMixtape = { track ->
                        trackToRecord = track
                        showRecordDialog = true
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
                    tapeShellResId = currentTapeShellResId,
                    isInserting = isTapeInserting,
                    isEjecting = isTapeEjecting,
                    onInsertAnimationFinished = {
                        isTapeInserting = false
                    },
                    onEjectAnimationFinished = {
                        isTapeEjecting = false
                        isTapeInserted = false
                        shelfTargetMode = ShelfMode.SHOEBOX_TAPES
                        shelfTargetArtist = null
                        tapeSelectionResetKey++
                        currentScreen = CurrentAppView.TAPE_SELECTION
                    },
                    isPlaying = isPlaying,
                    isPaused = isPaused,
                    isSpotifyLoggedIn = isSpotifyLoggedIn,
                    spotifyPlayerState = spotifyPlayerState,
                    currentPositionMs = currentPositionMs,
                    totalDurationMs = totalDurationMs,
                    feedback = feedback,
                    isCueing = isCueingTrack,
                    onSpotifyClicked = { showSpotifyDialog = true },
                    onPlay = {
                        isExplicitlyStopped = false
                        if (isTapeInserted) {
                            val isLocalFile = activeTrack?.contentUri?.scheme == "content" || activeTrack?.contentUri?.scheme == "file"
                            val isSpotify = !isLocalFile && (activeTrack?.spotifyUri != null ||
                                            activeTrack?.contentUri?.scheme == "spotify" ||
                                            currentTape?.isSpotify == true)
                            if (isPaused) {
                                if (isSpotify) {
                                    isPlaying = true
                                    isPaused = false
                                    isSpotifyRemotePlaying = true
                                    if (isSpotifyRemoteConnected) {
                                        SpotifyRemoteManager.resume(context)
                                    } else {
                                        playTrack(activeTrack, currentTape)
                                    }
                                } else {
                                    isSpotifyRemotePlaying = false
                                    isPlaying = true
                                    isPaused = false
                                    exoPlayer.play()
                                }
                            } else {
                                playTrack(activeTrack, currentTape)
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
                        isExplicitlyStopped = true
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
                        feedback?.onTick()
                        isCueingTrack = true
                        if (isSpotifyRemotePlaying || currentTape?.isSpotify == true) {
                            SpotifyRemoteManager.skipPrevious(context)
                        } else if (activeTracks.size > 1) {
                            activeTrackIndex = if (activeTrackIndex - 1 < 0) activeTracks.size - 1 else activeTrackIndex - 1
                            val prevTrack = activeTracks[activeTrackIndex]
                            playTrack(prevTrack)
                        } else {
                            exoPlayer.seekTo(0L)
                        }
                    },
                    onSkip = {
                        feedback?.onTick()
                        isCueingTrack = true
                        if (isSpotifyRemotePlaying || currentTape?.isSpotify == true) {
                            SpotifyRemoteManager.skipNext(context)
                        } else if (activeTracks.size > 1) {
                            activeTrackIndex = (activeTrackIndex + 1) % activeTracks.size
                            val nextTrack = activeTracks[activeTrackIndex]
                            playTrack(nextTrack)
                        } else {
                            exoPlayer.seekTo(0L)
                        }
                    },
                    onEject = {
                        feedback.onTapeLatch()
                        if (isSpotifyRemotePlaying) {
                            SpotifyRemoteManager.pause(context)
                        }
                        isSpotifyRemotePlaying = false
                        isPlaying = false
                        exoPlayer.stop()
                        isPaused = false
                        currentPositionMs = 0L
                        isTapeEjecting = true
                    },
                    onTapeDeckTapped = {
                        feedback.onTapeLatch()
                        if (isTapeInserted) {
                            if (isSpotifyRemotePlaying) {
                                SpotifyRemoteManager.pause(context)
                            }
                            isSpotifyRemotePlaying = false
                            isPlaying = false
                            exoPlayer.stop()
                            isPaused = false
                            isTapeEjecting = true
                        } else {
                            shelfTargetMode = ShelfMode.SHOEBOX_TAPES
                            shelfTargetArtist = null
                            tapeSelectionResetKey++
                            currentScreen = CurrentAppView.TAPE_SELECTION
                        }
                    },
                    onBrowseTapesClicked = {
                        feedback.onTick()
                        shelfTargetMode = ShelfMode.SHOEBOX_TAPES
                        shelfTargetArtist = null
                        tapeSelectionResetKey++
                        currentScreen = CurrentAppView.TAPE_SELECTION
                    },
                    onRecordClicked = {
                        feedback.onTapeLatch()
                        if (activeTrack != null) {
                            val isSpotify = activeTrack.spotifyUri != null || activeTrack.contentUri.scheme == "spotify" || currentTape?.isSpotify == true
                            if (isSpotify) {
                                android.widget.Toast.makeText(
                                    context,
                                    "MIXTAPES: LOCAL FILES ONLY",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                trackToRecord = activeTrack
                                showRecordDialog = true
                            }
                        }
                    },
                    onRecordStoreClicked = {
                        feedback.onTick()
                        currentScreen = CurrentAppView.RECORD_STORE
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

            CurrentAppView.TAPE_INLAY -> {
                val inlayTape = selectedInlayTape ?: currentTape
                if (inlayTape != null) {
                    TapeInlayScreen(
                        tape = inlayTape,
                        feedback = feedback,
                        onPlayTape = { tapeToPlay, trackIndex ->
                            currentTape = tapeToPlay
                            currentTapeShellResId = cassetteVariants.random()
                            SpotifyManager.saveLastTapeId(context, tapeToPlay.id)
                            activeTrackIndex = trackIndex
                            isTapeInserted = true
                            isTapeInserting = true
                            currentScreen = CurrentAppView.DECK
                            playTrack(tapeToPlay.tracks.getOrNull(trackIndex), tape = tapeToPlay)
                        },
                        onAddToMixtape = { track ->
                            trackToRecord = track
                            showRecordDialog = true
                        },
                        onGoToArtist = { artistName ->
                            shelfTargetArtist = artistName
                            shelfTargetMode = ShelfMode.ARTIST_TAPES
                            tapeSelectionResetKey++
                            currentScreen = CurrentAppView.TAPE_SELECTION
                        },
                        onRemoveTrack = if (inlayTape.id.startsWith("mixtape_")) {
                            { idx ->
                                MixtapeManager.removeTrackAt(context, inlayTape.id, idx)
                                userMixtapes = MixtapeManager.loadMixtapes(context)
                                selectedInlayTape = userMixtapes.firstOrNull { it.id == inlayTape.id }
                                if (currentTape?.id == inlayTape.id) {
                                    currentTape = selectedInlayTape
                                }
                            }
                        } else null,
                        onDeleteMixtape = if (inlayTape.id.startsWith("mixtape_")) {
                            {
                                MixtapeManager.deleteMixtape(context, inlayTape.id)
                                userMixtapes = MixtapeManager.loadMixtapes(context)
                                if (currentTape?.id == inlayTape.id) {
                                    currentTape = playlistTapes.firstOrNull()
                                }
                                currentScreen = CurrentAppView.TAPE_SELECTION
                            }
                        } else null,
                        onBack = {
                            currentScreen = CurrentAppView.TAPE_SELECTION
                        }
                    )
                } else {
                    currentScreen = CurrentAppView.TAPE_SELECTION
                }
            }

            CurrentAppView.RECORD_STORE -> {
                RecordStoreScreen(
                    localTracks = allTracks,
                    feedback = feedback,
                    onPlayTrack = { track ->
                        val singleTape = TapeItem(
                            id = "single_${track.id}",
                            title = track.title,
                            subtitle = track.artist,
                            type = TapeType.ALBUM,
                            tracks = listOf(track),
                            isSpotify = false,
                            isHomeMade = false
                        )
                        currentTape = singleTape
                        currentTapeShellResId = cassetteVariants.random()
                        activeTrackIndex = 0
                        isTapeInserted = true
                        isTapeInserting = true
                        currentScreen = CurrentAppView.DECK
                        playTrack(track, tape = singleTape)
                    },
                    onPlayTape = { tape ->
                        currentTape = tape
                        currentTapeShellResId = cassetteVariants.random()
                        activeTrackIndex = 0
                        isTapeInserted = true
                        isTapeInserting = true
                        currentScreen = CurrentAppView.DECK
                        tape.tracks.firstOrNull()?.let { firstTrack ->
                            playTrack(firstTrack, tape = tape)
                        }
                    },
                    onAddToMixtape = { track ->
                        trackToRecord = track
                        showRecordDialog = true
                    },
                    onBack = {
                        currentScreen = CurrentAppView.DECK
                    }
                )
            }
        }
    }

    if (showRecordDialog && trackToRecord != null) {
        RecordToMixtapeDialog(
            track = trackToRecord!!,
            userMixtapes = userMixtapes,
            feedback = feedback,
            onAddToMixtape = { mixtapeId ->
                MixtapeManager.addTrackToMixtape(context, mixtapeId, trackToRecord!!)
                userMixtapes = MixtapeManager.loadMixtapes(context)
                if (currentTape?.id == mixtapeId) {
                    currentTape = userMixtapes.firstOrNull { it.id == mixtapeId }
                }
                if (selectedInlayTape?.id == mixtapeId) {
                    selectedInlayTape = userMixtapes.firstOrNull { it.id == mixtapeId }
                }
                showRecordDialog = false
            },
            onCreateAndAdd = { title ->
                val newTape = MixtapeManager.createMixtape(context, title, trackToRecord!!)
                userMixtapes = MixtapeManager.loadMixtapes(context)
                if (currentTape?.id == newTape.id) {
                    currentTape = newTape
                }
                showRecordDialog = false
            },
            onDismiss = {
                showRecordDialog = false
            }
        )
    }
}

@Composable
fun DeckScreen(
    currentTape: TapeItem?,
    activeTrack: Track?,
    activeTrackIndex: Int,
    totalTracksInTape: Int,
    tapeShellResId: Int = R.drawable.deck_cassette_shell,
    isTapeInserted: Boolean,
    isInserting: Boolean = false,
    isEjecting: Boolean = false,
    onEjectAnimationFinished: (() -> Unit)? = null,
    onInsertAnimationFinished: (() -> Unit)? = null,
    isPlaying: Boolean,
    isPaused: Boolean,
    isSpotifyLoggedIn: Boolean = false,
    spotifyPlayerState: com.spotify.protocol.types.PlayerState? = null,
    currentPositionMs: Long,
    totalDurationMs: Long,
    feedback: MechanicalFeedback? = null,
    isCueing: Boolean = false,
    onSpotifyClicked: () -> Unit = {},
    onRecordClicked: () -> Unit = {},
    onRecordStoreClicked: () -> Unit = {},
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

    val currentSpotifyTrack = spotifyPlayerState?.track
    val isSpotifyTape = currentTape?.isSpotify == true
    val tapeTitleStr = currentTape?.title?.trim() ?: ""

    // Strictly prevents playlist name or generic place-holders from ever polluting Track or Artist readouts
    fun isForbiddenText(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        val trimmed = text.trim()
        if (tapeTitleStr.isNotBlank() && trimmed.equals(tapeTitleStr, ignoreCase = true)) return true
        val lower = trimmed.lowercase()
        return lower == "spotify playlist" ||
               lower == "spotify artist" ||
               lower == "spotify mixtape" ||
               lower == "unknown" ||
               lower == "unknown playlist" ||
               lower == "track" ||
               lower.startsWith("spotify mix")
    }

    // 1. Authoritative Spotify App Remote track info (if currently connected & streaming)
    val liveSpotifyTrackName = currentSpotifyTrack?.name?.takeIf { !isForbiddenText(it) }
    val liveSpotifyArtistName = currentSpotifyTrack?.artist?.name?.takeIf { !isForbiddenText(it) }

    // 2. Authentic active track from cassette tracklist
    val authenticTrackTitle = activeTrack?.title?.takeIf { !isForbiddenText(it) }
    val authenticArtistName = activeTrack?.artist?.takeIf { !isForbiddenText(it) }

    val trackTitle = when {
        !isTapeInserted -> "PLEASE INSERT TAPE"
        // Spotify App Remote live track is authoritative when playing/streaming:
        isSpotifyTape && liveSpotifyTrackName != null -> liveSpotifyTrackName
        // Authentic track from tape tracklist:
        authenticTrackTitle != null -> authenticTrackTitle
        // Fallback: strictly generic track number, NEVER the playlist name!
        else -> "TRACK ${activeTrackIndex + 1}"
    }

    val artistText = when {
        !isTapeInserted -> "----"
        // Spotify App Remote live artist is authoritative when playing/streaming:
        isSpotifyTape && liveSpotifyArtistName != null -> liveSpotifyArtistName
        // Authentic artist from tape tracklist:
        authenticArtistName != null -> authenticArtistName
        currentTape?.type == TapeType.ARTIST_ALL -> currentTape.subtitle.takeIf { !isForbiddenText(it) } ?: "ARTIST DISCOGRAPHY"
        currentTape?.type == TapeType.ALBUM -> currentTape.subtitle.takeIf { !isForbiddenText(it) } ?: "ALBUM"
        // Fallback: strictly audio source indicator, NEVER the playlist name!
        isSpotifyTape -> "SPOTIFY AUDIO"
        else -> "LOCAL STEREO"
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
            painter = pixelPainterResource(id = bgResId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // FOREGROUND CONTENT LAYER: Exact 1:1 match with Android Studio Visual Layout Editor
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val context = LocalContext.current
            val skin = remember { SkinManager.loadSkin(context) }
            val vp = if (isLandscape) skin.landscape else skin.portrait
            val scaleX = if (vp.canvasWidth > 0f) maxWidth.value / vp.canvasWidth else 1f
            val scaleY = if (vp.canvasHeight > 0f) maxHeight.value / vp.canvasHeight else 1f

            fun com.retro.pixelplayer.skin.SkinElement.dpX() = (this.x * scaleX).dp
            fun com.retro.pixelplayer.skin.SkinElement.dpY() = (this.y * scaleY).dp
            fun com.retro.pixelplayer.skin.SkinElement.dpW() = (this.width * scaleX).dp
            fun com.retro.pixelplayer.skin.SkinElement.dpH() = (this.height * scaleY).dp

            // 1. Overlay Header Bar: floats at top within status bar insets, without consuming layout height
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 14.dp, vertical = if (isLandscape) 4.dp else 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "◆ SUNSET CASSETTE ◆",
                    color = PixelPalette.ChassisHighlight,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = if (isLandscape) 12.sp else 11.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MechanicalButton(
                        label = "🔴 REC",
                        baseColor = Color(0xFFB71C1C),
                        highlightColor = Color(0xFFE53935),
                        shadowColor = Color(0xFF7F0000),
                        width = if (isLandscape) 64.dp else 56.dp,
                        height = if (isLandscape) 25.dp else 28.dp,
                        onDownPress = { feedback?.onTapeLatch() },
                        onClick = { onRecordClicked() }
                    )

                    MechanicalButton(
                        label = "📻 STORE",
                        baseColor = Color(0xFFE65100),
                        highlightColor = Color(0xFFFF9800),
                        shadowColor = Color(0xFFBF360C),
                        width = if (isLandscape) 68.dp else 58.dp,
                        height = if (isLandscape) 25.dp else 28.dp,
                        onDownPress = { feedback?.onTick() },
                        onClick = { onRecordStoreClicked() }
                    )

                    MechanicalButton(
                        label = if (isSpotifyLoggedIn) "🟢 LINKED" else "🟢 SPOTIFY",
                        baseColor = if (isSpotifyLoggedIn) Color(0xFF1DB954) else Color(0xFF1B5E20),
                        highlightColor = if (isSpotifyLoggedIn) Color(0xFF4ADE80) else Color(0xFF2E7D32),
                        shadowColor = Color(0xFF0A2910),
                        labelColor = if (isSpotifyLoggedIn) Color(0xFF0A140E) else Color(0xFFE0E0E0),
                        width = if (isLandscape) 78.dp else 68.dp,
                        height = if (isLandscape) 25.dp else 28.dp,
                        onDownPress = { feedback?.onTick() },
                        onClick = { onSpotifyClicked() }
                    )

                    MechanicalButton(
                        label = "TAPES 📚",
                        baseColor = Color(0xFF4E342E),
                        highlightColor = Color(0xFF6D4C41),
                        shadowColor = Color(0xFF3E2723),
                        width = if (isLandscape) 74.dp else 64.dp,
                        height = if (isLandscape) 25.dp else 28.dp,
                        onDownPress = { feedback?.onTick() },
                        onClick = { onBrowseTapesClicked() }
                    )
                }
            }

            // 0. Tape Deck Bevels
            val bayBevelsEl = vp.getElement("chassis_bay_bevels")
            bayBevelsEl?.let { el ->
                Image(
                    painter = pixelPainterResource(id = R.drawable.chassis_bay_bevels),
                    contentDescription = "Tape Deck Bevels",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = el.dpX(), y = el.dpY())
                        .size(width = el.dpW(), height = el.dpH()),
                    contentScale = ContentScale.FillBounds
                )
            }

            // 1. Cassette Deck Bay
            val isOpenDeck = !isTapeInserted || isInserting || isEjecting
            val baseDoorEl = vp.getElement("cassette_bay") ?: vp.getElement("cassette_door")
            val activeDoorEl = if (isOpenDeck) (vp.getElement("cassette_bay_open") ?: baseDoorEl) else baseDoorEl
            val openDoorEl = vp.getElement("deck_door_open")

            activeDoorEl?.let { doorEl ->
                CassetteDeck(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = doorEl.dpX(), y = doorEl.dpY())
                        .size(width = doorEl.dpW(), height = doorEl.dpH()),
                    isTapeInserted = isTapeInserted,
                    shellResId = tapeShellResId,
                    isPlaying = isPlaying,
                    tapeTitle = tapeTitle,
                    tapeSubtitle = tapeSubtitle,
                    labelColor = tapeLabelColor,
                    shellColor = tapeShellColor,
                    isHomeMade = currentTape?.isHomeMade ?: false,
                    artworkBitmap = currentTape?.artworkBitmap,
                    isInserting = isInserting,
                    isEjecting = isEjecting,
                    onEjectAnimationFinished = onEjectAnimationFinished,
                    onInsertAnimationFinished = onInsertAnimationFinished,
                    onEjectOrInsert = { onTapeDeckTapped() }
                )
            }

            // Open Cassette Door (visible only when deck is open, locks 1:1 with cassette bay coordinates)
            if (isOpenDeck && openDoorEl != null) {
                Image(
                    painter = pixelPainterResource(id = R.drawable.deck_door_open_front),
                    contentDescription = "Open Cassette Door",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = openDoorEl.dpX(), y = openDoorEl.dpY())
                        .size(width = openDoorEl.dpW(), height = openDoorEl.dpH())
                        .clickable { onTapeDeckTapped() },
                    contentScale = ContentScale.FillBounds
                )
            }

            // 2. Speaker Vents
            val ventsEl = vp.getElement("speaker_vents")
            ventsEl?.let { el ->
                Image(
                    painter = pixelPainterResource(id = R.drawable.chassis_speaker_vents),
                    contentDescription = "Speaker Vents",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = el.dpX(), y = el.dpY())
                        .size(width = el.dpW(), height = el.dpH()),
                    contentScale = ContentScale.FillBounds
                )
            }

            // 3a. LCD Bevels
            val lcdBevelsEl = vp.getElement("chassis_lcd_bevels")
            lcdBevelsEl?.let { el ->
                Image(
                    painter = pixelPainterResource(id = R.drawable.chassis_lcd_bevels),
                    contentDescription = "LCD Bevels",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = el.dpX(), y = el.dpY())
                        .size(width = el.dpW(), height = el.dpH()),
                    contentScale = ContentScale.FillBounds
                )
            }

            // 3b. Mint-Green LCD Display
            val lcdEl = vp.getElement("lcd_display")
            lcdEl?.let { el ->
                LcdDisplay(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = el.dpX(), y = el.dpY())
                        .size(width = el.dpW(), height = el.dpH()),
                    trackTitle = trackTitle,
                    artist = artistText,
                    source = when {
                        currentTape?.isSpotify == true -> "SPOTIFY"
                        currentTape?.isHomeMade == true -> "MIXTAPE"
                        else -> "ALBUM"
                    },
                    timeText = formattedTime,
                    statusText = statusText,
                    isCueing = isCueing
                )
            }

            // 4. Shuffle Switch
            val shuffleEl = vp.getElement("switch_shuffle")
            shuffleEl?.let { el ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = el.dpX(), y = el.dpY())
                ) {
                    PixelArtButton(
                        unpressedResId = if (isShuffleOn) R.drawable.switch_shuffle_on else R.drawable.switch_shuffle_off,
                        pressedResId = if (isShuffleOn) R.drawable.switch_shuffle_on else R.drawable.switch_shuffle_off,
                        width = el.dpW(),
                        height = el.dpH(),
                        onDownPress = { feedback?.onTick() },
                        onClick = {
                            isShuffleOn = !isShuffleOn
                            onShuffleToggle(isShuffleOn)
                        }
                    )
                }
            }

            // 5. Repeat Switch
            val repeatEl = vp.getElement("switch_repeat")
            repeatEl?.let { el ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = el.dpX(), y = el.dpY())
                ) {
                    PixelArtButton(
                        unpressedResId = if (isRepeatOn) R.drawable.switch_repeat_on else R.drawable.switch_repeat_off,
                        pressedResId = if (isRepeatOn) R.drawable.switch_repeat_on else R.drawable.switch_repeat_off,
                        width = el.dpW(),
                        height = el.dpH(),
                        onDownPress = { feedback?.onTick() },
                        onClick = {
                            isRepeatOn = !isRepeatOn
                            onRepeatToggle(isRepeatOn)
                        }
                    )
                }
            }

            // 5b. Button Backing
            val btnBackingEl = vp.getElement("chassis_btn_backing")
            btnBackingEl?.let { el ->
                Image(
                    painter = pixelPainterResource(id = R.drawable.chassis_btn_backing),
                    contentDescription = "Button Backing",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = el.dpX(), y = el.dpY())
                        .size(width = el.dpW(), height = el.dpH()),
                    contentScale = ContentScale.FillBounds
                )
            }

            // 6. Transport Buttons Bank (Direct 1:1 placement matching designer)
            SkinTransportControls(
                vp = vp,
                scaleX = scaleX,
                scaleY = scaleY,
                isPlaying = isPlaying,
                isTapeRunning = isTapeRunning,
                feedback = feedback,
                onPlay = onPlay,
                onStop = onStop,
                onSkipPrev = onSkipPrev,
                onSkip = onSkip,
                onEject = onEject,
                onSeek = onSeek
            )
        }
    }
}

@Composable
private fun BoxScope.SkinTransportControls(
    vp: com.retro.pixelplayer.skin.ViewportSkin,
    scaleX: Float,
    scaleY: Float,
    isPlaying: Boolean,
    isTapeRunning: Boolean,
    feedback: MechanicalFeedback?,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkip: () -> Unit,
    onEject: () -> Unit,
    onSeek: (Long) -> Unit
) {
    fun com.retro.pixelplayer.skin.SkinElement.dpX() = (this.x * scaleX).dp
    fun com.retro.pixelplayer.skin.SkinElement.dpY() = (this.y * scaleY).dp
    fun com.retro.pixelplayer.skin.SkinElement.dpW() = (this.width * scaleX).dp
    fun com.retro.pixelplayer.skin.SkinElement.dpH() = (this.height * scaleY).dp

    val skipPrevEl = vp.getElement("btn_skip_prev")
    val rewindEl = vp.getElement("btn_rewind")
    val playEl = vp.getElement("btn_play")
    val fastFwdEl = vp.getElement("btn_fast_fwd")
    val skipNextEl = vp.getElement("btn_skip_next")
    val stopEl = vp.getElement("btn_stop")
    val ejectEl = vp.getElement("btn_eject")

    // 1. Skip Prev
    skipPrevEl?.let { el ->
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = el.dpX(), y = el.dpY())
        ) {
            PixelArtButton(
                unpressedResId = R.drawable.btn_skip_prev_unpressed,
                pressedResId = R.drawable.btn_skip_prev_pressed,
                width = el.dpW(),
                height = el.dpH(),
                onDownPress = { feedback?.onTick() },
                onClick = { onSkipPrev() }
            )
        }
    }

    // 2. Rewind
    rewindEl?.let { el ->
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = el.dpX(), y = el.dpY())
        ) {
            PixelArtButton(
                unpressedResId = R.drawable.btn_rewind_unpressed,
                pressedResId = R.drawable.btn_rewind_pressed,
                width = el.dpW(),
                height = el.dpH(),
                onDownPress = { feedback?.onRewindPress() },
                onHoldRepeat = {
                    feedback?.onTick()
                    onSeek(-5_000L)
                },
                onClick = {
                    onSeek(-10_000L)
                }
            )
        }
    }

    // 3. Play
    playEl?.let { el ->
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = el.dpX(), y = el.dpY())
        ) {
            PixelArtButton(
                unpressedResId = R.drawable.btn_play_unpressed,
                pressedResId = R.drawable.btn_play_pressed,
                isLatched = isPlaying,
                width = el.dpW(),
                height = el.dpH(),
                onDownPress = { feedback?.onPlayPress() },
                onClick = { onPlay() }
            )
        }
    }

    // 4. Fast Forward
    fastFwdEl?.let { el ->
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = el.dpX(), y = el.dpY())
        ) {
            PixelArtButton(
                unpressedResId = R.drawable.btn_fast_fwd_unpressed,
                pressedResId = R.drawable.btn_fast_fwd_pressed,
                width = el.dpW(),
                height = el.dpH(),
                onDownPress = { feedback?.onRewindPress() },
                onHoldRepeat = {
                    feedback?.onTick()
                    onSeek(5_000L)
                },
                onClick = {
                    onSeek(10_000L)
                }
            )
        }
    }

    // 5. Skip Next
    skipNextEl?.let { el ->
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = el.dpX(), y = el.dpY())
        ) {
            PixelArtButton(
                unpressedResId = R.drawable.btn_skip_next_unpressed,
                pressedResId = R.drawable.btn_skip_next_pressed,
                width = el.dpW(),
                height = el.dpH(),
                onDownPress = { feedback?.onTick() },
                onClick = { onSkip() }
            )
        }
    }

    // 6. Stop / Dual-purpose Stop-Eject
    val isSeparateEject = ejectEl != null && stopEl != null &&
        (kotlin.math.abs(ejectEl.x - stopEl.x) > 20f || kotlin.math.abs(ejectEl.y - stopEl.y) > 20f)

    stopEl?.let { el ->
        val showStop = isSeparateEject || isTapeRunning
        val btnW = if (!showStop && ejectEl != null) ejectEl.dpW() else el.dpW()
        val btnH = if (!showStop && ejectEl != null) ejectEl.dpH() else el.dpH()
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = el.dpX(), y = el.dpY())
        ) {
            if (showStop) {
                PixelArtButton(
                    unpressedResId = R.drawable.btn_stop_unpressed,
                    pressedResId = R.drawable.btn_stop_pressed,
                    width = btnW,
                    height = btnH,
                    onDownPress = { feedback?.onStopPress() },
                    onClick = { onStop() }
                )
            } else {
                PixelArtButton(
                    unpressedResId = R.drawable.btn_eject_unpressed,
                    pressedResId = R.drawable.btn_eject_pressed,
                    width = btnW,
                    height = btnH,
                    onDownPress = { feedback?.onTapeLatch() },
                    onClick = { onEject() }
                )
            }
        }
    }

    // 7. Eject (rendered only if placed as a separate distinct button, or if stopEl doesn't exist)
    if (isSeparateEject || (stopEl == null && ejectEl != null)) {
        ejectEl?.let { el ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = el.dpX(), y = el.dpY())
            ) {
                PixelArtButton(
                    unpressedResId = R.drawable.btn_eject_unpressed,
                    pressedResId = R.drawable.btn_eject_pressed,
                    width = el.dpW(),
                    height = el.dpH(),
                    onDownPress = { feedback?.onTapeLatch() },
                    onClick = { onEject() }
                )
            }
        }
    }
}


@Composable
private fun TransportButtonsRow(
    modifier: Modifier = Modifier,
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
    BoxWithConstraints(modifier = modifier) {
        val rowW = maxWidth
        // Exact 1:1 button proportions matching designer: 48px square buttons, 62px play button
        val wSkip = rowW * (48f / 342f)
        val wRewind = rowW * (48f / 342f)
        val wPlay = rowW * (62f / 342f)
        val wFastFwd = rowW * (48f / 342f)
        val wSkipNext = rowW * (48f / 342f)
        val wEject = rowW * (48f / 342f)

        val hStd = rowW * (48f / 342f)
        val hPlay = rowW * (48f / 342f)

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
        album = "Sunset Soundwaves",
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
        album = "Sunset Soundwaves",
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



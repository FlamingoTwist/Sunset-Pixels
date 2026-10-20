package com.retro.pixelplayer.spotify

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Repeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages Spotify playback seamlessly in the background without EVER switching
 * focus or launching the Spotify app UI to the foreground.
 *
 * All commands are strictly authoritative and routed through the official Spotify App Remote SDK
 * with background Web API fallback. NEVER sends rogue or unsolicited media key events.
 */
object SpotifyRemoteManager {

    private const val TAG = "SpotifyRemote"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var appRemote: SpotifyAppRemote? = null
    private var playerStateSubscription: Subscription<PlayerState>? = null

    private val _playerState = MutableStateFlow<PlayerState?>(null)
    val playerState: StateFlow<PlayerState?> = _playerState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _needsAuthorization = MutableStateFlow(false)
    val needsAuthorization: StateFlow<Boolean> = _needsAuthorization.asStateFlow()

    @Volatile
    private var isConnecting = false
    private var pendingPlayUri: String? = null
    private var pendingContextUri: String? = null
    private var pendingTrackIndex: Int? = null
    private var pendingTrackUris: List<String>? = null

    fun isInstalled(context: Context): Boolean {
        return try {
            SpotifyAppRemote.isSpotifyInstalled(context)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Binds strictly to Spotify's background service. showAuthView is ALWAYS false
     * by default so that the Spotify application UI is never brought to the foreground.
     * showAuthView is only passed as true when the user explicitly taps "AUTHORIZE SPOTIFY PLAYBACK" in settings.
     *
     * Connecting to App Remote is strictly read-only and NEVER triggers audio playback.
     */
    fun connect(
        context: Context,
        showAuthView: Boolean = false,
        onConnected: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        if (appRemote?.isConnected == true) {
            _isConnected.value = true
            appRemote?.playerApi?.playerState?.setResultCallback { state ->
                if (state != null) {
                    _playerState.value = state
                }
            }
            onConnected?.invoke()
            return
        }

        if (isConnecting) return
        isConnecting = true

        val clientId = SpotifyManager.getSavedClientId(context)
        val params = ConnectionParams.Builder(clientId)
            .setRedirectUri(SpotifyManager.REDIRECT_URI)
            .showAuthView(showAuthView)
            .build()

        val connectContext = if (showAuthView && context is android.app.Activity) context else context.applicationContext

        SpotifyAppRemote.connect(connectContext, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                isConnecting = false
                appRemote = remote
                _isConnected.value = true
                _needsAuthorization.value = false
                remote.playerApi.playerState?.setResultCallback { state ->
                    if (state != null) {
                        _playerState.value = state
                    }
                }
                Log.d(TAG, "SpotifyAppRemote successfully connected!")

                // Subscribe to real-time player state updates (position, duration, isPaused, track info)
                playerStateSubscription?.cancel()
                playerStateSubscription = remote.playerApi.subscribeToPlayerState().apply {
                    setEventCallback { state ->
                        _playerState.value = state
                    }
                    setErrorCallback { error ->
                        Log.w(TAG, "PlayerState subscription warning: ${error.message}")
                    }
                }

                // If a track was explicitly queued by the user while connecting, play it now
                val queuedUri = pendingPlayUri
                if (queuedUri != null) {
                    val ctx = pendingContextUri
                    val idx = pendingTrackIndex
                    val uris = pendingTrackUris
                    pendingPlayUri = null
                    pendingContextUri = null
                    pendingTrackIndex = null
                    pendingTrackUris = null
                    play(context, queuedUri, ctx, idx, uris)
                }

                onConnected?.invoke()
            }

            override fun onFailure(throwable: Throwable) {
                isConnecting = false
                _isConnected.value = false
                val isAuthRequired = throwable.message?.contains("Explicit user authorization is required", ignoreCase = true) == true
                if (isAuthRequired) {
                    _needsAuthorization.value = true
                }
                Log.w(TAG, "SpotifyAppRemote connection failure (isAuthRequired=$isAuthRequired): ${throwable.message}")

                // Clear pending play parameters so they don't linger
                val queuedUri = pendingPlayUri
                val ctx = pendingContextUri
                val idx = pendingTrackIndex
                val uris = pendingTrackUris
                pendingPlayUri = null
                pendingContextUri = null
                pendingTrackIndex = null
                pendingTrackUris = null

                // If a track was queued by the user, fall back silently to Spotify Web API playback
                if (queuedUri != null) {
                    scope.launch {
                        SpotifyManager.playSpotifyTrack(
                            context = context,
                            spotifyUri = queuedUri,
                            contextUri = ctx,
                            trackIndex = idx,
                            allTrackUris = uris
                        )
                    }
                }

                onError?.invoke(throwable)
            }
        })
    }

    /**
     * Queries the latest player state from Spotify App Remote without starting playback.
     */
    fun refreshState(context: Context) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.playerState?.setResultCallback { state ->
                if (state != null) {
                    _playerState.value = state
                }
            }
        } else if (isInstalled(context)) {
            connect(context)
        }
    }

    fun play(
        context: Context,
        spotifyUri: String,
        contextUri: String? = null,
        trackIndex: Int? = null,
        allTrackUris: List<String>? = null
    ) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.play(spotifyUri)
                .setErrorCallback { error ->
                    Log.w(TAG, "playerApi.play error: ${error.message}")
                }
        } else {
            pendingPlayUri = spotifyUri
            pendingContextUri = contextUri
            pendingTrackIndex = trackIndex
            pendingTrackUris = allTrackUris

            if (isInstalled(context)) {
                connect(context)
            } else {
                scope.launch {
                    SpotifyManager.playSpotifyTrack(
                        context = context,
                        spotifyUri = spotifyUri,
                        contextUri = contextUri,
                        trackIndex = trackIndex,
                        allTrackUris = allTrackUris
                    )
                }
            }
        }
    }

    fun pause(context: Context) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.pause()
        } else {
            scope.launch {
                SpotifyManager.pauseSpotify(context)
            }
        }
    }

    fun resume(context: Context) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.resume()
        } else {
            if (isInstalled(context)) {
                connect(context)
            }
            scope.launch {
                SpotifyManager.resumeSpotify(context)
            }
        }
    }

    fun skipNext(context: Context, nextTrackUri: String? = null) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            if (!nextTrackUri.isNullOrBlank()) {
                remote.playerApi.play(nextTrackUri)
            } else {
                remote.playerApi.skipNext().setResultCallback {
                    remote.playerApi.playerState?.setResultCallback { state ->
                        if (state != null) {
                            _playerState.value = state
                        }
                    }
                }
            }
        } else {
            if (!nextTrackUri.isNullOrBlank()) {
                play(context, nextTrackUri)
            } else {
                scope.launch {
                    SpotifyManager.skipSpotifyNext(context)
                }
            }
        }
    }

    fun skipPrevious(context: Context, prevTrackUri: String? = null) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            if (!prevTrackUri.isNullOrBlank()) {
                remote.playerApi.play(prevTrackUri)
            } else {
                remote.playerApi.skipPrevious().setResultCallback {
                    remote.playerApi.playerState?.setResultCallback { state ->
                        if (state != null) {
                            _playerState.value = state
                        }
                    }
                }
            }
        } else {
            if (!prevTrackUri.isNullOrBlank()) {
                play(context, prevTrackUri)
            } else {
                scope.launch {
                    SpotifyManager.skipSpotifyPrev(context)
                }
            }
        }
    }

    fun seekTo(context: Context, positionMs: Long) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.seekTo(positionMs)
        } else {
            scope.launch {
                SpotifyManager.seekSpotify(context, positionMs)
            }
        }
    }

    fun setShuffle(context: Context, enabled: Boolean) {
        appRemote?.playerApi?.setShuffle(enabled)
        scope.launch {
            SpotifyManager.setSpotifyShuffle(context, enabled)
        }
    }

    fun setRepeat(context: Context, enabled: Boolean) {
        val mode = if (enabled) Repeat.ONE else Repeat.OFF
        appRemote?.playerApi?.setRepeat(mode)
        scope.launch {
            SpotifyManager.setSpotifyRepeat(context, enabled)
        }
    }

    fun disconnect() {
        try {
            playerStateSubscription?.cancel()
            playerStateSubscription = null
            appRemote?.let {
                if (it.isConnected) {
                    SpotifyAppRemote.disconnect(it)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "disconnect warning: ${e.message}")
        } finally {
            appRemote = null
            _isConnected.value = false
            _playerState.value = null
        }
    }
}

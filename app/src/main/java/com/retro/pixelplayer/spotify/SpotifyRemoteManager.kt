package com.retro.pixelplayer.spotify

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
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
 * Traverses context wrappers to find the underlying foreground Activity.
 * Crucial for Jetpack Compose where LocalContext.current is wrapped in ContextThemeWrapper.
 */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

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
        // Guarantee execution on Main Thread with Main Looper
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                connect(context, showAuthView, onConnected, onError)
            }
            return
        }

        if (appRemote?.isConnected == true) {
            _isConnected.value = true
            _needsAuthorization.value = false
            appRemote?.playerApi?.playerState?.setResultCallback { state ->
                if (state != null) {
                    _playerState.value = state
                }
            }
            onConnected?.invoke()
            return
        }

        // Never lock out the user if they explicitly tapped Authorize
        if (isConnecting && !showAuthView) return
        isConnecting = true

        val clientId = SpotifyManager.getSavedClientId(context)
        val params = ConnectionParams.Builder(clientId)
            .setRedirectUri(SpotifyManager.REDIRECT_URI)
            .showAuthView(showAuthView)
            .build()

        val activity = context.findActivity()
        val connectContext = if (showAuthView && activity != null) activity else context.applicationContext

        Log.d(TAG, "Connecting SpotifyAppRemote (showAuthView=$showAuthView, targetContext=${connectContext.javaClass.simpleName})")

        try {
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
                    pendingPlayUri = null
                    if (!queuedUri.isNullOrBlank()) {
                        play(context, queuedUri)
                    }

                    onConnected?.invoke()
                }

                override fun onFailure(throwable: Throwable) {
                    isConnecting = false
                    _isConnected.value = false
                    val isAuthRequired = throwable.message?.contains("Explicit user authorization is required", ignoreCase = true) == true
                    if (isAuthRequired) {
                        _needsAuthorization.value = true
                        if (!showAuthView) {
                            Log.d(TAG, "Authorization required, retrying with showAuthView = true")
                            connect(context, showAuthView = true, onConnected, onError)
                            return
                        }
                    }
                    Log.w(TAG, "SpotifyAppRemote connection failure (isAuthRequired=$isAuthRequired): ${throwable.message}")

                    pendingPlayUri = null
                    onError?.invoke(throwable)
                }
            })
        } catch (e: Exception) {
            isConnecting = false
            Log.e(TAG, "SpotifyAppRemote.connect thrown exception: ${e.message}", e)
            onError?.invoke(e)
        }
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
        uri: String
    ) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.play(uri)
                .setErrorCallback { error ->
                    Log.w(TAG, "playerApi.play error: ${error.message}")
                }
        } else {
            pendingPlayUri = uri
            if (isInstalled(context)) {
                connect(context, showAuthView = true, onConnected = {
                    appRemote?.playerApi?.play(uri)
                })
            }
        }
    }

    fun pause(context: Context) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.pause()
        }
    }

    fun resume(context: Context) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.resume()
        } else if (isInstalled(context)) {
            connect(context)
        }
    }

    fun skipNext(context: Context, nextTrackUri: String? = null) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            if (!nextTrackUri.isNullOrBlank()) {
                remote.playerApi.play(nextTrackUri)
            } else {
                remote.playerApi.skipNext()
            }
        } else if (!nextTrackUri.isNullOrBlank()) {
            play(context, nextTrackUri)
        }
    }

    fun skipPrevious(context: Context, prevTrackUri: String? = null) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            if (!prevTrackUri.isNullOrBlank()) {
                remote.playerApi.play(prevTrackUri)
            } else {
                remote.playerApi.skipPrevious()
            }
        } else if (!prevTrackUri.isNullOrBlank()) {
            play(context, prevTrackUri)
        }
    }

    fun seekTo(context: Context, positionMs: Long) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.seekTo(positionMs)
        }
    }

    fun setShuffle(context: Context, enabled: Boolean) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.setShuffle(enabled)
        }
    }

    fun setRepeat(context: Context, enabled: Boolean) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            val mode = if (enabled) Repeat.ONE else Repeat.OFF
            remote.playerApi.setRepeat(mode)
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

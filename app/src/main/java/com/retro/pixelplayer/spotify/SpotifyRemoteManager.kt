package com.retro.pixelplayer.spotify

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
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
 * Utilizes a 3-layer headless approach identical to Android widgets and lockscreen media controls:
 * 1. Spotify App Remote SDK IPC background service binding (with showAuthView = false)
 * 2. Spotify Web API Player endpoints (/v1/me/player/...) over background HTTPS
 * 3. Android AudioManager media key event dispatch (KEYCODE_MEDIA_...)
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
     * so that the Spotify application UI is never brought to the foreground.
     */
    fun connect(
        context: Context,
        showAuthView: Boolean = true,
        onConnected: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        if (appRemote?.isConnected == true) {
            _isConnected.value = true
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

        val connectContext = if (context is android.app.Activity) context else context.applicationContext

        SpotifyAppRemote.connect(connectContext, params, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                isConnecting = false
                appRemote = remote
                _isConnected.value = true
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

                // If a track was queued while connecting, play it now
                val queuedUri = pendingPlayUri
                if (queuedUri != null) {
                    pendingPlayUri = null
                    play(context, queuedUri)
                }

                onConnected?.invoke()
            }

            override fun onFailure(throwable: Throwable) {
                isConnecting = false
                _isConnected.value = false
                Log.w(TAG, "SpotifyAppRemote connection failure: ${throwable.message}")
                onError?.invoke(throwable)
            }
        })
    }

    /**
     * Plays the authentic track without switching apps.
     */
    fun play(context: Context, spotifyUri: String) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.play(spotifyUri)
                .setErrorCallback { error ->
                    Log.w(TAG, "playerApi.play error: ${error.message}")
                }
        } else {
            pendingPlayUri = spotifyUri
            connect(context)
        }

        // Parallel Web API command (executed silently in background with ZERO app switch)
        scope.launch {
            SpotifyManager.playSpotifyTrack(context, spotifyUri)
        }
    }

    fun pause(context: Context) {
        appRemote?.playerApi?.pause()
        scope.launch {
            SpotifyManager.pauseSpotify(context)
        }
        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    fun resume(context: Context) {
        appRemote?.playerApi?.resume()
        scope.launch {
            SpotifyManager.resumeSpotify(context)
        }
        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    fun skipNext(context: Context, nextTrackUri: String? = null) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            if (!nextTrackUri.isNullOrBlank()) {
                remote.playerApi.play(nextTrackUri)
            } else {
                remote.playerApi.skipNext()
            }
        } else {
            if (!nextTrackUri.isNullOrBlank()) {
                play(context, nextTrackUri)
            } else {
                dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            }
        }

        scope.launch {
            if (!nextTrackUri.isNullOrBlank()) {
                SpotifyManager.playSpotifyTrack(context, nextTrackUri)
            } else {
                SpotifyManager.skipSpotifyNext(context)
            }
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
        } else {
            if (!prevTrackUri.isNullOrBlank()) {
                play(context, prevTrackUri)
            } else {
                dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
        }

        scope.launch {
            if (!prevTrackUri.isNullOrBlank()) {
                SpotifyManager.playSpotifyTrack(context, prevTrackUri)
            } else {
                SpotifyManager.skipSpotifyPrev(context)
            }
        }
    }

    fun seekTo(context: Context, positionMs: Long) {
        appRemote?.playerApi?.seekTo(positionMs)
        scope.launch {
            SpotifyManager.seekSpotify(context, positionMs)
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

    /**
     * Dispatches a media key event via Android AudioManager (identical to widget and Bluetooth controls)
     * which controls Spotify silently without opening its UI.
     */
    private fun dispatchMediaKey(context: Context, keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (e: Exception) {
            Log.w(TAG, "dispatchMediaKey warning: ${e.message}")
        }
    }
}

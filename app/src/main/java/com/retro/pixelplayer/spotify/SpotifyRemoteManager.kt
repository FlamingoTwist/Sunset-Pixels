package com.retro.pixelplayer.spotify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.delay
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
     * Checks whether Spotify is installed but dormant (disconnected, sleeping in background).
     */
    fun isDormant(context: Context): Boolean {
        if (!isInstalled(context)) return false
        return appRemote?.isConnected != true
    }

    /**
     * Wakes Spotify headlessly in the background using Android's OS-level MEDIA_BUTTON
     * intent allowlist. Resurrects com.spotify.music's background playback process without EVER
     * switching apps or bringing the Spotify UI to the foreground.
     */
    fun wakeSpotifyHeadless(context: Context) {
        try {
            val pm = context.packageManager
            val mediaIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setPackage("com.spotify.music")
            }
            val receivers = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.queryBroadcastReceivers(mediaIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryBroadcastReceivers(mediaIntent, 0)
            }

            if (receivers.isNotEmpty()) {
                for (resolveInfo in receivers) {
                    val component = ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
                    val explicitDown = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        this.component = component
                        putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                    }
                    context.sendBroadcast(explicitDown)
                    val explicitUp = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        this.component = component
                        putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
                    }
                    context.sendBroadcast(explicitUp)
                }
            } else {
                val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    setPackage("com.spotify.music")
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                }
                context.sendBroadcast(downIntent)
                val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    setPackage("com.spotify.music")
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
                }
                context.sendBroadcast(upIntent)
            }
            Log.d(TAG, "wakeSpotifyHeadless: Sent wake-up broadcast to com.spotify.music")
        } catch (e: Exception) {
            Log.w(TAG, "wakeSpotifyHeadless warning: ${e.message}")
        }
    }

    /**
     * Binds strictly to Spotify's background service. showAuthView is ALWAYS false
     * by default so that the Spotify application UI is never brought to the foreground.
     * showAuthView is only passed as true when the user explicitly taps "AUTHORIZE SPOTIFY PLAYBACK" in settings.
     */
    fun connect(
        context: Context,
        showAuthView: Boolean = false,
        isRetry: Boolean = false,
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

        // If Spotify is not connected and we are attempting a background connection,
        // fire the headless wake broadcast beforehand so the service is ready
        if (!showAuthView && !isRetry && isInstalled(context)) {
            wakeSpotifyHeadless(context)
        }

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
            appRemote?.playerApi?.playerState?.setResultCallback { state ->
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

                // If a track was queued while connecting, play it now
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
                Log.w(TAG, "SpotifyAppRemote connection failure (isRetry=$isRetry, isAuthRequired=$isAuthRequired): ${throwable.message}")

                // If this was our first background attempt and Spotify was dormant (not an auth rejection),
                // fire wake-up broadcast and retry connection once after a brief delay.
                if (!isRetry && !showAuthView && !isAuthRequired && isInstalled(context)) {
                    Log.d(TAG, "Spotify was likely dormant. Sending wake broadcast and retrying connect in 450ms...")
                    wakeSpotifyHeadless(context)
                    scope.launch {
                        delay(450L)
                        connect(
                            context = context,
                            showAuthView = false,
                            isRetry = true,
                            onConnected = onConnected,
                            onError = onError
                        )
                    }
                    return
                }

                // Clear pending play parameters so they don't linger
                val queuedUri = pendingPlayUri
                val ctx = pendingContextUri
                val idx = pendingTrackIndex
                val uris = pendingTrackUris
                pendingPlayUri = null
                pendingContextUri = null
                pendingTrackIndex = null
                pendingTrackUris = null

                // If a track was queued, fall back 100% silently to Spotify Web API playback
                // NEVER launch the Spotify app UI or start an Activity Intent!
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
     * Plays the authentic track or playlist context without switching apps.
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
            // Directly play authentic track without skipToIndex error-callback fallbacks
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
                // Preemptively wake Spotify headlessly so the IPC service is active
                wakeSpotifyHeadless(context)
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
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
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
                wakeSpotifyHeadless(context)
                connect(context)
            }
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)
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
                dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
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
                dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
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

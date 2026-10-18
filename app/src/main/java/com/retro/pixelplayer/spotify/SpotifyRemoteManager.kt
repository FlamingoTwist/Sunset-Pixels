package com.retro.pixelplayer.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Repeat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages official Spotify playback via the Spotify Android App Remote SDK.
 * Binds directly to the installed Spotify app on the device to stream genuine,
 * studio-quality audio with real-time player position, duration, and playback state.
 */
object SpotifyRemoteManager {

    private const val TAG = "SpotifyRemote"

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
     * Connects to the Spotify app service.
     */
    fun connect(
        context: Context,
        showAuthView: Boolean = false,
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

        SpotifyAppRemote.connect(context.applicationContext, params, object : Connector.ConnectionListener {
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
                Log.w(TAG, "SpotifyAppRemote connect failure: ${throwable.message}")
                onError?.invoke(throwable)
            }
        })
    }

    /**
     * Plays the authentic track corresponding to spotifyUri ("spotify:track:...").
     */
    fun play(context: Context, spotifyUri: String) {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.play(spotifyUri)
                .setErrorCallback { error ->
                    Log.w(TAG, "playerApi.play error: ${error.message}, launching Spotify intent fallback")
                    openSpotifyIntent(context, spotifyUri)
                }
        } else {
            pendingPlayUri = spotifyUri
            connect(
                context = context,
                showAuthView = true,
                onConnected = {
                    // Will play via pendingPlayUri in onConnected
                },
                onError = {
                    openSpotifyIntent(context, spotifyUri)
                }
            )
        }
    }

    fun pause() {
        appRemote?.playerApi?.pause()
    }

    fun resume() {
        appRemote?.playerApi?.resume()
    }

    fun seekTo(positionMs: Long) {
        appRemote?.playerApi?.seekTo(positionMs)
    }

    fun skipNext() {
        appRemote?.playerApi?.skipNext()
    }

    fun skipPrevious() {
        appRemote?.playerApi?.skipPrevious()
    }

    fun setShuffle(enabled: Boolean) {
        appRemote?.playerApi?.setShuffle(enabled)
    }

    fun setRepeat(enabled: Boolean) {
        val mode = if (enabled) Repeat.ONE else Repeat.OFF
        appRemote?.playerApi?.setRepeat(mode)
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

    private fun openSpotifyIntent(context: Context, spotifyUri: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri)).apply {
                setPackage("com.spotify.music")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open Spotify intent: ${e.message}")
        }
    }
}

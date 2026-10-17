package com.retro.pixelplayer.spotify

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.retro.pixelplayer.audio.LocalMusicScanner
import com.retro.pixelplayer.audio.TapeItem
import com.retro.pixelplayer.audio.TapeType
import com.retro.pixelplayer.audio.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

data class SpotifySyncResult(
    val userName: String,
    val artistTapes: Map<String, List<TapeItem>>,
    val playlistTapes: List<TapeItem>,
    val statusMessage: String
)

object SpotifyManager {

    private const val PREFS_NAME = "pixel_spotify_prefs"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_CODE_VERIFIER = "code_verifier"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_USER_NAME = "user_name"

    const val REDIRECT_URI = "pixelplayer://spotify-callback"
    const val DEFAULT_CLIENT_ID = "a65f41a4d09c4187af8257c4903b991a"

    private const val SCOPES =
        "user-read-private playlist-read-private playlist-read-collaborative " +
        "user-library-read user-top-read user-read-playback-state " +
        "user-modify-playback-state user-read-currently-playing streaming"

    private val spinePalette = listOf(
        0xFF1DB954, 0xFF1A237E, 0xFF004D40, 0xFF4A148C, 0xFFB71C1C, 0xFF880E4F, 0xFF311B92, 0xFF0D47A1
    )
    private val labelPalette = listOf(
        0xFF80BC9A, 0xFFFF70A6, 0xFF70D6FF, 0xFFFF9770, 0xFFE9FF70, 0xFFC594FF, 0xFFFFBE0B
    )
    private val mixtapeLabelPalette = listOf(
        0xFFFFF9C4, 0xFF80DEEA, 0xFFFFCCBC, 0xFFC8E6C9, 0xFFE1BEE7, 0xFFFFECB3
    )

    fun getSavedClientId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CLIENT_ID, null)
        return if (saved.isNullOrBlank()) DEFAULT_CLIENT_ID else saved
    }

    fun saveClientId(context: Context, clientId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLIENT_ID, clientId.trim())
            .apply()
    }

    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()
    }

    fun getSavedUserName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_NAME, "SPOTIFY USER") ?: "SPOTIFY USER"
    }

    fun logout(context: Context) {
        val clientId = getSavedClientId(context)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString(KEY_CLIENT_ID, clientId)
            .apply()
    }

    /**
     * Initiates OAuth 2.0 PKCE login in the system browser / Chrome Custom Tab.
     */
    fun startLogin(context: Context, clientId: String) {
        val cleanClientId = clientId.trim()
        saveClientId(context, cleanClientId)

        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODE_VERIFIER, codeVerifier)
            .apply()

        val authUri = Uri.parse("https://accounts.spotify.com/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", cleanClientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("scope", SCOPES)
            .build()

        val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Checks if an incoming Intent is the OAuth redirect callback and exchanges the code for tokens.
     */
    suspend fun handleAuthCallback(context: Context, intent: Intent?): Result<String> = withContext(Dispatchers.IO) {
        val uri = intent?.data ?: return@withContext Result.failure(Exception("No callback URI"))
        if (uri.scheme != "pixelplayer" || uri.host != "spotify-callback") {
            return@withContext Result.failure(Exception("Not a Spotify callback"))
        }

        val error = uri.getQueryParameter("error")
        if (error != null) {
            return@withContext Result.failure(Exception("Spotify Login Error: $error"))
        }

        val code = uri.getQueryParameter("code")
            ?: return@withContext Result.failure(Exception("No authorization code returned"))

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val clientId = prefs.getString(KEY_CLIENT_ID, null)
            ?: return@withContext Result.failure(Exception("Missing Client ID"))
        val codeVerifier = prefs.getString(KEY_CODE_VERIFIER, null)
            ?: return@withContext Result.failure(Exception("Missing PKCE Code Verifier"))

        try {
            val bodyParams = mapOf(
                "client_id" to clientId,
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "code_verifier" to codeVerifier
            )

            val responseJson = postForm("https://accounts.spotify.com/api/token", bodyParams)
            val accessToken = responseJson.getString("access_token")
            val refreshToken = responseJson.optString("refresh_token", "")
            val expiresIn = responseJson.optLong("expires_in", 3600L)
            val expiresAt = System.currentTimeMillis() + (expiresIn - 60) * 1000L

            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .apply()

            // Fetch user display name
            val profileJson = getJson("https://api.spotify.com/v1/me", accessToken)
            val displayName = profileJson.optString("display_name", "SPOTIFY USER")
            prefs.edit().putString(KEY_USER_NAME, displayName).apply()

            Result.success(displayName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Returns a valid access token, automatically refreshing if expired.
     */
    suspend fun getValidAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return@withContext null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)

        if (System.currentTimeMillis() < expiresAt) {
            return@withContext token
        }

        // Token expired; attempt refresh
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        val clientId = prefs.getString(KEY_CLIENT_ID, null)
        if (refreshToken.isNullOrBlank() || clientId.isNullOrBlank()) {
            return@withContext token
        }

        return@withContext try {
            val responseJson = postForm(
                "https://accounts.spotify.com/api/token",
                mapOf(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                    "client_id" to clientId
                )
            )
            val newToken = responseJson.getString("access_token")
            val newRefresh = responseJson.optString("refresh_token", refreshToken)
            val expiresIn = responseJson.optLong("expires_in", 3600L)
            val newExpiresAt = System.currentTimeMillis() + (expiresIn - 60) * 1000L

            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, newToken)
                .putString(KEY_REFRESH_TOKEN, newRefresh)
                .putLong(KEY_EXPIRES_AT, newExpiresAt)
                .apply()
            newToken
        } catch (_: Exception) {
            token
        }
    }

    /**
     * Fetches the user's Spotify Playlists and Saved Albums / Liked Songs and converts them into
     * 16-bit Pixel Tapes for the Shelf & Shoe Box!
     */
    suspend fun syncSpotifyLibrary(context: Context): Result<SpotifySyncResult> = withContext(Dispatchers.IO) {
        val token = getValidAccessToken(context)
            ?: return@withContext Result.failure(Exception("Not logged in to Spotify"))

        try {
            val profileJson = getJson("https://api.spotify.com/v1/me", token)
            val userName = profileJson.optString("display_name", "SPOTIFY USER")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_USER_NAME, userName)
                .apply()

            // 1. Fetch User Playlists -> Homemade Mixtapes in Shoe Box
            val playlistTapes = mutableListOf<TapeItem>()
            try {
                val playlistsJson = getJson("https://api.spotify.com/v1/me/playlists?limit=12", token)
                val items = playlistsJson.optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val pl = items.optJSONObject(i) ?: continue
                    val plId = pl.optString("id")
                    val plName = pl.optString("name", "Spotify Mixtape")
                    val plUri = pl.optString("uri", "spotify:playlist:$plId")

                    val tracks = fetchPlaylistTracks(plId, plName, token)
                    if (tracks.isNotEmpty()) {
                        val labelCol = mixtapeLabelPalette[i % mixtapeLabelPalette.size]
                        playlistTapes.add(
                            TapeItem(
                                id = "spotify_pl_$plId",
                                title = plName.uppercase(),
                                subtitle = "SPOTIFY MIX • ${tracks.size} TRACKS",
                                type = TapeType.PLAYLIST,
                                tracks = tracks,
                                spineColorHex = 0xFF1B5E20,
                                labelColorHex = labelCol,
                                isHomeMade = true,
                                artworkBitmap = null,
                                isSpotify = true,
                                spotifyContextUri = plUri
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }

            // 2. Fetch Saved Albums & Liked Songs -> Artist Shelf Tapes with Pixelated Cover Art
            val allSpotifyTracks = mutableListOf<Track>()
            val albumArtworkCache = mutableMapOf<String, Bitmap?>()

            try {
                val albumsJson = getJson("https://api.spotify.com/v1/me/albums?limit=12", token)
                val items = albumsJson.optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val albumObj = item.optJSONObject("album") ?: continue
                    val albumName = albumObj.optString("name", "Unknown Album")
                    val artistsArr = albumObj.optJSONArray("artists")
                    val artistName = artistsArr?.optJSONObject(0)?.optString("name") ?: "Spotify Artist"

                    // Download & pixelate album cover art (smallest thumbnail for speed)
                    val imagesArr = albumObj.optJSONArray("images")
                    val imageUrl = if (imagesArr != null && imagesArr.length() > 0) {
                        imagesArr.optJSONObject(imagesArr.length() - 1)?.optString("url")
                            ?: imagesArr.optJSONObject(0)?.optString("url")
                    } else null

                    val artKey = "${artistName}_$albumName"
                    if (!albumArtworkCache.containsKey(artKey) && !imageUrl.isNullOrBlank()) {
                        albumArtworkCache[artKey] = downloadAndPixelateArtwork(imageUrl)
                    }

                    val tracksObj = albumObj.optJSONObject("tracks")
                    val trackItems = tracksObj?.optJSONArray("items") ?: JSONArray()
                    for (t in 0 until trackItems.length()) {
                        val tr = trackItems.optJSONObject(t) ?: continue
                        val trId = tr.optString("id").hashCode().toLong()
                        val trTitle = tr.optString("name", "Track")
                        val trDur = tr.optLong("duration_ms", 180000L)
                        val trUri = tr.optString("uri", "")
                        val previewUrl = tr.optString("preview_url", "")
                        val contentUri = if (previewUrl.isNotBlank() && previewUrl != "null") {
                            Uri.parse(previewUrl)
                        } else {
                            Uri.parse(trUri.ifBlank { "spotify:track:0" })
                        }

                        allSpotifyTracks.add(
                            Track(
                                id = trId,
                                title = trTitle,
                                artist = artistName,
                                album = albumName,
                                durationMs = trDur,
                                contentUri = contentUri,
                                spotifyUri = trUri
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }

            // Fallback: if user has no saved albums, pull from Liked Songs (/v1/me/tracks)
            if (allSpotifyTracks.isEmpty()) {
                try {
                    val likedJson = getJson("https://api.spotify.com/v1/me/tracks?limit=30", token)
                    val items = likedJson.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val tr = item.optJSONObject("track") ?: continue
                        val trId = tr.optString("id").hashCode().toLong()
                        val trTitle = tr.optString("name", "Track")
                        val trDur = tr.optLong("duration_ms", 180000L)
                        val trUri = tr.optString("uri", "")
                        val previewUrl = tr.optString("preview_url", "")

                        val albumObj = tr.optJSONObject("album")
                        val albumName = albumObj?.optString("name", "Spotify Singles") ?: "Spotify Singles"
                        val artistsArr = tr.optJSONArray("artists")
                        val artistName = artistsArr?.optJSONObject(0)?.optString("name") ?: "Spotify Artist"

                        val imagesArr = albumObj?.optJSONArray("images")
                        val imageUrl = if (imagesArr != null && imagesArr.length() > 0) {
                            imagesArr.optJSONObject(imagesArr.length() - 1)?.optString("url")
                        } else null

                        val artKey = "${artistName}_$albumName"
                        if (!albumArtworkCache.containsKey(artKey) && !imageUrl.isNullOrBlank()) {
                            albumArtworkCache[artKey] = downloadAndPixelateArtwork(imageUrl)
                        }

                        val contentUri = if (previewUrl.isNotBlank() && previewUrl != "null") {
                            Uri.parse(previewUrl)
                        } else {
                            Uri.parse(trUri.ifBlank { "spotify:track:0" })
                        }

                        allSpotifyTracks.add(
                            Track(
                                id = trId,
                                title = trTitle,
                                artist = artistName,
                                album = albumName,
                                durationMs = trDur,
                                contentUri = contentUri,
                                spotifyUri = trUri
                            )
                        )
                    }
                } catch (_: Exception) {
                }
            }

            // Group Spotify tracks into Artist Shelf Tapes
            val artistTapes = mutableMapOf<String, List<TapeItem>>()
            val tracksByArtist = allSpotifyTracks.groupBy { it.artist }

            tracksByArtist.forEach { (artist, artistTracks) ->
                val list = mutableListOf<TapeItem>()
                val hash = kotlin.math.abs(artist.hashCode())
                val firstArt = artistTracks.firstNotNullOfOrNull { albumArtworkCache["${it.artist}_${it.album}"] }

                list.add(
                    TapeItem(
                        id = "spotify_artist_all_$artist",
                        title = "$artist - SPOTIFY ALL",
                        subtitle = "🟢 SPOTIFY • ${artistTracks.size} Tracks",
                        type = TapeType.ARTIST_ALL,
                        tracks = artistTracks,
                        spineColorHex = spinePalette[hash % spinePalette.size],
                        labelColorHex = labelPalette[hash % labelPalette.size],
                        isHomeMade = false,
                        artworkBitmap = firstArt,
                        isSpotify = true
                    )
                )

                artistTracks.groupBy { it.album }.forEach { (album, albumTracks) ->
                    val albHash = kotlin.math.abs((artist + album).hashCode())
                    val art = albumArtworkCache["${artist}_$album"] ?: firstArt
                    list.add(
                        TapeItem(
                            id = "spotify_album_${artist}_$album",
                            title = album,
                            subtitle = "🟢 $artist (${albumTracks.size} Tracks)",
                            type = TapeType.ALBUM,
                            tracks = albumTracks,
                            spineColorHex = spinePalette[albHash % spinePalette.size],
                            labelColorHex = labelPalette[albHash % labelPalette.size],
                            isHomeMade = false,
                            artworkBitmap = art,
                            isSpotify = true
                        )
                    )
                }

                artistTapes[artist] = list
            }

            Result.success(
                SpotifySyncResult(
                    userName = userName,
                    artistTapes = artistTapes,
                    playlistTapes = playlistTapes,
                    statusMessage = "SYNCED ${playlistTapes.size} MIXTAPES & ${artistTapes.size} ARTISTS"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchPlaylistTracks(playlistId: String, playlistName: String, token: String): List<Track> {
        val result = mutableListOf<Track>()
        try {
            val json = getJson("https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=25", token)
            val items = json.optJSONArray("items") ?: return emptyList()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val tr = item.optJSONObject("track") ?: continue
                if (tr.isNull("id")) continue

                val trId = tr.optString("id").hashCode().toLong()
                val trTitle = tr.optString("name", "Track")
                val trDur = tr.optLong("duration_ms", 180000L)
                val trUri = tr.optString("uri", "")
                val previewUrl = tr.optString("preview_url", "")
                val artistsArr = tr.optJSONArray("artists")
                val artistName = artistsArr?.optJSONObject(0)?.optString("name") ?: "Spotify Artist"
                val albumName = tr.optJSONObject("album")?.optString("name") ?: playlistName

                val contentUri = if (previewUrl.isNotBlank() && previewUrl != "null") {
                    Uri.parse(previewUrl)
                } else {
                    Uri.parse(trUri.ifBlank { "spotify:track:0" })
                }

                result.add(
                    Track(
                        id = trId,
                        title = trTitle,
                        artist = artistName,
                        album = albumName,
                        durationMs = trDur,
                        contentUri = contentUri,
                        spotifyUri = trUri
                    )
                )
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun downloadAndPixelateArtwork(imageUrl: String): Bitmap? {
        return try {
            val conn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                doInput = true
            }
            conn.connect()
            val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
            conn.disconnect()
            if (bmp != null) LocalMusicScanner.pixelateBitmap(bmp, 28) else null
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // SPOTIFY CONNECT PLAYBACK CONTROLS
    // =========================================================================

    /**
     * Triggers playback of a Spotify track via Spotify Connect Web API.
     * Returns true if Spotify Connect accepted the command.
     */
    suspend fun playSpotifyTrack(context: Context, spotifyUri: String): Boolean = withContext(Dispatchers.IO) {
        val token = getValidAccessToken(context) ?: return@withContext false
        try {
            val body = JSONObject().apply {
                put("uris", JSONArray().put(spotifyUri))
            }.toString()

            val code = sendPlayerRequest("PUT", "https://api.spotify.com/v1/me/player/play", token, body)
            if (code in 200..299) {
                return@withContext true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun pauseSpotify(context: Context): Boolean = withContext(Dispatchers.IO) {
        val token = getValidAccessToken(context) ?: return@withContext false
        try {
            val code = sendPlayerRequest("PUT", "https://api.spotify.com/v1/me/player/pause", token, null)
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    suspend fun seekSpotify(context: Context, positionMs: Long): Boolean = withContext(Dispatchers.IO) {
        val token = getValidAccessToken(context) ?: return@withContext false
        try {
            val code = sendPlayerRequest(
                "PUT",
                "https://api.spotify.com/v1/me/player/seek?position_ms=$positionMs",
                token,
                null
            )
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    suspend fun setSpotifyShuffle(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val token = getValidAccessToken(context) ?: return@withContext false
        try {
            val code = sendPlayerRequest(
                "PUT",
                "https://api.spotify.com/v1/me/player/shuffle?state=$enabled",
                token,
                null
            )
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    suspend fun setSpotifyRepeat(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val token = getValidAccessToken(context) ?: return@withContext false
        try {
            val mode = if (enabled) "context" else "off"
            val code = sendPlayerRequest(
                "PUT",
                "https://api.spotify.com/v1/me/player/repeat?state=$mode",
                token,
                null
            )
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    // =========================================================================
    // PKCE & HTTP HELPERS
    // =========================================================================

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        ).take(64)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun postForm(urlStr: String, params: Map<String, String>): JSONObject {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = 8000
            readTimeout = 8000
        }

        val formBody = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

        OutputStreamWriter(conn.outputStream).use { it.write(formBody) }

        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()

        if (status !in 200..299) {
            throw Exception("HTTP $status: $text")
        }
        return JSONObject(text)
    }

    private fun getJson(urlStr: String, accessToken: String): JSONObject {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 8000
            readTimeout = 8000
        }

        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()

        if (status !in 200..299) {
            throw Exception("HTTP $status: $text")
        }
        return JSONObject(text)
    }

    private fun sendPlayerRequest(method: String, urlStr: String, accessToken: String, jsonBody: String?): Int {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $accessToken")
            if (jsonBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            connectTimeout = 5000
            readTimeout = 5000
        }

        if (jsonBody != null) {
            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }
        }

        val code = conn.responseCode
        conn.disconnect()
        return code
    }
}

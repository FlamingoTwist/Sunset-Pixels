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

data class SpotifyDevice(
    val id: String,
    val name: String,
    val type: String,
    val isActive: Boolean
)

data class SpotifyPlaybackState(
    val isPlaying: Boolean,
    val progressMs: Long,
    val durationMs: Long,
    val trackUri: String?
)

object SpotifyManager {

    private const val PREFS_NAME = "pixel_spotify_prefs"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_CODE_VERIFIER = "code_verifier"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_LAST_TAPE_ID = "last_tape_id"
    private const val CACHE_FILE_NAME = "spotify_library_cache_v2.json"

    const val REDIRECT_URI = "pixelplayer://spotify-callback"
    const val DEFAULT_CLIENT_ID = "a65f41a4d09c4187af8257c4903b991a"
    const val STREAM_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"


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

    fun saveLastTapeId(context: Context, tapeId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_TAPE_ID, tapeId)
            .apply()
    }

    fun getLastTapeId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_TAPE_ID, null)
    }

    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()
    }

        private const val KEY_FAVOURITE_TAPES = "favourite_tape_ids"

    fun getFavouriteTapeIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_FAVOURITE_TAPES, emptySet())?.toSet() ?: emptySet()
    }

    fun toggleFavouriteTape(context: Context, tapeId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_FAVOURITE_TAPES, emptySet())?.toMutableSet() ?: mutableSetOf()
        val isNowFav = if (current.contains(tapeId)) {
            current.remove(tapeId)
            false
        } else {
            current.add(tapeId)
            true
        }
        prefs.edit().putStringSet(KEY_FAVOURITE_TAPES, current).apply()
        return isNowFav
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
        try {
            java.io.File(context.filesDir, CACHE_FILE_NAME).delete()
        } catch (_: Exception) {
        }
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
    suspend fun syncSpotifyLibrary(
        context: Context,
        onProgress: (String) -> Unit = {}
    ): Result<SpotifySyncResult> = withContext(Dispatchers.IO) {
        val token = getValidAccessToken(context)
            ?: return@withContext Result.failure(Exception("Not logged in to Spotify"))

        try {
            onProgress("READING SPOTIFY PROFILE...")
            val profileJson = getJson("https://api.spotify.com/v1/me", token)
            val userName = profileJson.optString("display_name", "SPOTIFY USER")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_USER_NAME, userName)
                .apply()

            val playlistTapes = mutableListOf<TapeItem>()
            val allSpotifyTracks = mutableListOf<Track>()
            val albumArtworkCache = mutableMapOf<String, Bitmap?>()

            // 1. Fetch User Playlists with Full Pagination -> Homemade Mixtapes in Shoe Box
            try {
                val allPlaylistItems = mutableListOf<JSONObject>()
                var nextPlaylistsUrl: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
                var pageNum = 1

                while (!nextPlaylistsUrl.isNullOrBlank() && allPlaylistItems.size < 250) {
                    onProgress("SCANNING PLAYLISTS (PAGE $pageNum)...")
                    val pageJson = getJson(nextPlaylistsUrl, token)
                    val items = pageJson.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        items.optJSONObject(i)?.let { allPlaylistItems.add(it) }
                    }
                    val next = pageJson.optString("next", "")
                    nextPlaylistsUrl = if (next.isNotBlank() && next != "null") next else null
                    pageNum++
                }

                android.util.Log.d("SpotifySync", "Found ${allPlaylistItems.size} total playlists across $pageNum pages")
                for (i in 0 until allPlaylistItems.size) {
                    val pl = allPlaylistItems[i]
                    val plId = pl.optString("id")
                    if (plId.isBlank()) continue
                    val plName = pl.optString("name", "Spotify Mixtape")
                    val plUri = pl.optString("uri", "spotify:playlist:$plId")

                    onProgress("LOADING MIXTAPE (${i + 1}/${allPlaylistItems.size}): ${plName.take(16)}...")
                    val tracks = fetchPlaylistTracks(plId, plName, token)
                    val fallbackTracks = if (tracks.isNotEmpty()) {
                        tracks
                    } else {
                        // Algorithmic or protected playlist context track: strictly use generic track numbering,
                        // NEVER the playlist name as track or artist!
                        listOf(
                            Track(
                                id = plId.hashCode().toLong(),
                                title = "Track 1",
                                artist = "Spotify Audio",
                                album = "Spotify Mixtape",
                                durationMs = 180000L,
                                contentUri = Uri.parse(plUri),
                                spotifyUri = plUri
                            )
                        )
                    }

                    val labelCol = mixtapeLabelPalette[i % mixtapeLabelPalette.size]
                    playlistTapes.add(
                        TapeItem(
                            id = "spotify_pl_$plId",
                            title = plName.uppercase(),
                            subtitle = "🟢 SPOTIFY MIX • ${fallbackTracks.size} TRACKS",
                            type = TapeType.PLAYLIST,
                            tracks = fallbackTracks,
                            spineColorHex = 0xFF1B5E20,
                            labelColorHex = labelCol,
                            isHomeMade = true,
                            artworkBitmap = null,
                            isSpotify = true,
                            spotifyContextUri = plUri
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("SpotifySync", "Playlists fetch warning: ${e.message}")
            }

            // 2. Fetch Liked Songs (/v1/me/tracks) -> Both a "LIKED SONGS" Mixtape AND Artist Shelf tracks
            try {
                onProgress("FETCHING LIKED SONGS...")
                val likedJson = getJson("https://api.spotify.com/v1/me/tracks?limit=30", token)
                val items = likedJson.optJSONArray("items") ?: JSONArray()
                android.util.Log.d("SpotifySync", "Found ${items.length()} liked tracks")
                val likedTracks = mutableListOf<Track>()

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val tr = item.optJSONObject("track") ?: continue
                    val trId = tr.optString("id").hashCode().toLong()
                    val trTitle = tr.optString("name", "Track")
                    val trDur = tr.optLong("duration_ms", 180000L)
                    val trUri = tr.optString("uri", "")

                    val albumObj = tr.optJSONObject("album")
                    val albumName = albumObj?.optString("name", "Spotify Singles") ?: "Spotify Singles"
                    val artistsArr = tr.optJSONArray("artists")
                    val artistName = artistsArr?.optJSONObject(0)?.optString("name") ?: "Spotify Artist"

                    val imagesArr = albumObj?.optJSONArray("images")
                    val imageUrl = if (imagesArr != null && imagesArr.length() > 0) {
                        imagesArr.optJSONObject(imagesArr.length() - 1)?.optString("url")
                    } else null

                    val artKey = "${artistName}_$albumName"
                    if (!albumArtworkCache.containsKey(artKey) && !imageUrl.isNullOrBlank() && albumArtworkCache.size < 20) {
                        albumArtworkCache[artKey] = downloadAndPixelateArtwork(imageUrl)
                    }

                    val contentUri = Uri.parse(trUri.ifBlank { "spotify:track:$i" })

                    val trackObj = Track(
                        id = trId,
                        title = trTitle,
                        artist = artistName,
                        album = albumName,
                        durationMs = trDur,
                        contentUri = contentUri,
                        spotifyUri = trUri
                    )
                    likedTracks.add(trackObj)
                    allSpotifyTracks.add(trackObj)
                }

                if (likedTracks.isNotEmpty()) {
                    playlistTapes.add(
                        0,
                        TapeItem(
                            id = "spotify_liked_songs",
                            title = "LIKED SONGS MIX",
                            subtitle = "🟢 SPOTIFY FAVORITES • ${likedTracks.size} TRACKS",
                            type = TapeType.PLAYLIST,
                            tracks = likedTracks,
                            spineColorHex = 0xFF1DB954,
                            labelColorHex = 0xFFC8E6C9,
                            isHomeMade = true,
                            artworkBitmap = null,
                            isSpotify = true
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("SpotifySync", "Liked tracks warning: ${e.message}")
            }

            val syncResult = SpotifySyncResult(
                userName = userName,
                artistTapes = emptyMap(),
                playlistTapes = playlistTapes,
                statusMessage = "SYNCED ${playlistTapes.size} SPOTIFY PLAYLIST TAPES"
            )
            saveLibraryToCache(context, syncResult)
            android.util.Log.d("SpotifySync", "Sync complete & cached: ${playlistTapes.size} playlists")
            Result.success(syncResult)
        } catch (e: Exception) {
            android.util.Log.e("SpotifySync", "Sync failed", e)
            Result.failure(e)
        }
    }

    /**
     * Saves the synced Spotify cassettes (including pixelated 16-bit artwork bitmaps) to local disk
     * so reopening the app populates all Spotify cassettes at 0ms without needing any button press!
     */
    private fun saveLibraryToCache(context: Context, data: SpotifySyncResult) {
        try {
            val root = JSONObject()
            root.put("userName", data.userName)
            root.put("statusMessage", data.statusMessage)

            val playlistsArr = JSONArray()
            data.playlistTapes.forEach { playlistsArr.put(serializeTapeItem(it)) }
            root.put("playlistTapes", playlistsArr)

            val artistsObj = JSONObject()
            data.artistTapes.forEach { (artist, tapes) ->
                val arr = JSONArray()
                tapes.forEach { arr.put(serializeTapeItem(it)) }
                artistsObj.put(artist, arr)
            }
            root.put("artistTapes", artistsObj)

            java.io.File(context.filesDir, CACHE_FILE_NAME).writeText(root.toString())
        } catch (e: Exception) {
            android.util.Log.w("SpotifySync", "Cache save warning: ${e.message}")
        }
    }

    /**
     * Synchronously loads the cached Spotify cassettes on startup so shelves are populated immediately.
     */
    fun loadCachedLibrary(context: Context): SpotifySyncResult? {
        if (!isLoggedIn(context)) return null
        val file = java.io.File(context.filesDir, CACHE_FILE_NAME)
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText())
            val userName = root.optString("userName", getSavedUserName(context))
            val statusMessage = root.optString("statusMessage", "SPOTIFY TAPES LOADED")

            val playlistTapes = mutableListOf<TapeItem>()
            val playlistsArr = root.optJSONArray("playlistTapes") ?: JSONArray()
            for (i in 0 until playlistsArr.length()) {
                val obj = playlistsArr.optJSONObject(i) ?: continue
                playlistTapes.add(deserializeTapeItem(obj))
            }

            val artistTapes = mutableMapOf<String, List<TapeItem>>()
            val artistsObj = root.optJSONObject("artistTapes")
            if (artistsObj != null) {
                val keys = artistsObj.keys()
                while (keys.hasNext()) {
                    val artist = keys.next()
                    val arr = artistsObj.optJSONArray(artist) ?: continue
                    val list = mutableListOf<TapeItem>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        list.add(deserializeTapeItem(obj))
                    }
                    if (list.isNotEmpty()) {
                        artistTapes[artist] = list
                    }
                }
            }

            SpotifySyncResult(
                userName = userName,
                artistTapes = artistTapes,
                playlistTapes = playlistTapes,
                statusMessage = statusMessage
            )
        } catch (e: Exception) {
            android.util.Log.w("SpotifySync", "Cache load warning: ${e.message}")
            null
        }
    }

    private fun serializeTapeItem(tape: TapeItem): JSONObject {
        val obj = JSONObject()
        obj.put("id", tape.id)
        obj.put("title", tape.title)
        obj.put("subtitle", tape.subtitle)
        obj.put("type", tape.type.name)
        obj.put("spineColorHex", tape.spineColorHex)
        obj.put("labelColorHex", tape.labelColorHex)
        obj.put("isHomeMade", tape.isHomeMade)
        obj.put("isSpotify", tape.isSpotify)
        if (tape.spotifyContextUri != null) {
            obj.put("spotifyContextUri", tape.spotifyContextUri)
        }
        if (tape.artworkBitmap != null) {
            val baos = java.io.ByteArrayOutputStream()
            tape.artworkBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            obj.put("artworkBase64", Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP))
        }
        val tracksArr = JSONArray()
        tape.tracks.forEach { tr ->
            val tObj = JSONObject()
            tObj.put("id", tr.id)
            tObj.put("title", tr.title)
            tObj.put("artist", tr.artist)
            tObj.put("album", tr.album)
            tObj.put("durationMs", tr.durationMs)
            tObj.put("contentUri", tr.contentUri.toString())
            if (tr.spotifyUri != null) tObj.put("spotifyUri", tr.spotifyUri)
            if (tr.artistUri != null) tObj.put("artistUri", tr.artistUri)
            if (tr.albumUri != null) tObj.put("albumUri", tr.albumUri)
            tracksArr.put(tObj)
        }
        obj.put("tracks", tracksArr)
        return obj
    }

    private fun deserializeTapeItem(obj: JSONObject): TapeItem {
        val tapeTitle = obj.optString("title", "").trim()
        val tracks = mutableListOf<Track>()
        val tracksArr = obj.optJSONArray("tracks") ?: JSONArray()
        for (i in 0 until tracksArr.length()) {
            val tObj = tracksArr.optJSONObject(i) ?: continue
            val spUri = tObj.optString("spotifyUri", "").ifBlank { null }
            val artUri = tObj.optString("artistUri", "").ifBlank { null }
            val albUri = tObj.optString("albumUri", "").ifBlank { null }
            val rawTitle = tObj.optString("title", "Track").trim()
            val rawArtist = tObj.optString("artist", "Artist").trim()

            // Strictly sanitize legacy cached tracks: never let the playlist title pollute track or artist
            val cleanTitle = if (rawTitle.isBlank() || (tapeTitle.isNotBlank() && rawTitle.equals(tapeTitle, ignoreCase = true))) {
                "Track ${i + 1}"
            } else rawTitle

            val cleanArtist = if (rawArtist.isBlank() ||
                rawArtist.equals("Spotify Playlist", ignoreCase = true) ||
                (tapeTitle.isNotBlank() && rawArtist.equals(tapeTitle, ignoreCase = true))) {
                "Spotify Audio"
            } else rawArtist

            tracks.add(
                Track(
                    id = tObj.optLong("id"),
                    title = cleanTitle,
                    artist = cleanArtist,
                    album = tObj.optString("album", "Album"),
                    durationMs = tObj.optLong("durationMs", 180000L),
                    contentUri = Uri.parse(spUri ?: tObj.optString("contentUri", "spotify:track:$i")),
                    spotifyUri = spUri,
                    artistUri = artUri,
                    albumUri = albUri
                )
            )
        }
        val artBase64 = obj.optString("artworkBase64", "")
        val bitmap = if (artBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(artBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        } else null

        val typeName = obj.optString("type", TapeType.PLAYLIST.name)
        val tapeType = try {
            TapeType.valueOf(typeName)
        } catch (_: Exception) {
            TapeType.PLAYLIST
        }

        return TapeItem(
            id = obj.optString("id"),
            title = obj.optString("title"),
            subtitle = obj.optString("subtitle"),
            type = tapeType,
            tracks = tracks,
            spineColorHex = obj.optLong("spineColorHex", 0xFF1B5E20),
            labelColorHex = obj.optLong("labelColorHex", 0xFFC8E6C9),
            isHomeMade = obj.optBoolean("isHomeMade", false),
            artworkBitmap = bitmap,
            isSpotify = obj.optBoolean("isSpotify", true),
            spotifyContextUri = obj.optString("spotifyContextUri", "").ifBlank { null }
        )
    }

    private fun fetchPlaylistTracks(playlistId: String, playlistName: String, token: String): List<Track> {
        val embedTracks = fetchEmbedTracks("playlist", playlistId, playlistName)
        if (embedTracks.isNotEmpty()) {
            return embedTracks
        }

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
                val artistsArr = tr.optJSONArray("artists")
                val firstArtObj = artistsArr?.optJSONObject(0)
                val artistName = firstArtObj?.optString("name") ?: "Spotify Artist"
                val artistUri = firstArtObj?.optString("uri")?.ifBlank { null }
                val albumObj = tr.optJSONObject("album")
                val albumName = albumObj?.optString("name") ?: playlistName
                val albumUri = albumObj?.optString("uri")?.ifBlank { null }

                val contentUri = Uri.parse(trUri.ifBlank { "spotify:track:$i" })

                result.add(
                    Track(
                        id = trId,
                        title = trTitle,
                        artist = artistName,
                        album = albumName,
                        durationMs = trDur,
                        contentUri = contentUri,
                        spotifyUri = trUri,
                        artistUri = artistUri,
                        albumUri = albumUri
                    )
                )
            }
        } catch (_: Exception) {
        }
        return result
    }



    /**
     * Fetches the trackList from Spotify's public embed state JSON for a playlist or album.
     * Note: We intentionally keep contentUri as "spotify:track:..." so playTrack() resolves
     * the FULL-LENGTH song stream instead of playing the 30-second audioPreview clip!
     */
    fun fetchEmbedTracks(entityType: String, entityId: String, defaultAlbumName: String): List<Track> {
        val result = mutableListOf<Track>()
        try {
            val html = getRawString("https://open.spotify.com/embed/$entityType/$entityId")
            val markerStart = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
            val startIdx = html.indexOf(markerStart)
            if (startIdx == -1) return emptyList()
            val jsonStart = startIdx + markerStart.length
            val endIdx = html.indexOf("</script>", jsonStart)
            if (endIdx == -1) return emptyList()

            val nextData = JSONObject(html.substring(jsonStart, endIdx))
            val entity = nextData
                .optJSONObject("props")
                ?.optJSONObject("pageProps")
                ?.optJSONObject("state")
                ?.optJSONObject("data")
                ?.optJSONObject("entity")
                ?: return emptyList()

            val trackList = entity.optJSONArray("trackList") ?: return emptyList()
            for (i in 0 until trackList.length().coerceAtMost(35)) {
                val tr = trackList.optJSONObject(i) ?: continue
                val trUri = tr.optString("uri", "").ifBlank { "spotify:track:${entityId}_$i" }
                val trTitle = tr.optString("title", "Track")
                val artistName = tr.optString("subtitle", "Spotify Artist")
                    .replace("\u00a0", " ")
                val trDur = tr.optLong("duration", 180000L)

                result.add(
                    Track(
                        id = trUri.hashCode().toLong(),
                        title = trTitle,
                        artist = artistName,
                        album = defaultAlbumName,
                        durationMs = trDur,
                        contentUri = Uri.parse(trUri),
                        spotifyUri = trUri
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("SpotifySync", "Embed fetch failed for $entityType/$entityId: ${e.message}")
        }
        return result
    }

    private fun getRawString(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
            connectTimeout = 6000
            readTimeout = 6000
        }
        val code = conn.responseCode
        val reader = if (code in 200..299) {
            BufferedReader(InputStreamReader(conn.inputStream))
        } else {
            conn.disconnect()
            throw Exception("HTTP $code")
        }
        val text = reader.use { it.readText() }
        conn.disconnect()
        return text
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
}

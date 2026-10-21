package com.retro.pixelplayer.audio

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object MixtapeManager {

    private const val MIXTAPE_FILE = "user_mixtapes.json"

    private val mixtapeSpinePalette = listOf(
        0xFF5D4037, // Cardboard brown
        0xFF006064, // Retro teal
        0xFF8D6E63, // Kraft brown
        0xFF37474F, // Dark slate
        0xFF4A148C, // Synth purple
        0xFFB71C1C, // Crimson cassette
        0xFF1B5E20, // Pine green
        0xFFE65100  // Sunset orange
    )

    private val mixtapeLabelPalette = listOf(
        0xFFFFF9C4, // Faded masking tape yellow
        0xFF80DEEA, // Mint paper
        0xFFFFCCBC, // Soft coral
        0xFFC8E6C9, // Pastel green
        0xFFE1BEE7, // Light lavender
        0xFFFFECB3, // Cream parchment
        0xFFFFFFFF  // Clean typewriter white
    )

    /**
     * Loads all user-created mixtapes from disk.
     */
    fun loadMixtapes(context: Context): List<TapeItem> {
        val file = File(context.filesDir, MIXTAPE_FILE)
        if (!file.exists()) return emptyList()

        val mixtapes = mutableListOf<TapeItem>()
        try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val spineColorHex = obj.optLong("spineColorHex", 0xFF5D4037)
                val labelColorHex = obj.optLong("labelColorHex", 0xFFFFF9C4)

                val tracksArray = obj.getJSONArray("tracks")
                val tracks = mutableListOf<Track>()
                for (j in 0 until tracksArray.length()) {
                    val tObj = tracksArray.getJSONObject(j)
                    val trackId = tObj.getLong("id")
                    val trackTitle = tObj.getString("title")
                    val artist = tObj.getString("artist")
                    val album = tObj.optString("album", "Mixtape")
                    val durationMs = tObj.optLong("durationMs", 0L)
                    val contentUriStr = tObj.getString("contentUri")
                    val albumId = tObj.optLong("albumId", 0L)
                    val spotifyUri = if (tObj.has("spotifyUri")) tObj.getString("spotifyUri").takeIf { it.isNotBlank() } else null

                    tracks.add(
                        Track(
                            id = trackId,
                            title = trackTitle,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            contentUri = Uri.parse(contentUriStr),
                            albumId = albumId,
                            spotifyUri = spotifyUri
                        )
                    )
                }

                val hasSpotify = tracks.any { it.spotifyUri != null || it.contentUri.scheme == "spotify" }
                val hasLocal = tracks.any { it.spotifyUri == null && it.contentUri.scheme != "spotify" }
                val typeDesc = when {
                    hasSpotify && hasLocal -> "Hybrid Mixtape"
                    hasSpotify -> "Spotify Mixtape"
                    else -> "Local Mixtape"
                }

                mixtapes.add(
                    TapeItem(
                        id = id,
                        title = title,
                        subtitle = "$typeDesc • ${tracks.size} Track${if (tracks.size == 1) "" else "s"}",
                        type = TapeType.PLAYLIST,
                        tracks = tracks,
                        spineColorHex = spineColorHex,
                        labelColorHex = labelColorHex,
                        isHomeMade = true,
                        artworkBitmap = null,
                        isSpotify = hasSpotify && !hasLocal
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return mixtapes
    }

    /**
     * Saves user mixtapes list to disk.
     */
    fun saveMixtapes(context: Context, mixtapes: List<TapeItem>) {
        try {
            val array = JSONArray()
            mixtapes.forEach { tape ->
                val obj = JSONObject()
                obj.put("id", tape.id)
                obj.put("title", tape.title)
                obj.put("spineColorHex", tape.spineColorHex)
                obj.put("labelColorHex", tape.labelColorHex)

                val tracksArray = JSONArray()
                tape.tracks.forEach { track ->
                    val tObj = JSONObject()
                    tObj.put("id", track.id)
                    tObj.put("title", track.title)
                    tObj.put("artist", track.artist)
                    tObj.put("album", track.album)
                    tObj.put("durationMs", track.durationMs)
                    tObj.put("contentUri", track.contentUri.toString())
                    tObj.put("albumId", track.albumId)
                    if (track.spotifyUri != null) {
                        tObj.put("spotifyUri", track.spotifyUri)
                    }
                    tracksArray.put(tObj)
                }
                obj.put("tracks", tracksArray)
                array.put(obj)
            }

            val file = File(context.filesDir, MIXTAPE_FILE)
            file.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Creates a brand new mixtape.
     */
    fun createMixtape(
        context: Context,
        title: String,
        initialTrack: Track? = null
    ): TapeItem {
        val existing = loadMixtapes(context).toMutableList()
        val randomSpine = mixtapeSpinePalette.random()
        val randomLabel = mixtapeLabelPalette.random()
        val newId = "mixtape_${UUID.randomUUID().toString().take(8)}"

        val initialTracks = if (initialTrack != null) listOf(initialTrack) else emptyList()
        val hasSpotify = initialTracks.any { it.spotifyUri != null || it.contentUri.scheme == "spotify" }

        val newTape = TapeItem(
            id = newId,
            title = title.trim().ifEmpty { "UNTITLED MIXTAPE" },
            subtitle = "Handmade Mixtape • ${initialTracks.size} Tracks",
            type = TapeType.PLAYLIST,
            tracks = initialTracks,
            spineColorHex = randomSpine,
            labelColorHex = randomLabel,
            isHomeMade = true,
            artworkBitmap = null,
            isSpotify = hasSpotify
        )

        existing.add(0, newTape)
        saveMixtapes(context, existing)
        return newTape
    }

    /**
     * Appends a track to an existing mixtape.
     */
    fun addTrackToMixtape(context: Context, mixtapeId: String, track: Track): Boolean {
        val existing = loadMixtapes(context).toMutableList()
        val idx = existing.indexOfFirst { it.id == mixtapeId }
        if (idx == -1) return false

        val tape = existing[idx]
        val updatedTracks = tape.tracks + track
        val hasSpotify = updatedTracks.any { it.spotifyUri != null || it.contentUri.scheme == "spotify" }
        val hasLocal = updatedTracks.any { it.spotifyUri == null && it.contentUri.scheme != "spotify" }
        val typeDesc = when {
            hasSpotify && hasLocal -> "Hybrid Mixtape"
            hasSpotify -> "Spotify Mixtape"
            else -> "Local Mixtape"
        }

        existing[idx] = tape.copy(
            tracks = updatedTracks,
            subtitle = "$typeDesc • ${updatedTracks.size} Track${if (updatedTracks.size == 1) "" else "s"}",
            isSpotify = hasSpotify && !hasLocal
        )

        saveMixtapes(context, existing)
        return true
    }

    /**
     * Removes a track from a mixtape by track index.
     */
    fun removeTrackAt(context: Context, mixtapeId: String, trackIndex: Int): Boolean {
        val existing = loadMixtapes(context).toMutableList()
        val idx = existing.indexOfFirst { it.id == mixtapeId }
        if (idx == -1) return false

        val tape = existing[idx]
        if (trackIndex !in tape.tracks.indices) return false

        val updatedTracks = tape.tracks.toMutableList().apply { removeAt(trackIndex) }
        val hasSpotify = updatedTracks.any { it.spotifyUri != null || it.contentUri.scheme == "spotify" }
        val hasLocal = updatedTracks.any { it.spotifyUri == null && it.contentUri.scheme != "spotify" }
        val typeDesc = when {
            hasSpotify && hasLocal -> "Hybrid Mixtape"
            hasSpotify -> "Spotify Mixtape"
            else -> "Local Mixtape"
        }

        existing[idx] = tape.copy(
            tracks = updatedTracks,
            subtitle = "$typeDesc • ${updatedTracks.size} Track${if (updatedTracks.size == 1) "" else "s"}",
            isSpotify = hasSpotify && !hasLocal
        )

        saveMixtapes(context, existing)
        return true
    }

    /**
     * Deletes a mixtape entirely.
     */
    fun deleteMixtape(context: Context, mixtapeId: String): Boolean {
        val existing = loadMixtapes(context).toMutableList()
        val removed = existing.removeAll { it.id == mixtapeId }
        if (removed) {
            saveMixtapes(context, existing)
        }
        return removed
    }
}

package com.retro.pixelplayer.audio

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import java.io.File

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: Uri,
    val albumId: Long = 0L,
    val spotifyUri: String? = null
)

enum class TapeType {
    ALBUM,
    ARTIST_ALL,
    PLAYLIST
}

data class TapeItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: TapeType,
    val tracks: List<Track>,
    val spineColorHex: Long = 0xFF2A2336,
    val labelColorHex: Long = 0xFFFF70A6,
    val isHomeMade: Boolean = false,
    val artworkBitmap: Bitmap? = null,
    val isSpotify: Boolean = false,
    val spotifyContextUri: String? = null
)

object LocalMusicScanner {

    fun loadLocalTracks(context: Context): List<Track> {
        val tracks = mutableListOf<Track>()

        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.ARTIST} ASC, ${MediaStore.Audio.Media.ALBUM} ASC, ${MediaStore.Audio.Media.TITLE} ASC"

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = if (titleCol != -1) cursor.getString(titleCol) ?: "Unknown" else "Unknown"
                    val artist = if (artistCol != -1) cursor.getString(artistCol) ?: "Unknown Artist" else "Unknown Artist"
                    val album = if (albumCol != -1) cursor.getString(albumCol) ?: "Unknown Album" else "Unknown Album"
                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val albumId = if (albumIdCol != -1) cursor.getLong(albumIdCol) else 0L
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    tracks.add(Track(id, title, artist, album, duration, contentUri, albumId))
                }
            }
        } catch (_: Exception) {
        }

        // Direct directory fallback
        if (tracks.isEmpty()) {
            val musicDir = File("/sdcard/Music")
            if (musicDir.exists()) {
                val files = musicDir.listFiles()?.filter {
                    it.isFile && it.extension.lowercase() in listOf("mp3", "flac", "wav", "m4a")
                } ?: emptyList()

                files.forEachIndexed { index, file ->
                    val cleanName = file.nameWithoutExtension.replace("_", " ")
                    tracks.add(
                        Track(
                            id = index.toLong(),
                            title = cleanName,
                            artist = "Local Artist",
                            album = "Tape Side A",
                            durationMs = 0L,
                            contentUri = Uri.fromFile(file),
                            albumId = 0L
                        )
                    )
                }
            }
        }

        return tracks
    }

    /**
     * Pixelates any Bitmap down to a retro 16-bit low-res icon (e.g. 28x28 upscaled to 64x64)
     */
    fun pixelateBitmap(fullBitmap: Bitmap, targetSize: Int = 28): Bitmap {
        val pixelTiny = Bitmap.createScaledBitmap(fullBitmap, targetSize, targetSize, false)
        return Bitmap.createScaledBitmap(pixelTiny, 64, 64, false)
    }

    /**
     * Extracts album art and pixelates it down to a retro low-res icon (e.g. 24x24)
     */
    fun extractPixelatedArtwork(context: Context, track: Track, targetSize: Int = 28): Bitmap? {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, track.contentUri)
            val embeddedPicture = retriever.embeddedPicture
            retriever.release()

            if (embeddedPicture != null) {
                val fullBitmap = BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
                if (fullBitmap != null) {
                    return pixelateBitmap(fullBitmap, targetSize)
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    /**
     * Groups tracks into Artist tapes (Artist All-tracks + distinct Album tapes)
     */
    fun buildArtistTapes(context: Context, tracks: List<Track>): Map<String, List<TapeItem>> {
        val result = mutableMapOf<String, MutableList<TapeItem>>()

        val tracksByArtist = tracks.groupBy { it.artist }
        val spinePalette = listOf(
            0xFF3E2723, 0xFF1A237E, 0xFF004D40, 0xFF4A148C, 0xFFB71C1C, 0xFF880E4F, 0xFF311B92, 0xFF0D47A1
        )
        val labelPalette = listOf(
            0xFFFF70A6, 0xFF70D6FF, 0xFFFF9770, 0xFFE9FF70, 0xFF80BC9A, 0xFFC594FF, 0xFFFFBE0B
        )

        tracksByArtist.forEach { (artist, artistTracks) ->
            val tapeList = mutableListOf<TapeItem>()
            val artwork = artistTracks.firstOrNull()?.let { extractPixelatedArtwork(context, it) }

            // 1. "ALL" Tape for this artist
            val hash = artist.hashCode()
            val spineCol = spinePalette[kotlin.math.abs(hash) % spinePalette.size]
            val labelCol = labelPalette[kotlin.math.abs(hash) % labelPalette.size]

            tapeList.add(
                TapeItem(
                    id = "artist_all_$artist",
                    title = "$artist - GREATEST / ALL",
                    subtitle = "${artistTracks.size} Tracks",
                    type = TapeType.ARTIST_ALL,
                    tracks = artistTracks,
                    spineColorHex = spineCol,
                    labelColorHex = labelCol,
                    isHomeMade = false,
                    artworkBitmap = artwork
                )
            )

            // 2. Individual Album Tapes for this artist
            val tracksByAlbum = artistTracks.groupBy { it.album }
            tracksByAlbum.forEach { (album, albumTracks) ->
                val albumHash = (artist + album).hashCode()
                val albSpine = spinePalette[kotlin.math.abs(albumHash) % spinePalette.size]
                val albLabel = labelPalette[kotlin.math.abs(albumHash) % labelPalette.size]
                val albumArt = albumTracks.firstOrNull()?.let { extractPixelatedArtwork(context, it) } ?: artwork

                tapeList.add(
                    TapeItem(
                        id = "album_${artist}_$album",
                        title = album,
                        subtitle = "$artist (${albumTracks.size} Tracks)",
                        type = TapeType.ALBUM,
                        tracks = albumTracks,
                        spineColorHex = albSpine,
                        labelColorHex = albLabel,
                        isHomeMade = false,
                        artworkBitmap = albumArt
                    )
                )
            }

            result[artist] = tapeList
        }

        return result
    }

    /**
     * Builds Homemade Playlist tapes
     */
    fun buildPlaylistTapes(tracks: List<Track>): List<TapeItem> {
        val playlists = mutableListOf<TapeItem>()

        // Tape 1: Mixtape Vol. 1 (all tracks)
        if (tracks.isNotEmpty()) {
            playlists.add(
                TapeItem(
                    id = "playlist_mixtape_1",
                    title = "SUMMER CHILL MIXTAPE '86",
                    subtitle = "Handmade C-90 Tape • ${tracks.size} Songs",
                    type = TapeType.PLAYLIST,
                    tracks = tracks,
                    spineColorHex = 0xFF5D4037, // cardboard / handwritten tape
                    labelColorHex = 0xFFFFF9C4, // faded yellowish masking tape
                    isHomeMade = true,
                    artworkBitmap = null
                )
            )
        }

        // Tape 2: 16-Bit Night Drive
        val anamanaguchi = tracks.filter { it.artist.contains("Anamanaguchi", ignoreCase = true) }
        if (anamanaguchi.isNotEmpty()) {
            playlists.add(
                TapeItem(
                    id = "playlist_chiptune",
                    title = "8-BIT & CHIPTUNE BUSTERS",
                    subtitle = "Home Recorded • ${anamanaguchi.size} Tracks",
                    type = TapeType.PLAYLIST,
                    tracks = anamanaguchi,
                    spineColorHex = 0xFF006064,
                    labelColorHex = 0xFF80DEEA,
                    isHomeMade = true,
                    artworkBitmap = null
                )
            )
        }

        // Tape 3: Indie Acoustic Sunset
        val syd = tracks.filter { it.artist.contains("Syd", ignoreCase = true) }
        if (syd.isNotEmpty()) {
            playlists.add(
                TapeItem(
                    id = "playlist_acoustic",
                    title = "AUTUMN IN ARCADIA BAY",
                    subtitle = "Acoustic Mixtape • ${syd.size} Tracks",
                    type = TapeType.PLAYLIST,
                    tracks = syd,
                    spineColorHex = 0xFF8D6E63,
                    labelColorHex = 0xFFFFCCBC,
                    isHomeMade = true,
                    artworkBitmap = null
                )
            )
        }

        return playlists
    }
}

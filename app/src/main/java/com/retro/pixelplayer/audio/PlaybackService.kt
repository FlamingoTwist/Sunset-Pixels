package com.retro.pixelplayer.audio

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.retro.pixelplayer.spotify.SpotifyManager
import com.retro.pixelplayer.spotify.SpotifyRemoteManager

/**
 * Foreground MediaLibraryService that powers Android Auto dashboard media browsing,
 * lockscreen transport controls, and background playback.
 */
class PlaybackService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var librarySession: MediaLibrarySession? = null

    companion object {
        const val ROOT_ID = "root"
        const val CATEGORY_ARTISTS = "category_artists"
        const val CATEGORY_SHOEBOX = "category_shoebox"
        const val PREFIX_TAPE = "tape_"
    }

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build()
        player = exoPlayer

        librarySession = MediaLibrarySession.Builder(this, exoPlayer, LibrarySessionCallback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return librarySession
    }

    override fun onDestroy() {
        librarySession?.run {
            player.release()
            release()
            librarySession = null
        }
        super.onDestroy()
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Sunset Pixels")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = mutableListOf<MediaItem>()

            // Load local and Spotify cassettes
            val allLocalTracks = LocalMusicScanner.loadLocalTracks(this@PlaybackService)
            val localArtistTapes = LocalMusicScanner.buildArtistTapes(this@PlaybackService, allLocalTracks)
            val localPlaylistTapes = LocalMusicScanner.buildPlaylistTapes(allLocalTracks)
            val spotifyCache = SpotifyManager.loadCachedLibrary(this@PlaybackService)

            val mergedArtists = mutableMapOf<String, List<TapeItem>>()
            localArtistTapes.forEach { (artist, tapes) -> mergedArtists[artist] = tapes }
            spotifyCache?.artistTapes?.forEach { (artist, tapes) ->
                val existing = mergedArtists[artist] ?: emptyList()
                mergedArtists[artist] = (existing + tapes).distinctBy { it.id }
            }

            val mergedPlaylists = (localPlaylistTapes + (spotifyCache?.playlistTapes ?: emptyList())).distinctBy { it.id }

            when (parentId) {
                ROOT_ID -> {
                    // Two main browsing categories on Android Auto head unit
                    items.add(
                        MediaItem.Builder()
                            .setMediaId(CATEGORY_ARTISTS)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("Artist Shelf 📚")
                                    .setSubtitle("${mergedArtists.size} Artists")
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setFolderType(MediaMetadata.FOLDER_TYPE_ARTISTS)
                                    .build()
                            )
                            .build()
                    )
                    items.add(
                        MediaItem.Builder()
                            .setMediaId(CATEGORY_SHOEBOX)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle("Shoe Box (Playlists) 📦")
                                    .setSubtitle("${mergedPlaylists.size} Mixtapes")
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                                    .build()
                            )
                            .build()
                    )
                }

                CATEGORY_ARTISTS -> {
                    // List artists
                    mergedArtists.forEach { (artistName, tapes) ->
                        val totalTracks = tapes.sumOf { it.tracks.size }
                        val tape = tapes.firstOrNull()
                        val tapeId = tape?.id ?: artistName
                        items.add(
                            MediaItem.Builder()
                                .setMediaId("$PREFIX_TAPE$tapeId")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(artistName)
                                        .setArtist(artistName)
                                        .setSubtitle("${tapes.size} Tapes • $totalTracks Tracks")
                                        .setIsBrowsable(true)
                                        .setIsPlayable(true)
                                        .setFolderType(MediaMetadata.FOLDER_TYPE_ALBUMS)
                                        .build()
                                )
                                .build()
                        )
                    }
                }

                CATEGORY_SHOEBOX -> {
                    // List playlists / mixtapes
                    mergedPlaylists.forEach { tape ->
                        items.add(
                            MediaItem.Builder()
                                .setMediaId("$PREFIX_TAPE${tape.id}")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(tape.title)
                                        .setSubtitle(tape.subtitle)
                                        .setIsBrowsable(true)
                                        .setIsPlayable(true)
                                        .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                                        .build()
                                )
                                .build()
                        )
                    }
                }

                else -> {
                    if (parentId.startsWith(PREFIX_TAPE)) {
                        val targetTapeId = parentId.removePrefix(PREFIX_TAPE)
                        val allTapes = mergedArtists.values.flatten() + mergedPlaylists
                        val foundTape = allTapes.firstOrNull { it.id == targetTapeId }

                        foundTape?.tracks?.forEach { tr ->
                            items.add(
                                MediaItem.Builder()
                                    .setMediaId(tr.spotifyUri ?: tr.contentUri.toString())
                                    .setRequestMetadata(
                                        MediaItem.RequestMetadata.Builder()
                                            .setMediaUri(tr.contentUri)
                                            .build()
                                    )
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setTitle(tr.title)
                                            .setArtist(tr.artist)
                                            .setAlbumTitle(tr.album)
                                            .setIsBrowsable(false)
                                            .setIsPlayable(true)
                                            .build()
                                    )
                                    .build()
                            )
                        }
                    }
                }
            }

            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Sunset Pixels Track")
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            mediaItems.forEach { item ->
                val uriStr = item.mediaId
                if (uriStr.startsWith("spotify:")) {
                    SpotifyRemoteManager.play(this@PlaybackService, uriStr)
                }
            }
            return Futures.immediateFuture(mediaItems)
        }
    }
}

package com.retro.pixelplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.pixelplayer.audio.TapeItem
import com.retro.pixelplayer.audio.TapeType
import com.retro.pixelplayer.audio.Track
import com.retro.pixelplayer.feedback.MechanicalFeedback
import com.retro.pixelplayer.ui.theme.PixelPalette

enum class StoreCrateTab {
    ARTISTS,
    ALBUMS,
    SONGS
}

data class StoreAlbumItem(
    val albumName: String,
    val artistName: String,
    val tracks: List<Track>
)

/**
 * Sunset Record Shop & Local Cassette Bins.
 * Allows digging through crates of local Artists, Albums, and Songs,
 * playing any track or album instantly, and recording tracks to mixtapes.
 */
@Composable
fun RecordStoreScreen(
    localTracks: List<Track>,
    feedback: MechanicalFeedback?,
    onPlayTrack: (Track) -> Unit,
    onPlayTape: ((TapeItem) -> Unit)? = null,
    onAddToMixtape: (Track) -> Unit,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    var selectedTab by remember { mutableStateOf(StoreCrateTab.ARTISTS) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<StoreAlbumItem?>(null) }

    // Derived collections from localTracks
    val artistsMap = remember(localTracks) {
        localTracks.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    val albumsList = remember(localTracks) {
        localTracks.groupBy { "${it.artist}___${it.album}" }
            .map { (_, tracks) ->
                StoreAlbumItem(
                    albumName = tracks.first().album,
                    artistName = tracks.first().artist,
                    tracks = tracks
                )
            }
            .sortedBy { it.albumName.lowercase() }
    }

    val sortedSongs = remember(localTracks) {
        localTracks.sortedBy { it.title.lowercase() }
    }

    val filteredTracks = remember(localTracks, searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.isBlank()) emptyList()
        else {
            localTracks.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                it.artist.contains(trimmed, ignoreCase = true) ||
                it.album.contains(trimmed, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF19131C))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MechanicalButton(
                label = "◀ EXIT",
                baseColor = Color(0xFF37474F),
                highlightColor = Color(0xFF546E7A),
                shadowColor = Color(0xFF263238),
                width = 72.dp,
                height = 32.dp,
                onDownPress = { feedback?.onTick() },
                onClick = {
                    if (selectedAlbum != null) {
                        selectedAlbum = null
                    } else if (selectedArtist != null) {
                        selectedArtist = null
                    } else {
                        onBack()
                    }
                }
            )

            Text(
                text = "📻 SUNSET RECORD SHOP",
                color = Color(0xFFFFD54F),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )

            // Crate size indicator badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2E1C38))
                    .border(1.dp, Color(0xFF5E3A70), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${localTracks.size} TRACKS",
                    color = Color(0xFFCE93D8),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Input Bar (Retro CRT Search Box)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF261D2E))
                .border(2.dp, Color(0xFFFFD54F), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🔍",
                    fontSize = 16.sp
                )

                BasicTextField(
                    value = searchQuery,
                    onValueChange = { newText ->
                        searchQuery = newText
                    },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    cursorBrush = SolidColor(Color(0xFFFFD54F)),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "DIG FOR TRACKS, ARTISTS OR ALBUMS...",
                                color = Color.White.copy(alpha = 0.4f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                        innerTextField()
                    }
                )

                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = "✕",
                        color = Color.White.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable {
                                feedback?.onTick()
                                searchQuery = ""
                            }
                            .padding(4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Crate Section Tabs (Visible when not actively searching)
        if (searchQuery.isBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    StoreCrateTab.ARTISTS to "📁 ARTISTS (${artistsMap.size})",
                    StoreCrateTab.ALBUMS to "💿 ALBUMS (${albumsList.size})",
                    StoreCrateTab.SONGS to "🎵 SONGS (${localTracks.size})"
                ).forEach { (tab, label) ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFFFFD54F) else Color(0xFF261D2E))
                            .border(
                                1.5.dp,
                                if (isSelected) Color(0xFFFFE082) else Color(0xFF4A3859),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                feedback?.onTick()
                                selectedTab = tab
                                selectedArtist = null
                                selectedAlbum = null
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color(0xFF1E1428) else Color(0xFFD1C4E9),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Main Bin Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF221A28))
                .border(2.dp, Color(0xFF3B2E46), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            when {
                // Active Search Results
                searchQuery.isNotBlank() -> {
                    if (filteredTracks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "NO TAPES FOUND",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "No local tracks match \"$searchQuery\"",
                                    color = Color(0xFFAAA0B8),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredTracks, key = { "search_${it.id}" }) { track ->
                                TrackRowCard(
                                    track = track,
                                    feedback = feedback,
                                    onPlayTrack = onPlayTrack,
                                    onAddToMixtape = onAddToMixtape
                                )
                            }
                        }
                    }
                }

                // TAB 1: ARTISTS
                selectedTab == StoreCrateTab.ARTISTS -> {
                    if (selectedArtist != null) {
                        // Viewing specific Artist's crate
                        val artistName = selectedArtist!!
                        val artistTracks = artistsMap[artistName] ?: emptyList()
                        val artistAlbums = artistTracks.groupBy { it.album }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Subheader bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF37474F))
                                        .clickable {
                                            feedback?.onTick()
                                            selectedArtist = null
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "◀ ALL ARTISTS",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }

                                Text(
                                    text = artistName.uppercase(),
                                    color = Color(0xFFFFD54F),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(horizontal = 6.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(PixelPalette.PlayGreen)
                                        .clickable {
                                            feedback?.onTapeLatch()
                                            val tape = TapeItem(
                                                id = "artist_all_$artistName",
                                                title = "$artistName - ALL",
                                                subtitle = "${artistTracks.size} Tracks",
                                                type = TapeType.ARTIST_ALL,
                                                tracks = artistTracks,
                                                isHomeMade = false
                                            )
                                            if (onPlayTape != null) onPlayTape(tape)
                                            else onPlayTrack(artistTracks.first())
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "▶ PLAY ALL",
                                        color = Color(0xFF0D2818),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(artistTracks, key = { "art_tr_${it.id}" }) { track ->
                                    TrackRowCard(
                                        track = track,
                                        feedback = feedback,
                                        onPlayTrack = onPlayTrack,
                                        onAddToMixtape = onAddToMixtape
                                    )
                                }
                            }
                        }
                    } else {
                        // List all Artists
                        if (artistsMap.isEmpty()) {
                            EmptyCrateMessage("No local artists found on device.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(artistsMap.keys.toList(), key = { "artist_$it" }) { artist ->
                                    val tracks = artistsMap[artist] ?: emptyList()
                                    val albumCount = tracks.map { it.album }.distinct().size

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF251B2E))
                                            .border(1.dp, Color(0xFF433252), RoundedCornerShape(6.dp))
                                            .clickable {
                                                feedback?.onTick()
                                                selectedArtist = artist
                                            }
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = artist,
                                                    color = Color.White,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$albumCount Album${if (albumCount > 1) "s" else ""} • ${tracks.size} Songs",
                                                    color = Color(0xFFFFD54F),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp
                                                )
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(PixelPalette.PlayGreen)
                                                        .clickable {
                                                            feedback?.onTapeLatch()
                                                            val tape = TapeItem(
                                                                id = "artist_all_$artist",
                                                                title = "$artist - ALL",
                                                                subtitle = "${tracks.size} Tracks",
                                                                type = TapeType.ARTIST_ALL,
                                                                tracks = tracks,
                                                                isHomeMade = false
                                                            )
                                                            if (onPlayTape != null) onPlayTape(tape)
                                                            else onPlayTrack(tracks.first())
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = "▶ PLAY ALL",
                                                        color = Color(0xFF0D2818),
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 10.sp
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF37474F))
                                                        .clickable {
                                                            feedback?.onTick()
                                                            selectedArtist = artist
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = "BROWSE ▶",
                                                        color = Color.White,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: ALBUMS
                selectedTab == StoreCrateTab.ALBUMS -> {
                    if (selectedAlbum != null) {
                        val albumItem = selectedAlbum!!
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Subheader bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF37474F))
                                        .clickable {
                                            feedback?.onTick()
                                            selectedAlbum = null
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "◀ ALL ALBUMS",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }

                                Text(
                                    text = albumItem.albumName.uppercase(),
                                    color = Color(0xFFFFD54F),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(horizontal = 6.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(PixelPalette.PlayGreen)
                                        .clickable {
                                            feedback?.onTapeLatch()
                                            val tape = TapeItem(
                                                id = "album_${albumItem.artistName}_${albumItem.albumName}",
                                                title = albumItem.albumName,
                                                subtitle = "${albumItem.artistName} (${albumItem.tracks.size} Tracks)",
                                                type = TapeType.ALBUM,
                                                tracks = albumItem.tracks,
                                                isHomeMade = false
                                            )
                                            if (onPlayTape != null) onPlayTape(tape)
                                            else onPlayTrack(albumItem.tracks.first())
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "▶ PLAY ALBUM",
                                        color = Color(0xFF0D2818),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(albumItem.tracks, key = { "alb_tr_${it.id}" }) { track ->
                                    TrackRowCard(
                                        track = track,
                                        feedback = feedback,
                                        onPlayTrack = onPlayTrack,
                                        onAddToMixtape = onAddToMixtape
                                    )
                                }
                            }
                        }
                    } else {
                        // List all Albums
                        if (albumsList.isEmpty()) {
                            EmptyCrateMessage("No local albums found on device.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(albumsList, key = { "album_${it.artistName}_${it.albumName}" }) { album ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF251B2E))
                                            .border(1.dp, Color(0xFF433252), RoundedCornerShape(6.dp))
                                            .clickable {
                                                feedback?.onTick()
                                                selectedAlbum = album
                                            }
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = album.albumName,
                                                    color = Color.White,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${album.artistName} • ${album.tracks.size} Songs",
                                                    color = Color(0xFFFFD54F),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp
                                                )
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(PixelPalette.PlayGreen)
                                                        .clickable {
                                                            feedback?.onTapeLatch()
                                                            val tape = TapeItem(
                                                                id = "album_${album.artistName}_${album.albumName}",
                                                                title = album.albumName,
                                                                subtitle = "${album.artistName} (${album.tracks.size} Tracks)",
                                                                type = TapeType.ALBUM,
                                                                tracks = album.tracks,
                                                                isHomeMade = false
                                                            )
                                                            if (onPlayTape != null) onPlayTape(tape)
                                                            else onPlayTrack(album.tracks.first())
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = "▶ PLAY",
                                                        color = Color(0xFF0D2818),
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 10.sp
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF37474F))
                                                        .clickable {
                                                            feedback?.onTick()
                                                            selectedAlbum = album
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = "TRACKS ▶",
                                                        color = Color.White,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 3: SONGS
                selectedTab == StoreCrateTab.SONGS -> {
                    if (sortedSongs.isEmpty()) {
                        EmptyCrateMessage("No local tracks found on device.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(sortedSongs, key = { "song_${it.id}" }) { track ->
                                TrackRowCard(
                                    track = track,
                                    feedback = feedback,
                                    onPlayTrack = onPlayTrack,
                                    onAddToMixtape = onAddToMixtape
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCrateMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "CRATE EMPTY",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
            Text(
                text = message,
                color = Color(0xFFAAA0B8),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TrackRowCard(
    track: Track,
    feedback: MechanicalFeedback?,
    onPlayTrack: (Track) -> Unit,
    onAddToMixtape: (Track) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF231F2E))
            .border(1.dp, Color(0xFF4A3C5E), RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Local Tape Mini Icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1E88E5))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LOC",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp
                )
            }

            // Track Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = track.artist,
                        color = Color(0xFFFFD54F),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (track.album.isNotBlank()) {
                        Text(
                            text = "• ${track.album}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Action Buttons: PLAY | 🔴 REC
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // PLAY Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(PixelPalette.PlayGreen)
                        .clickable {
                            feedback?.onTapeLatch()
                            onPlayTrack(track)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "▶ PLAY",
                        color = Color(0xFF0D2818),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }

                // REC TO MIXTAPE Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFD32F2F))
                        .clickable {
                            feedback?.onTick()
                            onAddToMixtape(track)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🔴 REC",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

package com.retro.pixelplayer.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.retro.pixelplayer.audio.TapeItem
import com.retro.pixelplayer.audio.TapeType
import com.retro.pixelplayer.feedback.MechanicalFeedback
import com.retro.pixelplayer.ui.theme.PixelPalette

enum class ShelfMode {
    OVERVIEW,       // The Shelf vs Shoe Box selection
    ARTISTS_LIST,   // Choose which Artist
    ARTIST_TAPES,   // Choose between "All" tape or specific Album tapes
    SHOEBOX_TAPES   // Flick through handmade playlist tapes in shoe box
}

enum class TapeSourceFilter {
    ALL,
    LOCAL,
    SPOTIFY
}

/**
 * Tape selection screen featuring a wooden Artist Shelf and a cardboard Playlist Shoe Box.
 */
@Composable
fun TapeSelectionScreen(
    artistTapes: Map<String, List<TapeItem>>,
    playlistTapes: List<TapeItem>,
    feedback: MechanicalFeedback,
    isSpotifyLoggedIn: Boolean = false,
    onSpotifyClicked: () -> Unit = {},
    onTapeSelected: (TapeItem) -> Unit,
    onBackToPlayer: () -> Unit
) {
    var mode by remember { mutableStateOf(ShelfMode.OVERVIEW) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var sourceFilter by rememberSaveable { mutableStateOf(TapeSourceFilter.ALL) }

    // Filtered collections based on user source filter
    val filteredArtistTapes = remember(artistTapes, sourceFilter) {
        artistTapes.mapValues { (_, tapes) ->
            when (sourceFilter) {
                TapeSourceFilter.ALL -> tapes
                TapeSourceFilter.LOCAL -> tapes.filter { !it.isSpotify }
                TapeSourceFilter.SPOTIFY -> tapes.filter { it.isSpotify }
            }
        }.filterValues { it.isNotEmpty() }
    }

    val filteredPlaylistTapes = remember(playlistTapes, sourceFilter) {
        when (sourceFilter) {
            TapeSourceFilter.ALL -> playlistTapes
            TapeSourceFilter.LOCAL -> playlistTapes.filter { !it.isSpotify }
            TapeSourceFilter.SPOTIFY -> playlistTapes.filter { it.isSpotify }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.RoomDark)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header navigation bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MechanicalButton(
                label = "◀ DECK",
                baseColor = Color(0xFF37474F),
                highlightColor = Color(0xFF546E7A),
                shadowColor = Color(0xFF263238),
                width = 72.dp,
                height = 32.dp,
                onDownPress = { feedback.onTick() },
                onClick = {
                    if (mode != ShelfMode.OVERVIEW) {
                        mode = if (mode == ShelfMode.ARTIST_TAPES) ShelfMode.ARTISTS_LIST else ShelfMode.OVERVIEW
                    } else {
                        onBackToPlayer()
                    }
                }
            )

            Text(
                text = when (mode) {
                    ShelfMode.OVERVIEW -> "CASSETTE COLLECTION"
                    ShelfMode.ARTISTS_LIST -> "ARTIST SHELF"
                    ShelfMode.ARTIST_TAPES -> selectedArtist?.uppercase() ?: "TAPES"
                    ShelfMode.SHOEBOX_TAPES -> "PLAYLIST SHOE BOX"
                },
                color = PixelPalette.ChassisHighlight,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )

            MechanicalButton(
                label = if (isSpotifyLoggedIn) "🟢 LINKED" else "🟢 SPOTIFY",
                baseColor = Color(0xFF1B5E20),
                highlightColor = Color(0xFF2E7D32),
                shadowColor = Color(0xFF0D3B10),
                width = 88.dp,
                height = 32.dp,
                onDownPress = { feedback.onTick() },
                onClick = { onSpotifyClicked() }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3-Way Source Filter Toggle: ALL | LOCAL | SPOTIFY
        TapeFilterBar(
            selectedFilter = sourceFilter,
            onFilterSelected = { filter ->
                sourceFilter = filter
            },
            feedback = feedback
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (mode) {
            ShelfMode.OVERVIEW -> {
                // Two big tactile retro objects: Wooden Shelf vs Cardboard Shoe Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "WHERE ARE YOU LOOKING?",
                        color = Color.White.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )

                    // 1. THE WOODEN SHELF (Artists & Official Albums)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF4E342E)) // Rich dark wood
                            .border(4.dp, Color(0xFF2D1E1B), RoundedCornerShape(12.dp))
                            .clickable {
                                feedback.onHeavyButtonPress()
                                mode = ShelfMode.ARTISTS_LIST
                            }
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "❚ ARTIST SHELF",
                                    color = Color(0xFFFFD54F),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "${filteredArtistTapes.keys.size} ARTISTS",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }

                            // Pixel art representation of stacked tapes on a wooden shelf
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2E1C14))
                                    .border(2.dp, Color(0xFF1B0F0B), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(7) { i ->
                                    Box(
                                        modifier = Modifier
                                            .width(22.dp)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(
                                                when (i % 4) {
                                                    0 -> Color(0xFF1A237E)
                                                    1 -> Color(0xFFB71C1C)
                                                    2 -> Color(0xFF004D40)
                                                    else -> Color(0xFF4A148C)
                                                }
                                            )
                                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                    )
                                }
                            }

                            Text(
                                text = "Albums, discographies, and artist tapes with official cover art.",
                                color = Color(0xFFD7CCC8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // 2. THE CARDBOARD SHOE BOX (Handmade Playlists & Mixtapes)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF8D6E63)) // Cardboard brown
                            .border(4.dp, Color(0xFF5D4037), RoundedCornerShape(12.dp))
                            .clickable {
                                feedback.onHeavyButtonPress()
                                mode = ShelfMode.SHOEBOX_TAPES
                            }
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📦 PLAYLIST SHOE BOX",
                                    color = Color(0xFFFFF9C4),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "${filteredPlaylistTapes.size} MIXTAPES",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }

                            // Cardboard tape dividers look
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF4E342E))
                                    .border(2.dp, Color(0xFF3E2723), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(5) { i ->
                                    Box(
                                        modifier = Modifier
                                            .width(36.dp)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFFFFF9C4)) // Masking tape sticker look
                                            .border(1.dp, Color(0xFF795548), RoundedCornerShape(3.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "MIX",
                                            color = Color.Black,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Handmade mixtapes, custom playlists, and party tapes with handwritten labels.",
                                color = Color(0xFFEFEBE9),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            ShelfMode.ARTISTS_LIST -> {
                // List of Artists on the shelf
                Text(
                    text = "SELECT AN ARTIST TO BROWSE TAPES:",
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (filteredArtistTapes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF231D2D))
                            .border(2.dp, Color(0xFF382F45), RoundedCornerShape(8.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "NO ARTISTS FOUND",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                            Text(
                                text = when (sourceFilter) {
                                    TapeSourceFilter.LOCAL -> "No local artist audio files found on device."
                                    TapeSourceFilter.SPOTIFY -> if (isSpotifyLoggedIn) "No Spotify artists found in your library." else "Spotify is not linked. Tap SPOTIFY above to log in."
                                    TapeSourceFilter.ALL -> "No cassette tapes available."
                                },
                                color = Color(0xFFAAA0B8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredArtistTapes.keys.toList()) { artist ->
                            val tapes = filteredArtistTapes[artist] ?: emptyList()
                            val sampleArt = tapes.firstOrNull { it.artworkBitmap != null }?.artworkBitmap

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2C243B))
                                    .border(2.dp, PixelPalette.ChassisBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        feedback.onTick()
                                        selectedArtist = artist
                                        mode = ShelfMode.ARTIST_TAPES
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (sampleArt != null) {
                                        Image(
                                            bitmap = sampleArt.asImageBitmap(),
                                            contentDescription = artist,
                                            filterQuality = FilterQuality.None,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .border(1.dp, PixelPalette.ChassisHighlight, RoundedCornerShape(4.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(PixelPalette.TapeSpool, RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = "🎵", fontSize = 18.sp)
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = artist.uppercase(),
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp
                                        )
                                        val localCount = tapes.count { !it.isSpotify }
                                        val spotifyCount = tapes.count { it.isSpotify }
                                        val subtitleText = when {
                                            localCount > 0 && spotifyCount > 0 -> "$localCount Local • $spotifyCount Spotify"
                                            spotifyCount > 0 -> "$spotifyCount Spotify Album${if (spotifyCount > 1) "s" else ""}"
                                            else -> "$localCount Local Tape${if (localCount > 1) "s" else ""}"
                                        }
                                        Text(
                                            text = subtitleText,
                                            color = PixelPalette.ChassisHighlight,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        )
                                    }

                                    Text(
                                        text = "▶",
                                        color = PixelPalette.PlayGreen,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ShelfMode.ARTIST_TAPES -> {
                // Flick through the artist's tapes (ALL tape + Album tapes)
                val tapes = filteredArtistTapes[selectedArtist] ?: emptyList()

                Text(
                    text = "FLICK THROUGH ${selectedArtist?.uppercase()}'S TAPES:",
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (tapes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF231D2D))
                            .border(2.dp, Color(0xFF382F45), RoundedCornerShape(8.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "NO TAPES MATCHING FILTER",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "This artist has no ${sourceFilter.name.lowercase()} tapes. Try switching to ALL.",
                                color = Color(0xFFAAA0B8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(tapes) { tape ->
                            TapeSpineItem(
                                tape = tape,
                                feedback = feedback,
                                onSelect = {
                                    feedback.onTapeLatch()
                                    onTapeSelected(tape)
                                }
                            )
                        }
                    }
                }
            }

            ShelfMode.SHOEBOX_TAPES -> {
                // Flick through the Shoebox playlists
                Text(
                    text = "FLICK THROUGH HOMEMADE PLAYLISTS:",
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (filteredPlaylistTapes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF231D2D))
                            .border(2.dp, Color(0xFF382F45), RoundedCornerShape(8.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "NO PLAYLISTS FOUND",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                            Text(
                                text = when (sourceFilter) {
                                    TapeSourceFilter.LOCAL -> "No local playlist tapes found on device."
                                    TapeSourceFilter.SPOTIFY -> if (isSpotifyLoggedIn) "No Spotify playlists found in library." else "Spotify is not linked. Tap SPOTIFY above to log in."
                                    TapeSourceFilter.ALL -> "No playlists found in shoe box."
                                },
                                color = Color(0xFFAAA0B8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredPlaylistTapes) { tape ->
                            TapeSpineItem(
                                tape = tape,
                                feedback = feedback,
                                onSelect = {
                                    feedback.onTapeLatch()
                                    onTapeSelected(tape)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A Cassette Spine that you flick through on the shelf or shoe box.
 * Includes pixelated album artwork, spine title, and retro casing highlights.
 */
@Composable
fun TapeSpineItem(
    tape: TapeItem,
    feedback: MechanicalFeedback,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(tape.spineColorHex))
            .border(2.dp, Color.Black, RoundedCornerShape(6.dp))
            .clickable {
                onSelect()
            }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Pixelated Album / Tape Artwork
            if (tape.artworkBitmap != null) {
                Image(
                    bitmap = tape.artworkBitmap.asImageBitmap(),
                    contentDescription = tape.title,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                )
            } else {
                // Homemade tape sticker icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(tape.labelColorHex))
                        .border(1.dp, Color(0xFF8D6E63), RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tape.isHomeMade) "C-90" else "TAPE",
                        color = Color.Black,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }
            }

            // Cassette Spine Label
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Tape Title
                    Text(
                        text = tape.title.uppercase(),
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Source Badge: 🟢 SPOTIFY or 💾 LOCAL
                    val badgeText = if (tape.isSpotify) "SPOTIFY" else "LOCAL"
                    val badgeColor = if (tape.isSpotify) Color(0xFF81C784) else Color(0xFF64B5F6)
                    val badgeBorder = if (tape.isSpotify) Color(0xFF2E7D32) else Color(0xFF1976D2)
                    val badgeBg = if (tape.isSpotify) Color(0xFF142E18) else Color(0xFF10283F)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(badgeBg)
                            .border(1.dp, badgeBorder, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        )
                    }
                }

                // Subtitle
                Text(
                    text = tape.subtitle,
                    color = Color(tape.labelColorHex),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // "LOAD TAPE" button pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(PixelPalette.PlayGreen)
                    .border(1.dp, PixelPalette.PlayGreenHighlight, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "INSERT ⏏",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * 3-Way Retro segmented filter toggle: ALL | 💾 LOCAL | 🟢 SPOTIFY
 */
@Composable
fun TapeFilterBar(
    selectedFilter: TapeSourceFilter,
    onFilterSelected: (TapeSourceFilter) -> Unit,
    feedback: MechanicalFeedback
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E1A29))
            .border(2.dp, Color(0xFF2E273D), RoundedCornerShape(6.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TapeSourceFilter.values().forEach { filter ->
            val isSelected = filter == selectedFilter

            val (activeBg, activeBorder, activeText) = when (filter) {
                TapeSourceFilter.ALL -> Triple(Color(0xFF453D28), Color(0xFFFFB300), Color(0xFFFFD54F))
                TapeSourceFilter.LOCAL -> Triple(Color(0xFF152F47), Color(0xFF29B6F6), Color(0xFF81D4FA))
                TapeSourceFilter.SPOTIFY -> Triple(Color(0xFF17381C), Color(0xFF4CAF50), Color(0xFFA5D6A7))
            }

            val bg = if (isSelected) activeBg else Color(0xFF252033)
            val borderColor = if (isSelected) activeBorder else Color(0xFF383049)
            val textColor = if (isSelected) activeText else Color(0xFF8D859B)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(bg)
                    .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(4.dp))
                    .clickable {
                        if (!isSelected) {
                            feedback.onTick()
                            onFilterSelected(filter)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isSelected) {
                        Text(
                            text = "▶",
                            color = textColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = when (filter) {
                            TapeSourceFilter.ALL -> "ALL TAPES"
                            TapeSourceFilter.LOCAL -> "💾 LOCAL"
                            TapeSourceFilter.SPOTIFY -> "🟢 SPOTIFY"
                        },
                        color = textColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

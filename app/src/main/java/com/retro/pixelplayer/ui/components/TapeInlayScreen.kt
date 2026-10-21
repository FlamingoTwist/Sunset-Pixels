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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.pixelplayer.audio.TapeItem
import com.retro.pixelplayer.audio.Track
import com.retro.pixelplayer.feedback.MechanicalFeedback
import com.retro.pixelplayer.ui.theme.PixelPalette

/**
 * Cassette Inlay (J-Card inside sleeve) Screen.
 * Renders an authentic ruled-paper cassette insert sleeve where you can read, play,
 * and record tracks to mixtapes.
 */
@Composable
fun TapeInlayScreen(
    tape: TapeItem,
    feedback: MechanicalFeedback?,
    onPlayTape: (TapeItem, Int) -> Unit,
    onAddToMixtape: (Track) -> Unit,
    onGoToArtist: (String) -> Unit,
    onRemoveTrack: ((Int) -> Unit)? = null,
    onDeleteMixtape: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val totalDurationMs = remember(tape.tracks) {
        tape.tracks.sumOf { it.durationMs }
    }
    val totalMinutes = (totalDurationMs / 1000) / 60
    val totalSeconds = (totalDurationMs / 1000) % 60
    val formattedTotalTime = if (totalDurationMs > 0) {
        String.format("%02dm %02ds", totalMinutes, totalSeconds)
    } else {
        "--m --s"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.RoomDark)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MechanicalButton(
                label = "◀ BACK",
                baseColor = Color(0xFF37474F),
                highlightColor = Color(0xFF546E7A),
                shadowColor = Color(0xFF263238),
                width = 76.dp,
                height = 32.dp,
                onDownPress = { feedback?.onTick() },
                onClick = { onBack() }
            )

            Text(
                text = "CASSETTE J-CARD INLAY",
                color = PixelPalette.ChassisHighlight,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )

            MechanicalButton(
                label = "▶ PLAY ALL",
                baseColor = Color(0xFF1B5E20),
                highlightColor = Color(0xFF2E7D32),
                shadowColor = Color(0xFF0D3B10),
                width = 88.dp,
                height = 32.dp,
                onDownPress = { feedback?.onTapeLatch() },
                onClick = { onPlayTape(tape, 0) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // THE PHYSICAL CASSETTE J-CARD PAPER SLEEVE
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF7F4EA)) // Vintage off-white cassette paper
                .border(3.dp, Color(0xFF8D6E63), RoundedCornerShape(8.dp)) // Cardboard spine border
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Folded J-Card Spine Flap (Top header of the insert)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(tape.spineColorHex))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (tape.artworkBitmap != null) {
                            Image(
                                bitmap = tape.artworkBitmap.asImageBitmap(),
                                contentDescription = tape.title,
                                filterQuality = FilterQuality.None,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(tape.labelColorHex))
                                    .border(1.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (tape.isHomeMade) "C-90" else "TAPE",
                                    color = Color.Black,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tape.title.uppercase(),
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = tape.subtitle,
                                color = Color(tape.labelColorHex),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // J-Card Format Badge
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "EQ: 120µs",
                                color = Color.White.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                            Text(
                                text = formattedTotalTime,
                                color = Color(0xFFFFD54F),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Perforated fold line separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0xFF3E2723))
                )

                // 2. Ruled Track List Area
                if (tape.tracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "BLANK CASSETTE TAPE",
                                color = Color(0xFF5D4037),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "No tracks recorded on this mixtape yet.\nUse the 🔴 REC button on any song to record here!",
                                color = Color(0xFF795548),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        itemsIndexed(tape.tracks) { index, track ->
                            val isSpotify = track.spotifyUri != null || track.contentUri.scheme == "spotify"

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // Ruled lined paper effect (alternate faint tint)
                                    .background(if (index % 2 == 0) Color(0xFFFAF7EE) else Color(0xFFF1EDE0))
                                    .border(
                                        width = 0.5.dp,
                                        color = Color(0xFFD7CCC8)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Track Number (Typewritten / handwriting look)
                                    Text(
                                        text = String.format("%02d.", index + 1),
                                        color = Color(0xFF3E2723),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(26.dp)
                                    )

                                    // Track Info
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            color = Color(0xFF1B1B1B),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
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
                                                color = Color(0xFF5D4037),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .clickable {
                                                        if (!isSpotify && track.artist.isNotBlank() && track.artist != "Unknown Artist") {
                                                            feedback?.onTick()
                                                            onGoToArtist(track.artist)
                                                        }
                                                    }
                                            )

                                            // Badge: LOCAL vs SPOTIFY
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(if (isSpotify) Color(0xFFE8F5E9) else Color(0xFFE3F2FD))
                                                    .border(0.5.dp, if (isSpotify) Color(0xFF4CAF50) else Color(0xFF2196F3), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = if (isSpotify) "SPOTIFY" else "LOCAL",
                                                    color = if (isSpotify) Color(0xFF2E7D32) else Color(0xFF1565C0),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            if (track.durationMs > 0) {
                                                val durSec = (track.durationMs / 1000) % 60
                                                val durMin = (track.durationMs / 1000) / 60
                                                Text(
                                                    text = String.format("%02d:%02d", durMin, durSec),
                                                    color = Color(0xFF8D6E63),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }

                                    // Action buttons row: PLAY | REC | REMOVE
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 1. PLAY Track Button
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(PixelPalette.PlayGreen)
                                                .clickable {
                                                    feedback?.onTapeLatch()
                                                    onPlayTape(tape, index)
                                                }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "▶",
                                                color = Color.Black,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // 2. REC (Add to another Mixtape) Button - Local files only
                                        if (!isSpotify) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFFD32F2F))
                                                    .clickable {
                                                        feedback?.onTick()
                                                        onAddToMixtape(track)
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "🔴",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // 3. REMOVE from this Mixtape (if custom mixtape)
                                        if (onRemoveTrack != null && tape.isHomeMade) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFFEEEEEE))
                                                    .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        feedback?.onTick()
                                                        onRemoveTrack(index)
                                                    }
                                                    .padding(horizontal = 5.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "✕",
                                                    color = Color(0xFFB71C1C),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Bottom Sleeve Footer (Delete mixtape option if user-created)
                if (onDeleteMixtape != null && tape.isHomeMade) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEFEBE9))
                            .border(width = 1.dp, color = Color(0xFFD7CCC8))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!showDeleteConfirm) {
                            Text(
                                text = "🗑 DELETE THIS MIXTAPE",
                                color = Color(0xFFC62828),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier
                                    .clickable {
                                        feedback?.onTick()
                                        showDeleteConfirm = true
                                    }
                                    .padding(4.dp)
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "DISCARD TAPE?",
                                    color = Color(0xFFB71C1C),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFB71C1C))
                                        .clickable {
                                            feedback?.onHeavyButtonPress()
                                            onDeleteMixtape()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "YES, DELETE",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF757575))
                                        .clickable {
                                            feedback?.onTick()
                                            showDeleteConfirm = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "CANCEL",
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

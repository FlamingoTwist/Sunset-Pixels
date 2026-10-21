package com.retro.pixelplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.retro.pixelplayer.audio.TapeItem
import com.retro.pixelplayer.audio.Track
import com.retro.pixelplayer.feedback.MechanicalFeedback
import com.retro.pixelplayer.ui.theme.PixelPalette

@Composable
fun RecordToMixtapeDialog(
    track: Track,
    userMixtapes: List<TapeItem>,
    feedback: MechanicalFeedback?,
    onAddToMixtape: (mixtapeId: String) -> Unit,
    onCreateAndAdd: (mixtapeTitle: String) -> Unit,
    onDismiss: () -> Unit
) {
    var isCreatingNew by remember { mutableStateOf(userMixtapes.isEmpty()) }
    var newMixtapeName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1622))
                .border(3.dp, Color(0xFFD32F2F), RoundedCornerShape(12.dp)) // Red recording trim
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF1744))
                        )
                        Text(
                            text = "🔴 RECORD TO MIXTAPE",
                            color = Color(0xFFFF5252),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }

                    Text(
                        text = "✕",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                feedback?.onTick()
                                onDismiss()
                            }
                            .padding(4.dp)
                    )
                }

                // Currently Recording Track Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2B2030))
                        .border(1.dp, Color(0xFF4A3854), RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "CURRENT AUDIO SIGNAL:",
                            color = Color(0xFFAAA0B8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                        Text(
                            text = track.title.uppercase(),
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
                                color = PixelPalette.ChassisHighlight,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            val isSpotify = track.spotifyUri != null || track.contentUri.scheme == "spotify"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isSpotify) Color(0xFF142E18) else Color(0xFF10283F))
                                    .border(1.dp, if (isSpotify) Color(0xFF2E7D32) else Color(0xFF1976D2), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (isSpotify) "SPOTIFY" else "LOCAL",
                                    color = if (isSpotify) Color(0xFF81C784) else Color(0xFF64B5F6),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (isCreatingNew || userMixtapes.isEmpty()) {
                    // Create New Mixtape Form
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "LABEL YOUR NEW BLANK CASSETTE:",
                            color = Color(0xFFFFD54F),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )

                        BasicTextField(
                            value = newMixtapeName,
                            onValueChange = { newMixtapeName = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(Color(0xFFFFD54F)),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF14101A))
                                .border(1.dp, Color(0xFFFFD54F), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            decorationBox = { innerTextField ->
                                if (newMixtapeName.isEmpty()) {
                                    Text(
                                        text = "e.g. Summer Roadtrip '88",
                                        color = Color.White.copy(alpha = 0.3f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (userMixtapes.isNotEmpty()) {
                                MechanicalButton(
                                    label = "CANCEL",
                                    baseColor = Color(0xFF37474F),
                                    highlightColor = Color(0xFF546E7A),
                                    shadowColor = Color(0xFF263238),
                                    height = 36.dp,
                                    modifier = Modifier.weight(1f),
                                    onDownPress = { feedback?.onTick() },
                                    onClick = { isCreatingNew = false }
                                )
                            }

                            MechanicalButton(
                                label = "🔴 RECORD TRACK",
                                baseColor = Color(0xFFB71C1C),
                                highlightColor = Color(0xFFE53935),
                                shadowColor = Color(0xFF7F0000),
                                height = 36.dp,
                                modifier = Modifier.weight(1f),
                                onDownPress = { feedback?.onTapeLatch() },
                                onClick = {
                                    val title = newMixtapeName.ifBlank { "MIXTAPE VOL. ${userMixtapes.size + 1}" }
                                    onCreateAndAdd(title)
                                }
                            )
                        }
                    }
                } else {
                    // Pick Existing Mixtape or Create New
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SELECT TARGET MIXTAPE:",
                                color = Color.White.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )

                            Text(
                                text = "+ NEW MIXTAPE",
                                color = Color(0xFFFFD54F),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .clickable {
                                        feedback?.onTick()
                                        isCreatingNew = true
                                    }
                                    .padding(4.dp)
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(userMixtapes, key = { it.id }) { tape ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(tape.spineColorHex))
                                        .border(1.dp, Color.Black, RoundedCornerShape(6.dp))
                                        .clickable {
                                            feedback?.onTapeLatch()
                                            onAddToMixtape(tape.id)
                                        }
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tape.title.uppercase(),
                                                color = Color.White,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${tape.tracks.size} Tracks recorded",
                                                color = Color(tape.labelColorHex),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFD32F2F))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "🔴 REC HERE",
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
    }
}

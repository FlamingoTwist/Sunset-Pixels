package com.retro.pixelplayer.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.pixelplayer.R
import com.retro.pixelplayer.ui.theme.PixelPalette
import kotlinx.coroutines.delay

/**
 * Authentic Cassette Tape Deck using pixel-art sliced assets:
 * - deck_cassette_base: Full door frame, glass reflection, warm top lamp & cassette
 * - deck_empty_base: Open cassette well with drive spindles & head mechanism when ejected
 * - spinner_hub_left & spinner_hub_right: Clean circular cutouts rotated in mechanical steps (not smoothly)
 * - Superimposed tape label text matching physical C-90 cassette typography
 */
@Composable
fun CassetteDeck(
    modifier: Modifier = Modifier,
    isTapeInserted: Boolean,
    isPlaying: Boolean,
    tapeTitle: String,
    tapeSubtitle: String,
    labelColor: Color = PixelPalette.TapeLabelPink,
    shellColor: Color = PixelPalette.TapeBodyWhite,
    isHomeMade: Boolean = false,
    artworkBitmap: Bitmap? = null,
    isShuddering: Boolean = false,
    onEjectOrInsert: () -> Unit
) {
    // Stepped ratchet mechanical rotation (not smoothly)
    var stepCount by remember { mutableStateOf(0) }
    LaunchedEffect(isPlaying, isTapeInserted) {
        if (isPlaying && isTapeInserted) {
            while (true) {
                delay(90L) // Mechanical discrete step every 90ms
                stepCount = (stepCount + 1) % 24
            }
        }
    }

    // Mechanical tape motor vibration / jam shudder animation
    var shakeX by remember { mutableStateOf(0f) }
    LaunchedEffect(isShuddering) {
        if (isShuddering) {
            val pattern = listOf(4.5f, -4.5f, 4f, -4f, 3f, -3f, 2f, -2f, 1f, -1f, 0f)
            for (offset in pattern) {
                shakeX = offset
                delay(40L)
            }
            shakeX = 0f
        } else {
            shakeX = 0f
        }
    }

    val currentRotation = if (isPlaying && isTapeInserted) {
        stepCount * 15f // 15 degrees per chunky mechanical jump
    } else {
        0f
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1024f / 593f)
            .offset(x = shakeX.dp)
            .clickable { onEjectOrInsert() },
        contentAlignment = Alignment.TopStart
    ) {
        val deckWidth = maxWidth
        val deckHeight = maxHeight

        if (!isTapeInserted) {
            // Ejected Empty Deck Bay
            Image(
                painter = painterResource(id = R.drawable.deck_empty_base),
                contentDescription = "Empty Deck Bay",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            // Prompt centered in the empty tape bay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "[ NO CASSETTE INSERTED ]",
                    color = Color.White.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "TAP DECK TO BROWSE TAPES",
                    color = Color(0xFFFFAE52),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else {
            // Base Deck with Tape
            Image(
                painter = painterResource(id = R.drawable.deck_cassette_base),
                contentDescription = "Cassette Deck",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            // Superimposed Stepped Rotating Spinners (Left & Right)
            // Left Hub: true center = (339, 271), diameter = 111 in 1024x593
            // Right Hub: true center = (681, 271), diameter = 111 in 1024x593
            val spinnerSize = deckWidth * (111f / 1024f)

            // Left Spinner - rotates cleanly around true center with zero oscillation
            val leftOffsetX = deckWidth * (339f / 1024f) - (spinnerSize / 2f)
            val leftOffsetY = deckHeight * (271f / 593f) - (spinnerSize / 2f)

            Image(
                painter = painterResource(id = R.drawable.spinner_hub_left),
                contentDescription = "Left Tape Spool",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = leftOffsetX, y = leftOffsetY)
                    .size(spinnerSize)
                    .rotate(currentRotation),
                contentScale = ContentScale.Fit
            )

            // Right Spinner - rotates cleanly around true center with zero oscillation
            val rightOffsetX = deckWidth * (681f / 1024f) - (spinnerSize / 2f)
            val rightOffsetY = deckHeight * (271f / 593f) - (spinnerSize / 2f)

            Image(
                painter = painterResource(id = R.drawable.spinner_hub_right),
                contentDescription = "Right Tape Spool",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = rightOffsetX, y = rightOffsetY)
                    .size(spinnerSize)
                    .rotate(currentRotation),
                contentScale = ContentScale.Fit
            )

            // Superimposed Text on Tape Label
            // Label bounding box in 1024x593: X=[176, 832], Y=[111, 219]
            val labelLeft = deckWidth * (182f / 1024f)
            val labelTop = deckHeight * (116f / 593f)
            val labelWidth = deckWidth * (644f / 1024f)
            val labelHeight = deckHeight * (72f / 593f)

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = labelLeft, y = labelTop)
                    .size(width = labelWidth, height = labelHeight)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Top header line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isHomeMade) "★ MIXTAPE ★" else "SIDE A",
                            color = Color(0xFF4A3E38),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 8.sp
                        )
                        Text(
                            text = if (isHomeMade) "NORMAL BIAS C-90" else "16-BIT STEREO",
                            color = Color(0xFF5A4C44),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 7.sp
                        )
                    }

                    // Main Tape Title (sitting along the ruled line)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (artworkBitmap != null) {
                            Image(
                                bitmap = artworkBitmap.asImageBitmap(),
                                contentDescription = "Album Art",
                                filterQuality = FilterQuality.None,
                                modifier = Modifier
                                    .size(13.dp)
                                    .border(1.dp, Color(0xFF4A3E38))
                            )
                        }
                        Text(
                            text = tapeTitle.uppercase(),
                            color = Color(0xFF1E1714),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Cassette Deck - Playing", widthDp = 512, heightDp = 296)
@Composable
private fun CassetteDeckPlayingPreview() {
    CassetteDeck(
        isTapeInserted = true,
        isPlaying = true,
        tapeTitle = "Summer Chill Mixtape '86",
        tapeSubtitle = "Homemade Playlist",
        labelColor = PixelPalette.TapeLabelPink,
        shellColor = Color(0xFFF5F5DC),
        isHomeMade = true,
        onEjectOrInsert = {}
    )
}

@Preview(name = "Cassette Deck - Empty", widthDp = 512, heightDp = 296)
@Composable
private fun CassetteDeckEmptyPreview() {
    CassetteDeck(
        isTapeInserted = false,
        isPlaying = false,
        tapeTitle = "",
        tapeSubtitle = "",
        onEjectOrInsert = {}
    )
}

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
    onEjectOrInsert: () -> Unit
) {
    // Stepped alternating cog rotation (out of phase between L and R)
    var stepCount by remember { mutableStateOf(0) }
    LaunchedEffect(isPlaying, isTapeInserted) {
        if (isPlaying && isTapeInserted) {
            while (true) {
                delay(150L) // Mechanical discrete step every 150ms
                stepCount = (stepCount + 1) % 2
            }
        } else {
            stepCount = 0
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(250f / 144f)
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
                    .padding(bottom = 12.dp),
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
            // 1. Back Layer (Tape Inner Bay + Inner Cassette Backing)
            Image(
                painter = painterResource(id = R.drawable.deck_cassette_back),
                contentDescription = "Cassette Backing",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            // 2. Dynamic Wobbling Tape Reels (not spools)
            // Left Reel: wound tape pack around left hub
            // Right Reel: wound tape pack around right hub
            // 1 to 2 pixel authentic analog wobble when playing
            val pxUnitX = deckWidth / 250f
            val pxUnitY = deckHeight / 144f

            val leftWobble = when ((stepCount + 0) % 4) {
                1 -> Pair(1.5f, -1.0f)
                2 -> Pair(0.0f, 1.5f)
                3 -> Pair(-1.5f, 0.5f)
                else -> Pair(0.0f, 0.0f)
            }
            val rightWobble = when ((stepCount + 2) % 4) {
                1 -> Pair(-1.0f, 1.5f)
                2 -> Pair(1.5f, -0.5f)
                3 -> Pair(0.0f, -1.5f)
                else -> Pair(0.0f, 0.0f)
            }

            val lOffX = pxUnitX * (if (isPlaying) leftWobble.first else 0f)
            val lOffY = pxUnitY * (if (isPlaying) leftWobble.second else 0f)
            val rOffX = pxUnitX * (if (isPlaying) rightWobble.first else 0f)
            val rOffY = pxUnitY * (if (isPlaying) rightWobble.second else 0f)

            Image(
                painter = painterResource(id = R.drawable.deck_reel_left),
                contentDescription = "Left Tape Reel",
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = lOffX, y = lOffY),
                contentScale = ContentScale.FillBounds
            )

            Image(
                painter = painterResource(id = R.drawable.deck_reel_right),
                contentDescription = "Right Tape Reel",
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = rOffX, y = rOffY),
                contentScale = ContentScale.FillBounds
            )

            // 3. Cassette Shell Front & Acrylic Door (Beige casing with window cutout, shine, auto-reverse)
            Image(
                painter = painterResource(id = R.drawable.deck_cassette_front),
                contentDescription = "Cassette Front",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            // Alternating Mechanical Cogs (Cog R Rot when Cog L is normal, and vice versa)
            // Left Hub: center = (83, 66) in 250x144, size = 29x29
            // Right Hub: center = (163, 66) in 250x144, size = 29x29
            val spinnerSize = deckWidth * (29f / 250f)

            val leftOffsetX = deckWidth * (83f / 250f) - (spinnerSize / 2f)
            val leftOffsetY = deckHeight * (66f / 144f) - (spinnerSize / 2f)

            val rightOffsetX = deckWidth * (163f / 250f) - (spinnerSize / 2f)
            val rightOffsetY = deckHeight * (66f / 144f) - (spinnerSize / 2f)

            val leftCogRes = if (stepCount == 0) R.drawable.cog_l_norm else R.drawable.cog_l_rot
            val rightCogRes = if (stepCount == 0) R.drawable.cog_r_rot else R.drawable.cog_r_norm

            Image(
                painter = painterResource(id = leftCogRes),
                contentDescription = "Left Tape Spool",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = leftOffsetX, y = leftOffsetY)
                    .size(spinnerSize),
                contentScale = ContentScale.Fit
            )

            Image(
                painter = painterResource(id = rightCogRes),
                contentDescription = "Right Tape Spool",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = rightOffsetX, y = rightOffsetY)
                    .size(spinnerSize),
                contentScale = ContentScale.Fit
            )

            // Superimposed Text on Tape Label (Matching Ruled Write-in Line)
            // Bounding box in 250x144: X=[44, 204], Y=[24, 42]
            val labelLeft = deckWidth * (44f / 250f)
            val labelTop = deckHeight * (24f / 144f)
            val labelWidth = deckWidth * (160f / 250f)
            val labelHeight = deckHeight * (18f / 144f)

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

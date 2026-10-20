package com.retro.pixelplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.retro.pixelplayer.R
import com.retro.pixelplayer.ui.theme.PixelPalette
import kotlinx.coroutines.delay

/**
 * Authentic mint-green retro LCD dot-matrix readout screen with user-provided frame asset.
 * Superimposes track, artist, elapsed/total time, battery and player status directly on the digital display.
 * When cueing/seeking, displays strictly the animated retro spinner centered on the screen.
 */
@Composable
fun LcdDisplay(
    modifier: Modifier = Modifier,
    trackTitle: String,
    artist: String,
    source: String,
    timeText: String,
    statusText: String,
    isCueing: Boolean = false
) {
    var spinnerIndex by remember { mutableStateOf(0) }
    LaunchedEffect(isCueing) {
        if (isCueing) {
            while (true) {
                delay(100L)
                spinnerIndex = (spinnerIndex + 1) % 4
            }
        } else {
            spinnerIndex = 0
        }
    }

    val spinnerChars = listOf("|", "/", "-", "\\")
    val spinnerGlyph = spinnerChars[spinnerIndex]

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(392f / 163f)
    ) {
        val lcdW = maxWidth
        val lcdH = maxHeight
        val padH = lcdW * 0.056f
        val padV = lcdH * 0.135f
        val baseFontSize = (lcdH.value * 0.112f).coerceIn(10.5f, 15.5f).sp
        val titleFontSize = (lcdH.value * 0.120f).coerceIn(11.5f, 16.5f).sp

        // Authentic sliced mint-green LCD screen background
        Image(
            painter = pixelPainterResource(id = R.drawable.lcd_screen_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        if (isCueing) {
            // Sole element displayed on the LCD screen while cueing
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = padH, vertical = padV),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "[ $spinnerGlyph ]",
                    color = PixelPalette.LcdPixels,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = (lcdH.value * 0.35f).coerceIn(24f, 44f).sp
                )
            }
        } else {
            // Superimposed LCD Readout Text
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = padH, vertical = padV),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Line 1: Status & Source
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "STATUS: $statusText",
                        color = PixelPalette.LcdPixels,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = baseFontSize,
                        maxLines = 1
                    )
                    Text(
                        text = "SRC: $source",
                        color = PixelPalette.LcdPixels,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = baseFontSize,
                        maxLines = 1
                    )
                }

                // Line 2: Track title
                Text(
                    text = "TRACK: $trackTitle",
                    color = PixelPalette.LcdPixels,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = titleFontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Line 3: Artist
                Text(
                    text = "ARTIST: $artist",
                    color = PixelPalette.LcdPixels,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = baseFontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Line 4: Time & Battery
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TIME: $timeText",
                        color = PixelPalette.LcdPixels,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = baseFontSize,
                        maxLines = 1
                    )
                    Text(
                        text = "BATT: 98%",
                        color = PixelPalette.LcdPixels,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = baseFontSize,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Preview(name = "LCD Display Preview", widthDp = 392, heightDp = 163)
@Composable
private fun LcdDisplayPreview() {
    LcdDisplay(
        trackTitle = "Jetpack Blues, Sunset Hues",
        artist = "Anamanaguchi",
        source = "SPOTIFY",
        timeText = "02:41 / 03:52",
        statusText = "PLAYING",
        isCueing = false
    )
}

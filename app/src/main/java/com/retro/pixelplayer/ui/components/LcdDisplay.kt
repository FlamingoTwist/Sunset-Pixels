package com.retro.pixelplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.pixelplayer.R
import com.retro.pixelplayer.ui.theme.PixelPalette

/**
 * Authentic mint-green retro LCD dot-matrix readout screen with user-provided frame asset.
 * Superimposes track, artist, elapsed/total time, battery and player status directly on the digital display.
 */
@Composable
fun LcdDisplay(
    modifier: Modifier = Modifier,
    trackTitle: String,
    artist: String,
    source: String,
    timeText: String,
    statusText: String
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(936f / 401f)
    ) {
        // Authentic sliced mint-green LCD screen background
        Image(
            painter = painterResource(id = R.drawable.lcd_screen_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // Superimposed LCD Readout Text
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
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
                    fontSize = 10.sp
                )
                Text(
                    text = "SRC: $source",
                    color = PixelPalette.LcdPixels,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }

            // Line 2: Track title
            Text(
                text = "TRACK: $trackTitle",
                color = PixelPalette.LcdPixels,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Line 3: Artist
            Text(
                text = "ARTIST: $artist",
                color = PixelPalette.LcdPixels,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
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
                    fontSize = 10.sp
                )
                Text(
                    text = "BATT: 98%",
                    color = PixelPalette.LcdPixels,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

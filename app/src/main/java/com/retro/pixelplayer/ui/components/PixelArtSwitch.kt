package com.retro.pixelplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pixel art toggle switch that uses normal or horizontally flipped state.
 */
@Composable
fun PixelArtSwitch(
    modifier: Modifier = Modifier,
    isOn: Boolean,
    offResId: Int,
    onResId: Int,
    width: Dp = 48.dp,
    height: Dp = 25.dp,
    onToggle: () -> Unit
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clickable { onToggle() }
    ) {
        Image(
            painter = pixelPainterResource(id = if (isOn) onResId else offResId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

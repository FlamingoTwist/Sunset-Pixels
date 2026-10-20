package com.retro.pixelplayer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource

/**
 * Loads a drawable resource as a [Painter] with [FilterQuality.None] (nearest-neighbor sampling).
 * This completely avoids bilinear filtering/blurring on retro 16-bit pixel-art sprites.
 */
@Composable
fun pixelPainterResource(@DrawableRes id: Int): Painter {
    val context = LocalContext.current
    val imageBitmap = remember(id) {
        ImageBitmap.imageResource(context.resources, id)
    }
    return remember(imageBitmap) {
        BitmapPainter(imageBitmap, filterQuality = FilterQuality.None)
    }
}

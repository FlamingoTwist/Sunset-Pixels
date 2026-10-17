package com.retro.pixelplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * Pixel art button component using authentic sliced pressed & unpressed sprites.
 */
@Composable
fun PixelArtButton(
    modifier: Modifier = Modifier,
    unpressedResId: Int,
    pressedResId: Int,
    isLatched: Boolean = false,
    width: Dp,
    height: Dp,
    onDownPress: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    var isPressedLocally by remember { mutableStateOf(false) }
    val isDown = isPressedLocally || isLatched

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .pointerInput(isLatched) {
                detectTapGestures(
                    onPress = {
                        isPressedLocally = true
                        onDownPress()
                        val released = tryAwaitRelease()
                        isPressedLocally = false
                        if (released) {
                            onClick()
                        }
                    }
                )
            }
    ) {
        Image(
            painter = painterResource(id = if (isDown) pressedResId else unpressedResId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

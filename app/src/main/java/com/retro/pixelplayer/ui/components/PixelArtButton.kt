package com.retro.pixelplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay

/**
 * Pixel art button component using authentic sliced pressed & unpressed sprites.
 * Supports both single-tap actions and continuous press-and-hold scrubbing (for Rewind / Fast-Fwd).
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
    onHoldRepeat: (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    var isPressedLocally by remember { mutableStateOf(false) }
    val isDown = isPressedLocally || isLatched

    val currentOnDownPress by rememberUpdatedState(onDownPress)
    val currentOnHoldRepeat by rememberUpdatedState(onHoldRepeat)
    val currentOnClick by rememberUpdatedState(onClick)

    // If the user holds down the button (e.g. Rewind / Fast Forward), repeatedly fire onHoldRepeat
    LaunchedEffect(isPressedLocally) {
        if (isPressedLocally && currentOnHoldRepeat != null) {
            delay(260L)
            while (isPressedLocally) {
                currentOnHoldRepeat?.invoke()
                delay(160L)
            }
        }
    }

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .pointerInput(isLatched) {
                detectTapGestures(
                    onPress = {
                        val pressStartMs = System.currentTimeMillis()
                        isPressedLocally = true
                        currentOnDownPress()
                        val released = tryAwaitRelease()
                        val heldDurationMs = System.currentTimeMillis() - pressStartMs
                        isPressedLocally = false
                        if (released) {
                            // If hold-repeat already scrubbed, don't double-fire onClick on release
                            if (currentOnHoldRepeat == null || heldDurationMs < 260L) {
                                currentOnClick()
                            }
                        }
                    }
                )
            }
    ) {
        Image(
            painter = pixelPainterResource(id = if (isDown) pressedResId else unpressedResId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}


package com.retro.pixelplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A chunky, mechanical button with instant down-press haptics and 3D pixel bevel travel.
 */
@Composable
fun MechanicalButton(
    modifier: Modifier = Modifier,
    label: String = "",
    icon: String? = null,
    baseColor: Color,
    highlightColor: Color,
    shadowColor: Color,
    labelColor: Color = Color.White,
    textColor: Color = labelColor,
    isLatched: Boolean = false,
    size: Dp = 56.dp,
    width: Dp? = null,
    height: Dp? = null,
    onDownPress: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    var isPressedLocally by remember { mutableStateOf(false) }
    val isDown = isPressedLocally || isLatched
    val currentOnDownPress by androidx.compose.runtime.rememberUpdatedState(onDownPress)
    val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)

    val travelOffset = if (isDown) 4.dp else 0.dp
    val bevelDepth = if (isDown) 2.dp else 6.dp

    val sizeModifier = if (width != null && height != null) {
        Modifier.size(width = width, height = height)
    } else {
        Modifier.size(size)
    }

    Box(
        modifier = modifier
            .then(sizeModifier)
            // Outer bezel / chassis depression
            .background(Color(0xFF221810), RoundedCornerShape(8.dp))
            .padding(2.dp)
            .pointerInput(isLatched) {
                detectTapGestures(
                    onPress = {
                        isPressedLocally = true
                        currentOnDownPress()
                        val released = tryAwaitRelease()
                        isPressedLocally = false
                        if (released) {
                            currentOnClick()
                        }
                    }
                )
            }
    ) {
        // Button Cap with 3D Bevel effect
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = travelOffset)
                .clip(RoundedCornerShape(6.dp))
                .background(baseColor)
                .drawBehind {
                    val canvasWidth = this@drawBehind.size.width
                    val canvasHeight = this@drawBehind.size.height

                    // Pixel Bevel: Top & Left highlights
                    val highlightWidth = 3.dp.toPx()
                    drawLine(
                        color = if (isDown) shadowColor else highlightColor,
                        start = Offset(0f, 0f),
                        end = Offset(canvasWidth, 0f),
                        strokeWidth = highlightWidth
                    )
                    drawLine(
                        color = if (isDown) shadowColor else highlightColor,
                        start = Offset(0f, 0f),
                        end = Offset(0f, canvasHeight),
                        strokeWidth = highlightWidth
                    )

                    // Pixel Bevel: Bottom & Right shadows
                    val shadowWidth = bevelDepth.toPx()
                    drawLine(
                        color = if (isDown) highlightColor else shadowColor,
                        start = Offset(0f, canvasHeight),
                        end = Offset(canvasWidth, canvasHeight),
                        strokeWidth = shadowWidth
                    )
                    drawLine(
                        color = if (isDown) highlightColor else shadowColor,
                        start = Offset(canvasWidth, 0f),
                        end = Offset(canvasWidth, canvasHeight),
                        strokeWidth = shadowWidth
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Text(
                    text = icon,
                    color = textColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (label.isNotEmpty()) {
                Text(
                    text = label,
                    color = textColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

package com.retro.pixelplayer.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.retro.pixelplayer.feedback.MechanicalFeedback
import com.retro.pixelplayer.spotify.SpotifyManager
import com.retro.pixelplayer.ui.theme.PixelPalette

@Composable
fun SpotifyAuthDialog(
    isLoggedIn: Boolean,
    userName: String,
    syncStatus: String,
    isSyncing: Boolean,
    isRemoteConnected: Boolean = false,
    feedback: MechanicalFeedback,
    onLoginClicked: (String) -> Unit,
    onSyncClicked: () -> Unit,
    onConnectRemote: (() -> Unit)? = null,
    onLogoutClicked: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var clientIdInput by remember { mutableStateOf(SpotifyManager.getSavedClientId(context)) }
    var errorHint by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF231C28))
                .border(4.dp, Color(0xFF1DB954), RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🟢 SPOTIFY TAPE DECK LINK",
                        color = Color(0xFF1DB954),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "[X]",
                        color = PixelPalette.ChassisHighlight,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable {
                            feedback.onTick()
                            onDismiss()
                        }
                    )
                }

                // Mint-Green LCD Status Readout
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(PixelPalette.LcdBackground)
                        .border(2.dp, Color(0xFF15332C), RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = if (isLoggedIn) "STATUS: CONNECTED TO SPOTIFY" else "STATUS: NOT LINKED",
                        color = PixelPalette.LcdPixels,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                    if (isLoggedIn) {
                        Text(
                            text = "ACCOUNT: ${userName.uppercase()}",
                            color = PixelPalette.LcdPixels,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = if (isRemoteConnected) "PLAYBACK: APP REMOTE CONNECTED 🟢" else "PLAYBACK: NOT CONNECTED (TAP AUTHORIZE) 🔴",
                            color = if (isRemoteConnected) PixelPalette.LcdPixels else Color(0xFF7A1010),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                    Text(
                        text = syncStatus,
                        color = PixelPalette.LcdPixels,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }

                if (!isLoggedIn) {
                    Text(
                        text = "1. Create an app at developer.spotify.com/dashboard\n" +
                               "2. Add Redirect URI: pixelplayer://spotify-callback\n" +
                               "3. Paste your Client ID below & tap LOG IN:",
                        color = Color.White.copy(alpha = 0.85f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        lineHeight = 13.sp
                    )

                    // Copy Redirect URI pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF16121A))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                            .clickable {
                                feedback.onTick()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Spotify Redirect URI", SpotifyManager.REDIRECT_URI)
                                )
                                Toast.makeText(context, "Copied: ${SpotifyManager.REDIRECT_URI}", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = SpotifyManager.REDIRECT_URI,
                            color = Color(0xFFFFAE52),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                        Text(
                            text = "[COPY URI]",
                            color = Color(0xFF1DB954),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp
                        )
                    }

                    // Client ID Text Field
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "SPOTIFY CLIENT ID:",
                            color = PixelPalette.ButtonLabelAmber,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF120E16))
                                .border(1.5.dp, Color(0xFF1DB954).copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            if (clientIdInput.isEmpty()) {
                                Text(
                                    text = "Paste 32-char Spotify Client ID...",
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                            BasicTextField(
                                value = clientIdInput,
                                onValueChange = {
                                    clientIdInput = it
                                    errorHint = null
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                cursorBrush = SolidColor(Color(0xFF1DB954)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (errorHint != null) {
                        Text(
                            text = errorHint!!,
                            color = Color(0xFFFF5252),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Big Chunky "LOG IN WITH SPOTIFY" Button
                    MechanicalButton(
                        label = "🟢 LOG IN WITH SPOTIFY",
                        baseColor = Color(0xFF1DB954),
                        highlightColor = Color(0xFF1ED760),
                        shadowColor = Color(0xFF117A37),
                        textColor = Color(0xFF0B1F11),
                        width = 260.dp,
                        height = 40.dp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onDownPress = { feedback.onPlayPress() },
                        onClick = {
                            if (clientIdInput.trim().length < 10) {
                                errorHint = "⚠ Please paste your Spotify Client ID above first!"
                            } else {
                                onLoginClicked(clientIdInput.trim())
                            }
                        }
                    )
                } else {
                    // If not yet connected to Spotify App Remote, provide one-click authorization trigger
                    if (!isRemoteConnected) {
                        MechanicalButton(
                            label = "🟢 AUTHORIZE SPOTIFY PLAYBACK",
                            baseColor = Color(0xFF1DB954),
                            highlightColor = Color(0xFF1ED760),
                            shadowColor = Color(0xFF117A37),
                            textColor = Color(0xFF0B1F11),
                            width = 260.dp,
                            height = 38.dp,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            onDownPress = { feedback.onPlayPress() },
                            onClick = { onConnectRemote?.invoke() }
                        )
                    }

                    // Logged In Actions: Sync Tapes & Log Out
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MechanicalButton(
                            label = if (isSyncing) "SYNCING..." else "🔄 SYNC TAPES",
                            baseColor = Color(0xFF1DB954),
                            highlightColor = Color(0xFF1ED760),
                            shadowColor = Color(0xFF117A37),
                            textColor = Color(0xFF0B1F11),
                            width = 140.dp,
                            height = 38.dp,
                            onDownPress = { feedback.onPlayPress() },
                            onClick = { if (!isSyncing) onSyncClicked() }
                        )

                        MechanicalButton(
                            label = "LOG OUT",
                            baseColor = Color(0xFF8B2626),
                            highlightColor = Color(0xFFA83232),
                            shadowColor = Color(0xFF5A1616),
                            textColor = Color.White,
                            width = 96.dp,
                            height = 38.dp,
                            onDownPress = { feedback.onStopPress() },
                            onClick = onLogoutClicked
                        )
                    }

                    // Developer Dashboard Fingerprint details (required by Spotify for App Remote)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF16121A))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SPOTIFY DEVELOPER DASHBOARD SETTINGS:",
                            color = Color(0xFFFFAE52),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    feedback.onTick()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Package Name", "com.retro.pixelplayer"))
                                    Toast.makeText(context, "Copied: com.retro.pixelplayer", Toast.LENGTH_SHORT).show()
                                },
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Pkg: com.retro.pixelplayer",
                                color = Color.White.copy(alpha = 0.8f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp
                            )
                            Text(
                                text = "[COPY PKG]",
                                color = Color(0xFF1DB954),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    feedback.onTick()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("SHA-1 Fingerprint", "AD:67:EE:11:6B:8E:2D:DA:4D:FC:8A:6A:80:7A:74:C0:9B:D3:39:BB"))
                                    Toast.makeText(context, "Copied SHA-1 fingerprint", Toast.LENGTH_SHORT).show()
                                },
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SHA1: AD:67:EE:11:6B...39:BB",
                                color = Color.White.copy(alpha = 0.8f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp
                            )
                            Text(
                                text = "[COPY SHA-1]",
                                color = Color(0xFF1DB954),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

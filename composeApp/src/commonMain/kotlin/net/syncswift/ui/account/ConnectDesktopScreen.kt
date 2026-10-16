package net.syncswift.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.*
import net.syncswift.connection.ConnectionManager
import net.syncswift.connection.PairResult
import net.syncswift.ui.components.rememberQrScanner
import net.syncswift.ui.theme.*

/**
 * Full-page, modern screen for pairing mobile device with Desktop (Windows, macOS, Linux).
 * Supports both QR Code Scanning and direct 6-Digit Code pairing.
 */
@Composable
fun ConnectDesktopScreen(
    copy: Copy,
    onDismiss: () -> Unit
) {
    var desktopCodeInput by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }

    val scanQr = rememberQrScanner(
        onSuccess = { scannedText ->
            val clean = scannedText.trim().replace(" ", "")
            if (clean.length == 6 && clean.all { it.isDigit() }) {
                isConnecting = true
                ConnectionManager.connectViaPairingCode(clean) { res ->
                    isConnecting = false
                    if (res == PairResult.ACCEPTED) {
                        showNativeToast(copy.connectionAccepted)
                        onDismiss()
                    } else {
                        showNativeToast(copy.invalidPairingCode)
                    }
                }
            } else {
                showNativeToast(copy.invalidPairingCode)
            }
        },
        onError = { err ->
            if (err != "Cancelled") {
                showNativeToast(err)
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(InputBg)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    ChevronLeftIcon(Modifier.size(18.dp), color = Cloud)
                }

                Spacer(Modifier.width(14.dp))

                Text(
                    text = copy.connectToDesktop,
                    color = Cloud,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
            }

            // CONTENT
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // HERO GRAPHIC
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Blue.copy(alpha = 0.12f))
                        .border(BorderStroke(1.5.dp, Blue.copy(alpha = 0.35f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(Blue),
                        contentAlignment = Alignment.Center
                    ) {
                        DesktopVectorIcon(Modifier.size(32.dp), color = Color.White)
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Windows & Mac Sync",
                    color = Cloud,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.4).sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = copy.connectToDesktopDesc,
                    color = Mist,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(Modifier.height(28.dp))

                // METHOD 1: QR CODE SCANNER BUTTON
                Button(
                    onClick = { scanQr() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PillButtonBg,
                        contentColor = PillButtonText
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        QrScanVectorIcon(Modifier.size(18.dp), color = PillButtonText)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = copy.scanDesktopQr,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(26.dp))

                // OR DIVIDER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 1.dp,
                        color = InputBorder.copy(alpha = 0.35f)
                    )
                    Text(
                        text = "  ·  ",
                        color = Mist.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 1.dp,
                        color = InputBorder.copy(alpha = 0.35f)
                    )
                }

                Spacer(Modifier.height(26.dp))

                // METHOD 2: 6-DIGIT CODE INPUT
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = copy.enterDesktopCodePrompt,
                            color = Cloud,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(InputBg)
                                    .border(1.dp, InputBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = desktopCodeInput,
                                    onValueChange = {
                                        val digitsOnly = it.filter { ch -> ch.isDigit() || ch == ' ' }
                                        if (digitsOnly.length <= 7) desktopCodeInput = digitsOnly
                                    },
                                    textStyle = TextStyle(
                                        color = Cloud,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { innerTextField ->
                                        if (desktopCodeInput.isEmpty()) {
                                            Text(
                                                text = copy.enter6DigitCodeHint,
                                                color = Mist.copy(alpha = 0.5f),
                                                fontSize = 16.sp,
                                                letterSpacing = 2.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                            }

                            val cleanDigits = desktopCodeInput.trim().replace(" ", "")
                            val isValidCode = cleanDigits.length == 6 && cleanDigits.all { it.isDigit() }

                            Button(
                                onClick = {
                                    if (isValidCode && !isConnecting) {
                                        isConnecting = true
                                        ConnectionManager.connectViaPairingCode(cleanDigits) { res ->
                                            isConnecting = false
                                            if (res == PairResult.ACCEPTED) {
                                                desktopCodeInput = ""
                                                showNativeToast(copy.connectionAccepted)
                                                onDismiss()
                                            } else {
                                                showNativeToast(copy.invalidPairingCode)
                                            }
                                        }
                                    }
                                },
                                enabled = isValidCode && !isConnecting,
                                modifier = Modifier
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Blue,
                                    contentColor = Color.White,
                                    disabledContainerColor = InputBg,
                                    disabledContentColor = Mist.copy(alpha = 0.5f)
                                )
                            ) {
                                if (isConnecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = copy.connectAction,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                // STEPS CARD
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = InputBg.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StepRow(number = "1", text = "Bilgisayarınızda SyncSwift uygulamasını açın.")
                        StepRow(number = "2", text = "Ekranda gösterilen QR kodu veya 6 haneli kodu açın.")
                        StepRow(number = "3", text = "Yukarıdaki kamera veya kod girişini kullanarak bağlanın.")
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Blue.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = Mist,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

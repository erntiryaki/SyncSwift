package net.syncswift.ui.quickaccess

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.syncswift.*
import net.syncswift.quickaccess.*
import net.syncswift.ui.theme.LocalSyncSwiftColors

@Composable
fun QuickAccessPanel(
    copy: Copy,
    onSelectFileToShare: (() -> Unit)? = null,
    stagedFileName: String? = null,
    stagedFileSize: Long? = null,
    onClearStagedFile: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = LocalSyncSwiftColors.current.isDark
    val cardBg = if (isDark) Color(0xFF131B28) else Color(0xFFFFFFFF)
    val inputBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val borderColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val primaryBlue = LocalSyncSwiftColors.current.blue
    val clipboardManager = LocalClipboardManager.current

    var selectedSubTab by remember { mutableStateOf(0) } // 0: Kod Oluştur (Gönder), 1: Kod Gir (Al)
    var textToShare by remember { mutableStateOf("") }
    var enteredCode by remember { mutableStateOf("") }
    var queryError by remember { mutableStateOf<String?>(null) }
    var isCheckingCode by remember { mutableStateOf(false) }

    val activeSendSession by QuickAccessManager.activeSendSession.collectAsState()
    val incomingSession by QuickAccessManager.incomingSession.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sub-Tab Switcher (Pill style)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (isDark) Color(0xFF0F172A) else Color(0xFFE2E8F0),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 0: Kod Oluştur
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selectedSubTab == 0) cardBg else Color.Transparent)
                        .clickable { selectedSubTab = 0 },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = copy.quickSendTab,
                        fontSize = 14.sp,
                        fontWeight = if (selectedSubTab == 0) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selectedSubTab == 0) primaryBlue else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                    )
                }

                // Tab 1: Kod Gir
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selectedSubTab == 1) cardBg else Color.Transparent)
                        .clickable { selectedSubTab = 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = copy.quickReceiveTab,
                        fontSize = 14.sp,
                        fontWeight = if (selectedSubTab == 1) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selectedSubTab == 1) primaryBlue else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // TAB 0: KOD OLUŞTUR (GÖNDER)
        if (selectedSubTab == 0) {
            if (activeSendSession != null && activeSendSession?.state != QuickSessionState.CANCELLED && activeSendSession?.state != QuickSessionState.EXPIRED) {
                val session = activeSendSession!!
                var remainingSeconds by remember(session.expiresAt) {
                    mutableStateOf(maxOf(0L, (session.expiresAt - currentTimeMs()) / 1000L))
                }
                LaunchedEffect(session.expiresAt) {
                    while (remainingSeconds > 0) {
                        delay(1000L)
                        remainingSeconds = maxOf(0L, (session.expiresAt - currentTimeMs()) / 1000L)
                    }
                }

                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                val timeFormatted = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"

                // Active Session Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = copy.sessionCodeLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )

                        Spacer(Modifier.height(8.dp))

                        // Large formatted code
                        val codeFormatted = "${session.code.take(3)}  ${session.code.drop(3)}"
                        Text(
                            text = codeFormatted,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                            color = primaryBlue
                        )

                        Spacer(Modifier.height(10.dp))

                        // Countdown
                        Surface(
                            shape = CircleShape,
                            color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = copy.sessionCodeExpiresIn.replace("%s", timeFormatted),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (remainingSeconds < 60) Color(0xFFEF4444) else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // File info
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(inputBg)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(primaryBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                DocumentVectorIcon(Modifier.size(20.dp), color = primaryBlue)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.file.fileName.ifBlank { "Metin Paylaşımı" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color.White else Color(0xFF0F172A),
                                    maxLines = 1
                                )
                                if (session.file.fileSize > 0) {
                                    Text(
                                        text = net.syncswift.transfer.formatBytes(session.file.fileSize),
                                        fontSize = 12.sp,
                                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Status indicator
                        Text(
                            text = if (session.state == QuickSessionState.TRANSFERRING) copy.receiverConnected else copy.waitingForReceiver,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (session.state == QuickSessionState.TRANSFERRING) Color(0xFF10B981) else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(18.dp))

                        // Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(session.code))
                                    showNativeToast(copy.codeCopiedToast)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, borderColor)
                            ) {
                                Text(copy.copyCodeAction, fontSize = 13.sp, color = primaryBlue)
                            }

                            Button(
                                onClick = { QuickAccessManager.cancelActiveSession() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                            ) {
                                Text(copy.cancelSession, fontSize = 13.sp, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                // Sender Configuration Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = copy.quickAccessTitle,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF0F172A)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = copy.quickAccessSubtitle,
                            fontSize = 13.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            lineHeight = 18.sp
                        )

                        Spacer(Modifier.height(18.dp))

                        // File picker selector
                        if (!stagedFileName.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(inputBg)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(primaryBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    FolderVectorIcon(Modifier.size(20.dp), color = primaryBlue)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stagedFileName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) Color.White else Color(0xFF0F172A),
                                        maxLines = 1
                                    )
                                    if (stagedFileSize != null && stagedFileSize > 0) {
                                        Text(
                                            text = net.syncswift.transfer.formatBytes(stagedFileSize),
                                            fontSize = 12.sp,
                                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                        )
                                    }
                                }
                                Text(
                                    text = copy.changeFileAction,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryBlue,
                                    modifier = Modifier.clickable { onSelectFileToShare?.invoke() }
                                )
                            }
                        } else {
                            Button(
                                onClick = { onSelectFileToShare?.invoke() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                            ) {
                                Text(copy.selectFilesToShare, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Text input fallback
                        Text(
                            text = copy.selectTextToShare,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                        Spacer(Modifier.height(8.dp))
                        BasicTextField(
                            value = textToShare,
                            onValueChange = { textToShare = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(inputBg)
                                .padding(12.dp),
                            textStyle = TextStyle(
                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(primaryBlue),
                            decorationBox = { innerTextField ->
                                if (textToShare.isEmpty()) {
                                    Text(
                                        text = copy.textToShareHint,
                                        color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Spacer(Modifier.height(20.dp))

                        // Generate session code button
                        val canGenerate = !stagedFileName.isNullOrBlank() || textToShare.isNotBlank()
                        Button(
                            onClick = {
                                val meta = QuickAccessFileMetadata(
                                    fileName = stagedFileName ?: "text_snippet.txt",
                                    fileSize = stagedFileSize ?: textToShare.encodeToByteArray().size.toLong(),
                                    textContent = textToShare.ifBlank { null }
                                )
                                QuickAccessManager.createSession(meta)
                            },
                            enabled = canGenerate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryBlue,
                                disabledContainerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                            )
                        ) {
                            Text(
                                text = copy.generateSessionCode,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (canGenerate) Color.White else (if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8))
                            )
                        }
                    }
                }
            }
        }

        // TAB 1: KOD GİR (ALICI)
        if (selectedSubTab == 1) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = cardBg,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = copy.enter6DigitCode,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF0F172A)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = copy.enterCodePrompt,
                        fontSize = 13.sp,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(20.dp))

                    // 6 Digit Code Input
                    BasicTextField(
                        value = enteredCode,
                        onValueChange = {
                            if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                                enteredCode = it
                                queryError = null
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(62.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(inputBg)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        textStyle = TextStyle(
                            color = primaryBlue,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            letterSpacing = 8.sp
                        ),
                        cursorBrush = SolidColor(primaryBlue),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (enteredCode.isEmpty()) {
                                    Text(
                                        text = "• • •   • • •",
                                        color = if (isDark) Color(0xFF475569) else Color(0xFF94A3B8),
                                        fontSize = 24.sp,
                                        letterSpacing = 4.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    if (queryError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = queryError!!,
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = {
                            isCheckingCode = true
                            queryError = null
                            val found = QuickAccessManager.querySessionByCode(enteredCode)
                            isCheckingCode = false
                            if (found == null) {
                                queryError = copy.invalidOrExpiredCode
                            }
                        },
                        enabled = enteredCode.length == 6 && !isCheckingCode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryBlue,
                            disabledContainerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                        )
                    ) {
                        Text(
                            text = copy.connectAndReceive,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (enteredCode.length == 6) Color.White else (if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8))
                        )
                    }

                    // Found Session Preview Dialog / Card
                    if (incomingSession != null) {
                        Spacer(Modifier.height(20.dp))
                        val session = incomingSession!!

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = copy.incomingQuickTransfer,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color(0xFF0F172A)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = copy.quickTransferFrom.replace("%s", session.senderName),
                                    fontSize = 13.sp,
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                )

                                Spacer(Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(inputBg)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DownloadVectorIcon(Modifier.size(20.dp), color = primaryBlue)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.file.fileName.ifBlank { "Metin" },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDark) Color.White else Color(0xFF0F172A)
                                        )
                                        if (session.file.fileSize > 0) {
                                            Text(
                                                text = net.syncswift.transfer.formatBytes(session.file.fileSize),
                                                fontSize = 11.sp,
                                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }

                                if (session.state == QuickSessionState.TRANSFERRING) {
                                    Spacer(Modifier.height(12.dp))
                                    LinearProgressIndicator(
                                        progress = { session.progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(CircleShape),
                                        color = primaryBlue
                                    )
                                } else if (session.state == QuickSessionState.COMPLETED) {
                                    Spacer(Modifier.height(12.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CheckIcon(Modifier.size(16.dp), color = Color(0xFF10B981))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = copy.transferCompletedTitle,
                                            color = Color(0xFF10B981),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))

                                if (session.state != QuickSessionState.TRANSFERRING && session.state != QuickSessionState.COMPLETED) {
                                    Button(
                                        onClick = {
                                            QuickAccessManager.acceptIncomingSession(
                                                session = session,
                                                onProgress = {},
                                                onComplete = {
                                                    showNativeToast(copy.fileReceivedSuccessfully.replace("%s", session.file.fileName))
                                                }
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                                    ) {
                                        Text(copy.acceptAndDownload, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

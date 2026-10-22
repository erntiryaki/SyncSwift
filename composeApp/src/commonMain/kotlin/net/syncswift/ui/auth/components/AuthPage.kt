package net.syncswift.ui.auth.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.AppLanguage
import net.syncswift.SessionManager
import net.syncswift.ui.components.AuthHeroImage
import net.syncswift.ui.theme.LocalSyncSwiftColors

@Composable
fun ChevronBackIcon(modifier: Modifier = Modifier, color: Color) = Canvas(modifier) {
    val strokeWidth = size.minDimension * 0.16f
    val s = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height
    drawPath(Path().apply {
        moveTo(w * 0.62f, h * 0.22f)
        lineTo(w * 0.35f, h * 0.50f)
        lineTo(w * 0.62f, h * 0.78f)
    }, color, style = s)
}

@Composable
fun ChevronDownIcon(modifier: Modifier = Modifier, color: Color) = Canvas(modifier) {
    val strokeWidth = size.minDimension * 0.18f
    val s = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height
    drawPath(Path().apply {
        moveTo(w * 0.22f, h * 0.36f)
        lineTo(w * 0.50f, h * 0.64f)
        lineTo(w * 0.78f, h * 0.36f)
    }, color, style = s)
}

@Composable
fun AuthPage(
    title: String,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
    detail: String = "",
    subtitleSlot: (@Composable () -> Unit)? = null,
    showHero: Boolean = false,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalSyncSwiftColors.current.isDark
    val isIos = net.syncswift.currentPlatform() == net.syncswift.PlatformFlavor.Ios
    val pageBg = AuthColors.background(isDark)
    net.syncswift.UpdateSystemBars(isDark = isDark, statusBarColor = pageBg)

    var showLanguageSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBg)
    ) {
        if (net.syncswift.isDesktopPlatform()) {
            // ==========================================
            // DESKTOP: DEDICATED QR & 6-DIGIT CODE AUTH
            // ==========================================
            DesktopQrAuthScreen(
                language = language,
                onOpenLanguageSheet = { showLanguageSheet = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // ==========================================
            // MOBILE: SINGLE COLUMN SCROLLABLE LAYOUT
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                // Hero Image starts with balanced breathing room and soft fade gradients
                if (showHero) {
                    Spacer(Modifier.height(if (isIos) 28.dp else 22.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        AuthHeroImage(
                            isDark = isDark,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Hero üstüne solukluk: üst kesik kenarı ve arka plan geçişini tamamen pürüzsüzleştiren yumuşak degrade
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to pageBg,
                                        0.40f to pageBg.copy(alpha = 0.85f),
                                        0.75f to pageBg.copy(alpha = 0.35f),
                                        1.0f to Color.Transparent
                                    )
                                )
                        )

                        // Hero altına solukluk: hero ile form arasında ferah ve pürüzsüz geçiş (alt çizgiyi ve kesiği gizler)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        0.30f to pageBg.copy(alpha = 0.35f),
                                        0.65f to pageBg.copy(alpha = 0.80f),
                                        1.0f to pageBg
                                    )
                                )
                        )
                    }
                    Spacer(Modifier.height(if (isIos) 16.dp else 12.dp))
                } else {
                    // When hero is not shown, provide space below the floating top navigation bar
                    Spacer(Modifier.statusBarsPadding())
                    Spacer(Modifier.height(58.dp))
                }

                // Form Layout Content with horizontal padding
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    // Title Headline
                    Text(
                        text = title,
                        color = AuthColors.textPrimary(isDark),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                        lineHeight = 30.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    // Subtitle / Detail
                    if (subtitleSlot != null) {
                        subtitleSlot()
                    } else if (detail.isNotBlank()) {
                        Text(
                            text = detail,
                            color = AuthColors.textSecondary(isDark),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 19.sp
                        )
                    }

                    Spacer(Modifier.height(22.dp))

                    // Screen Form Content (inputs, primary button, divider, google button, etc.)
                    content()

                    Spacer(Modifier.height(52.dp))
                }
            }
        }

        // Floating Top Navigation Bar (Mobile only, overlaid on top of hero or screen)
        if (!net.syncswift.isDesktopPlatform()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xCC1E293B) else Color(0xEEF8FAFC))
                            .border(
                                BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0xFFE2E8F0)),
                                CircleShape
                            )
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        ChevronBackIcon(Modifier.size(15.dp), color = AuthColors.textPrimary(isDark))
                    }
                } else {
                    Spacer(Modifier.size(38.dp))
                }

                // Language button: "TR ∨"
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) Color(0x99121829) else Color(0xCCFFFFFF))
                        .border(
                            BorderStroke(1.dp, if (isDark) Color(0x22FFFFFF) else Color(0xFFE2E8F0)),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { showLanguageSheet = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = language.code.uppercase(),
                        color = AuthColors.textPrimary(isDark),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    ChevronDownIcon(Modifier.size(10.dp), color = AuthColors.textSecondary(isDark))
                }
            }
        }

        // Language Bottom Sheet
        LanguageBottomSheet(
            visible = showLanguageSheet,
            current = language,
            isDark = isDark,
            title = if (language == AppLanguage.Turkish) "Dil Seçin" else "Select Language",
            onSelect = { selectedLang ->
                setLanguage(selectedLang)
                SessionManager.saveLanguage(selectedLang.code)
            },
            onDismiss = { showLanguageSheet = false }
        )
    }
}

@Composable
private fun DesktopQrAuthScreen(
    language: AppLanguage,
    onOpenLanguageSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Professional Matte Dark Palette (Zero glassmorphism, zero blur, high contrast)
    val bg = Color(0xFF0F1117)
    val cardBg = Color(0xFF161922)
    val cardBorder = Color(0xFF262C38)
    val innerBg = Color(0xFF1E2330)
    val innerBorder = Color(0xFF333B4D)
    val textPrimary = Color(0xFFF8FAFC)
    val textSecondary = Color(0xFFCBD5E1)
    val textMuted = Color(0xFF94A3B8)
    val statusDotGreen = Color(0xFF22C55E)
    val accentPrimary = Color(0xFF38BDF8)

    // Activate pairing code rotation
    LaunchedEffect(Unit) {
        net.syncswift.connection.ConnectionManager.ensurePairingCodeActive()
    }

    val activePairingCode by net.syncswift.connection.ConnectionManager.activePairingCode.collectAsState()
    val codeRemainingSeconds by net.syncswift.connection.ConnectionManager.codeRemainingSeconds.collectAsState()

    val selfDev = net.syncswift.discovery.DeviceDiscoveryManager.getSelfDevice()
    val selfHost = selfDev?.host?.takeIf { it.isNotBlank() && it != "127.0.0.1" } ?: "127.0.0.1"
    val selfPort = selfDev?.port ?: 52424
    val selfName = selfDev?.name ?: SessionManager.getMyDeviceName()
    val selfOs = selfDev?.osType ?: SessionManager.getMyPlatformName()
    val selfId = selfDev?.id ?: SessionManager.getPlatformDeviceId()
    val cleanCode = activePairingCode.replace(" ", "")
    val qrPayload = "syncswift://remote?code=$cleanCode&id=$selfId&host=$selfHost&port=$selfPort&name=$selfName&os=$selfOs"

    val isTr = language == AppLanguage.Turkish
    val titleText = if (isTr) "Masaüstüne Hızlı Bağlantı" else "Connect to Desktop"
    val subtitleText = if (isTr) "Mobil SyncSwift uygulamanızdan QR kodu okutun veya eşleşme kodunu girin." else "Scan the QR code or enter the pairing code from your mobile SyncSwift app."
    val qrScanPrompt = if (isTr) "Kamerayla okutun" else "Scan with mobile camera"
    val orText = if (isTr) "VEYA" else "OR"
    val codeTitle = if (isTr) "6 Haneli Eşleşme Kodu" else "6-Digit Pairing Code"
    val refreshText = if (isTr) "Kod $codeRemainingSeconds saniye sonra yenilenir" else "Code refreshes in $codeRemainingSeconds s"
    val step1 = if (isTr) "1. SyncSwift mobil uygulamasını açın" else "1. Open SyncSwift on your mobile device"
    val step2 = if (isTr) "2. Hesabım > 'Kendi Cihazlarım' bölümüne gidin" else "2. Go to Account > 'My Devices'"
    val step3 = if (isTr) "3. QR kodu okutun veya yukarıdaki 6 haneli kodu yazın" else "3. Scan this QR or enter the 6-digit code above"
    val statusText = if (isTr) "Keşif aktif • Bağlantı bekleniyor" else "Discovery active • Waiting for connection"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP APP BAR - Clean & Solid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                net.syncswift.BrandIcon(
                    modifier = Modifier.size(36.dp),
                    tint = Color.Unspecified
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SyncSwift",
                            color = textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(innerBg)
                                .border(1.dp, innerBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Desktop",
                                color = accentPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Cross-Device Synchronization",
                        color = textMuted,
                        fontSize = 12.sp
                    )
                }
            }

            // Language Selector Button - Matte Solid
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(8.dp))
                    .clickable { onOpenLanguageSheet() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = language.nativeName,
                    color = textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ChevronDownIcon(Modifier.size(10.dp), color = textSecondary)
            }
        }

        // MAIN CENTER CARD - Solid Matte Dark
        Box(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 44.dp, vertical = 34.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = titleText,
                    color = textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitleText,
                    color = textSecondary,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(Modifier.height(34.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT: Solid Crisp QR Box
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(2.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (cleanCode.length == 6) {
                                net.syncswift.ui.components.QrCodeView(
                                    content = qrPayload,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = Color(0xFF0F172A),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusDotGreen)
                            )
                            Text(
                                text = qrScanPrompt,
                                color = textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // MIDDLE: Solid Divider
                    Box(
                        modifier = Modifier
                            .height(210.dp)
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(cardBorder)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(innerBg)
                                .border(1.dp, innerBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = orText,
                                color = textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // RIGHT: Clear High-Contrast Numeric Digits & Instructions
                    Column(
                        modifier = Modifier.weight(1.25f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = codeTitle,
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(14.dp))

                        // 6-digit numeric slots (Solid matte, high contrast)
                        val digits = cleanCode.padEnd(6, '-').take(6)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until 6) {
                                if (i == 3) {
                                    Text(
                                        text = "–",
                                        color = textMuted,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                }
                                val ch = digits.getOrNull(i)?.toString() ?: "-"
                                Box(
                                    modifier = Modifier
                                        .size(width = 44.dp, height = 52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(innerBg)
                                        .border(1.dp, innerBorder, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ch,
                                        color = textPrimary,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Countdown
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(accentPrimary)
                            )
                            Text(
                                text = refreshText,
                                color = textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        // Steps List (Readable silver)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(step1, step2, step3).forEach { step ->
                                Text(
                                    text = step,
                                    color = textSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // FOOTER STATUS BAR - Solid & Clean
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusDotGreen)
                )
                Text(
                    text = "$statusText • $selfName ($selfOs)",
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = "SyncSwift Desktop",
                color = textMuted,
                fontSize = 12.sp
            )
        }
    }
}

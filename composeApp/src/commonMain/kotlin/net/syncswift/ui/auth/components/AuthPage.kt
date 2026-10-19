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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
        // Main Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .imePadding()
                .navigationBarsPadding()
        ) {
            // Hero Image starts seamlessly, with balanced breathing room and soft fade gradients
            if (showHero) {
                Spacer(Modifier.statusBarsPadding())
                Spacer(Modifier.height(if (isIos) 28.dp else 24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    AuthHeroImage(
                        isDark = isDark,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Hero üstüne solukluk: üst kesik kenarı ve arka plan geçişini tamamen pürüzsüzleştiren yumuşak degrade
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    0.0f to pageBg,
                                    0.35f to pageBg.copy(alpha = 0.85f),
                                    0.70f to pageBg.copy(alpha = 0.35f),
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
                Spacer(Modifier.height(if (isIos) 22.dp else 18.dp))
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

        // Floating Top Navigation Bar (Overlaid on top of hero or screen)
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

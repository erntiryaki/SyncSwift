package net.syncswift.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.AppLanguage
import net.syncswift.BrandLogo
import net.syncswift.ChevronDownIcon
import net.syncswift.Copy
import net.syncswift.SessionManager
import net.syncswift.ui.auth.components.AuthColors
import net.syncswift.ui.auth.components.LanguageBottomSheet
import net.syncswift.ui.components.OnboardingHeroImage
import net.syncswift.ui.theme.*

@Composable
fun OnboardingScreen(
    copy: Copy,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit,
    continueToAuth: () -> Unit
) {
    val isDark = LocalSyncSwiftColors.current.isDark
    var page by remember { mutableIntStateOf(0) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var showLanguageSheet by remember { mutableStateOf(false) }

    val titles = listOf(
        copy.onboardingTitle1,
        copy.onboardingTitle2,
        copy.onboardingTitle3
    )
    val descriptions = listOf(
        copy.onboardingDesc1,
        copy.onboardingDesc2,
        copy.onboardingDesc3
    )
    val next: () -> Unit = { if (page < 2) { page++ } else { continueToAuth() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(AuthColors.background(isDark))
            .statusBarsPadding()
            .pointerInput(page) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> dragDistance += amount },
                    onDragEnd = {
                        when {
                            dragDistance < -60f && page < 2 -> page++
                            dragDistance > 60f && page > 0 -> page--
                        }
                        dragDistance = 0f
                    }
                )
            }
    ) {
        // 1. Top Header Bar (Brand Logo on Left, Language Chip & Skip on Right)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandLogo(Modifier.height(48.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Language Chip "TR ∨" (matching AuthPage)
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

                Text(
                    text = copy.skip,
                    color = LocalSyncSwiftColors.current.blue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { continueToAuth() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // 2. Segmented Step Bar (3 distinct pills/segments)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { index ->
                val isActive = index == page
                val isDone = index < page
                val barColor = when {
                    isActive -> LocalSyncSwiftColors.current.blue
                    isDone -> LocalSyncSwiftColors.current.blue.copy(alpha = 0.55f)
                    else -> if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(barColor)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 3. Top Hero Graphic Section (with animated transition between steps & soft gradient dissolves)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1.15f)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(280)) + slideInHorizontally { if (targetState > initialState) it / 3 else -it / 3 })
                        .togetherWith(fadeOut(animationSpec = tween(220)) + slideOutHorizontally { if (targetState > initialState) -it / 3 else it / 3 })
                }
            ) { targetPage ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)),
                    contentAlignment = Alignment.Center
                ) {
                    OnboardingHeroImage(
                        step = targetPage,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top smooth ambient dissolve
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    0.0f to (if (isDark) Color(0xFF0F172A).copy(alpha = 0.65f) else Color(0xFFF8FAFC).copy(alpha = 0.65f)),
                                    1.0f to Color.Transparent
                                )
                            )
                    )

                    // Bottom smooth ambient dissolve
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    1.0f to (if (isDark) Color(0xFF0F172A).copy(alpha = 0.75f) else Color(0xFFF8FAFC).copy(alpha = 0.75f))
                                )
                            )
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 4. Bottom Modern Content Card (Auth-style flat container)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.45f))
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // Subtle sheet notch handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Mist.copy(alpha = 0.45f))
                        )
                    }

                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            fadeIn(tween(260)) togetherWith fadeOut(tween(180))
                        }
                    ) { p ->
                        Column {
                            Text(
                                text = titles[p],
                                color = Cloud,
                                fontSize = 26.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = descriptions[p],
                                color = Mist,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // CTA Action Button (Blue solid pill)
                Button(
                    onClick = next,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalSyncSwiftColors.current.blue,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (page == 2) (if (language == AppLanguage.Turkish) "Hemen Başla" else "Get Started") else copy.continueText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.2).sp
                    )
                }
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
            showLanguageSheet = false
        },
        onDismiss = { showLanguageSheet = false }
    )
}

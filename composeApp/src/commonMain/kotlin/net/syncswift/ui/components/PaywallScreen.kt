package net.syncswift.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.Copy
import net.syncswift.subscription.PaywallReason
import net.syncswift.subscription.SubscriptionManager
import net.syncswift.ui.theme.*

/**
 * Full-page, high-converting 7-Day Trial Paywall Screen.
 * Minimalist dark aesthetic styled with the primary SyncSwift theme tokens:
 * - Ambient cinematic hero visual at top with smooth gradient fade
 * - Top-right "Cancel" button
 * - Centered bold headline: "Design Your Trial"
 * - 5 high-value checkmarked benefit bullets
 * - Flexible 2-box segmented plan selector for all languages (German, Russian, etc.)
 * - Footnote summary
 * - Full-width theme-colored CTA button ("Continue")
 * - Legal footer links (Terms | Privacy | Subscription Terms | Restore)
 */
@Composable
fun PaywallScreen(
    reason: PaywallReason,
    copy: Copy,
    onDismiss: () -> Unit,
    onNavigateToAuth: (() -> Unit)? = null
) {
    var selectedPlan by remember { mutableStateOf(0) } // 0: 7 Days Free Trial, 1: 1 Month
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showLoginRequiredDialog by remember { mutableStateOf(false) }

    val isPro by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()

    val themeColor = Blue
    val primaryButtonBg = PillButtonBg
    val pageBackground = Night

    SyncSwiftTheme(themeMode = AppThemeMode.Dark) {
        net.syncswift.UpdateSystemBars(isDark = true, statusBarColor = Color(0xFF0B101B))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B101B))
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP HERO SECTION WITH FADE
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                PaywallHeroImage(
                    modifier = Modifier.fillMaxSize()
                )

                // Smooth deep dark gradient overlay to fade into dark theme background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.35f to Color.Transparent,
                                0.65f to Color(0xFF0B101B).copy(alpha = 0.65f),
                                0.85f to Color(0xFF0B101B).copy(alpha = 0.95f),
                                1.0f to Color(0xFF0B101B)
                            )
                        )
                )

                // Top "Cancel" Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = copy.cancelAction,
                        color = primaryButtonBg,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                if (!isProcessing) onDismiss()
                            }
                            .padding(8.dp)
                    )
                }
            }

            // BODY CONTENT
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Headline (Flexible line wrap for long languages)
                Text(
                    text = copy.designYourTrial,
                    color = Cloud,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                // 5 Bullet Benefits with Theme Checkmarks
                val benefits = listOf(
                    copy.trialBenefit1,
                    copy.trialBenefit2,
                    copy.trialBenefit3,
                    copy.trialBenefit4,
                    copy.trialBenefit5
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    benefits.forEach { benefit ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✔",
                                color = themeColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 10.dp, top = 1.dp)
                            )
                            Text(
                                text = benefit,
                                color = Cloud.copy(alpha = 0.95f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 20.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                val softGold = Color(0xFFE5B95F)

                // FULL-WIDTH STACKED PLAN SELECTOR (Alt alta, geniş, yıl üstte, ay altta)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. YEARLY PLAN (Önerilen - Üstte)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPlan = 0 },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedPlan == 0) CardSurface else InputBg.copy(alpha = 0.35f),
                        border = BorderStroke(
                            width = if (selectedPlan == 0) 1.5.dp else 1.dp,
                            color = if (selectedPlan == 0) softGold else InputBorder.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f, fill = false),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = copy.planYearly,
                                        color = Cloud,
                                        fontSize = 16.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(
                                                SpanStyle(
                                                    color = softGold,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            ) {
                                                append(copy.trialFreeDaysOffer)
                                            }
                                            withStyle(
                                                SpanStyle(
                                                    color = Mist,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Normal
                                                )
                                            ) {
                                                append(copy.trialThenYearlyPrice)
                                            }
                                        },
                                        lineHeight = 17.sp
                                    )
                                }

                                Spacer(Modifier.width(10.dp))

                                if (selectedPlan == 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(softGold),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "✓",
                                            color = DeepInk,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color.Transparent)
                                            .border(BorderStroke(1.5.dp, Mist.copy(alpha = 0.5f)), CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    // 2. MONTHLY PLAN (Altta)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPlan = 1 },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedPlan == 1) CardSurface else InputBg.copy(alpha = 0.35f),
                        border = BorderStroke(
                            width = if (selectedPlan == 1) 1.5.dp else 1.dp,
                            color = if (selectedPlan == 1) themeColor else InputBorder.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = copy.planMonthly,
                                    color = Cloud,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = copy.trialPricingMonthlySummary,
                                    color = Mist,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(Modifier.width(10.dp))

                            if (selectedPlan == 1) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(themeColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✓",
                                        color = DeepInk,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Transparent)
                                        .border(BorderStroke(1.5.dp, Mist.copy(alpha = 0.5f)), CircleShape)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Footnote Summary Text
                if (selectedPlan == 0) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = softGold,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(copy.trialFreeDaysOffer)
                            }
                            withStyle(
                                SpanStyle(
                                    color = Mist,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            ) {
                                append(copy.trialThenYearlyPrice)
                            }
                        },
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                } else {
                    Text(
                        text = copy.trialPricingMonthlySummary,
                        color = Mist,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = ErrorRed,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(20.dp))

                // PRIMARY CTA BUTTON (Themed Button: PillButtonBg & PillButtonText)
                Button(
                    onClick = {
                        if (isProcessing) return@Button
                        if (!SubscriptionManager.hasActiveAccount()) {
                            showLoginRequiredDialog = true
                            return@Button
                        }
                        isProcessing = true
                        errorMessage = null
                        SubscriptionManager.purchasePro(null, isAnnual = (selectedPlan == 0)) { success, err ->
                            isProcessing = false
                            if (success) {
                                onDismiss()
                            } else {
                                errorMessage = err ?: copy.purchaseFailed
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryButtonBg,
                        contentColor = PillButtonText
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = PillButtonText,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = copy.continueText,
                            color = PillButtonText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                // LEGAL FOOTER LINKS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 28.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${copy.privacyPolicy}  ·  ${copy.subscriptionTerms}  ·  ${copy.restorePurchases}",
                        color = Mist.copy(alpha = 0.7f),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable {
                            isProcessing = true
                            SubscriptionManager.restorePurchases { success, err ->
                                isProcessing = false
                                if (success) {
                                    onDismiss()
                                } else {
                                    errorMessage = err ?: copy.noActiveSubscription
                                }
                            }
                        }
                    )
                }
            }
        }

        // Overlay if user is ALREADY PRO: Frosted layer with active dates
        if (isPro) {
            val dates = remember { net.syncswift.subscription.SubscriptionManager.getProSubscriptionDates() }
            SyncSwiftBottomSheet(
                visible = true,
                onDismissRequest = onDismiss,
                title = copy.alreadyProTitle,
                titleIcon = { net.syncswift.CrownVectorIcon(Modifier.size(24.dp), color = Color(0xFFFBBF24)) },
                confirmText = copy.close,
                confirmColor = PillButtonBg,
                confirmTextColor = PillButtonText,
                onConfirm = onDismiss,
                isDark = true
            ) {
                ProActiveSheetContent(
                    dates = dates,
                    copy = copy
                )
            }
        }

            if (showLoginRequiredDialog) {
                SyncSwiftBottomSheet(
                    visible = showLoginRequiredDialog,
                    onDismissRequest = { showLoginRequiredDialog = false },
                    title = copy.requireLoginForProTitle,
                    confirmText = copy.signInOrRegister,
                    confirmColor = PillButtonBg,
                    confirmTextColor = PillButtonText,
                    onConfirm = {
                        showLoginRequiredDialog = false
                        onDismiss()
                        onNavigateToAuth?.invoke()
                    },
                    dismissText = copy.cancelAction,
                    onDismiss = { showLoginRequiredDialog = false },
                    isDark = true
                ) {
                    Text(
                        text = copy.requireLoginForProDesc,
                        color = Mist,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}




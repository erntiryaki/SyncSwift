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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.Copy
import net.syncswift.subscription.PaywallReason
import net.syncswift.subscription.SubscriptionManager
import net.syncswift.ui.theme.*

/**
 * Full-page, high-converting 7-Day Trial Paywall Screen.
 * Faithful to the minimalist dark reference aesthetic:
 * - Ambient cinematic hero visual at top with smooth gradient fade
 * - Top-right "Cancel" button
 * - Centered bold headline: "Design Your Trial"
 * - 5 high-value checkmarked benefit bullets
 * - 2-box segmented plan selector (7 Days Free vs 1 Month)
 * - Trial footnote summary
 * - Full-width vibrant CTA button ("Continue")
 * - "Remind me before the trial ends" toggle switch
 * - Legal footer links (Terms | Privacy | Subscription Terms | Restore)
 */
@Composable
fun PaywallScreen(
    reason: PaywallReason,
    copy: Copy,
    onDismiss: () -> Unit
) {
    var selectedPlan by remember { mutableStateOf(0) } // 0: 7 Days Free Trial, 1: 1 Month
    var remindMeEnabled by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val accentRed = Color(0xFFEF4444)
    val pageBackground = Color(0xFF0D0F14)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        // Scrollable content
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
                    .height(260.dp)
            ) {
                PaywallHeroImage(
                    modifier = Modifier.fillMaxSize()
                )

                // Smooth gradient overlay to fade into dark background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    pageBackground.copy(alpha = 0.3f),
                                    pageBackground.copy(alpha = 0.85f),
                                    pageBackground
                                )
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
                        color = Mist,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
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
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Headline
                Text(
                    text = copy.designYourTrial,
                    color = Cloud,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(22.dp))

                // 5 Bullet Benefits with Red Checkmarks
                val benefits = listOf(
                    copy.trialBenefit1,
                    copy.trialBenefit2,
                    copy.trialBenefit3,
                    copy.trialBenefit4,
                    copy.trialBenefit5
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    benefits.forEach { benefit ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✔",
                                color = accentRed,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(
                                text = benefit,
                                color = Cloud.copy(alpha = 0.95f),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(26.dp))

                // 2-BOX PLAN SELECTOR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option 0: Free / 7 days (Selected)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selectedPlan = 0 },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selectedPlan == 0) CardSurface else InputBg.copy(alpha = 0.35f),
                        border = BorderStroke(
                            width = if (selectedPlan == 0) 1.5.dp else 1.dp,
                            color = if (selectedPlan == 0) accentRed else InputBorder.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = copy.planFreeTitle,
                                    color = Cloud,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = copy.planFreeSubtitle,
                                    color = Mist,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }

                            if (selectedPlan == 0) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(accentRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✓",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color.Transparent)
                                        .border(BorderStroke(1.5.dp, Mist.copy(alpha = 0.5f)), CircleShape)
                                )
                            }
                        }
                    }

                    // Option 1: 1 Month
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selectedPlan = 1 },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selectedPlan == 1) CardSurface else InputBg.copy(alpha = 0.35f),
                        border = BorderStroke(
                            width = if (selectedPlan == 1) 1.5.dp else 1.dp,
                            color = if (selectedPlan == 1) accentRed else InputBorder.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = "1 " + copy.planMonthly.lowercase(),
                                    color = Cloud,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = copy.planMonthlyPrice,
                                    color = Mist,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }

                            if (selectedPlan == 1) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(accentRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✓",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color.Transparent)
                                        .border(BorderStroke(1.5.dp, Mist.copy(alpha = 0.5f)), CircleShape)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Footnote Summary Text
                Text(
                    text = if (selectedPlan == 0) copy.trialPricingYearlySummary else copy.trialPricingMonthlySummary,
                    color = Mist,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                if (errorMessage != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = ErrorRed,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(18.dp))

                // PRIMARY CTA BUTTON ("Continue" / "Devam Et")
                Button(
                    onClick = {
                        if (isProcessing) return@Button
                        isProcessing = true
                        errorMessage = null
                        SubscriptionManager.purchasePro(null) { success, err ->
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
                        containerColor = accentRed,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = copy.continueText,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // REMINDER BEFORE TRIAL ENDS TOGGLE PILL
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = InputBg.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = copy.remindBeforeTrialEnds,
                            color = Cloud,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = remindMeEnabled,
                            onCheckedChange = { remindMeEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentRed,
                                uncheckedThumbColor = Mist,
                                uncheckedTrackColor = InputBg
                            )
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                // LEGAL FOOTER LINKS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${copy.privacyPolicy}  ·  ${copy.subscriptionTerms}  ·  ${copy.restorePurchases}",
                        color = Mist.copy(alpha = 0.65f),
                        fontSize = 11.5.sp,
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
    }
}

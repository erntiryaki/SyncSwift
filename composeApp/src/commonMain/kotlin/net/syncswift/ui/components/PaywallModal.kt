package net.syncswift.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.syncswift.Copy
import net.syncswift.subscription.PaywallReason
import net.syncswift.subscription.getTitle
import net.syncswift.subscription.getDescription
import net.syncswift.subscription.SubscriptionManager
import net.syncswift.ui.theme.*

@Composable
fun PaywallModal(
    reason: PaywallReason,
    copy: Copy,
    onDismiss: () -> Unit
) {
    var selectedPlan by remember { mutableStateOf(1) } // 0: Monthly, 1: Annual
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val primaryColor = Blue
    val successColor = SuccessGreen

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.25f)),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // PRO Pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = primaryColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "SYNCSWIFT PRO",
                            color = primaryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Close icon
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(InputBg.copy(alpha = 0.6f))
                            .clickable { if (!isProcessing) onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = Mist,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Title & Trigger reason
                Text(
                    text = reason.getTitle(copy),
                    color = Cloud,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = reason.getDescription(copy),
                    color = Mist,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(20.dp))

                // Features comparison card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = InputBg.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        FeatureRow(copy.featureUnlimitedDevices, copy.featureUnlimitedDevicesBase, successColor)
                        FeatureRow(copy.featureRemoteTransfer, copy.featureRemoteTransferBase, successColor)
                        FeatureRow(copy.featureBackgroundDelivery, copy.featureBackgroundDeliveryBase, successColor)
                        FeatureRow(copy.featureLiveClipboard, copy.featureLiveClipboardBase, successColor)
                        FeatureRow(copy.featureUnlimitedFileSize, copy.featureUnlimitedFileSizeBase, successColor)
                        FeatureRow(copy.featureAdFree, copy.featureAdFreeBase, successColor)
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Plan options (Annual vs Monthly)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PlanCard(
                        modifier = Modifier.weight(1f),
                        title = copy.planMonthly,
                        price = "2.99 $",
                        subtitle = copy.planMonthlySubtitle,
                        isSelected = selectedPlan == 0,
                        badge = null,
                        primaryColor = primaryColor,
                        onClick = { selectedPlan = 0 }
                    )

                    PlanCard(
                        modifier = Modifier.weight(1f),
                        title = copy.planYearly,
                        price = "19.99 $",
                        subtitle = copy.planYearlySubtitle,
                        isSelected = selectedPlan == 1,
                        badge = copy.planMostPopular,
                        primaryColor = primaryColor,
                        onClick = { selectedPlan = 1 }
                    )
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = ErrorRed,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Purchase CTA Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !isProcessing) {
                            isProcessing = true
                            errorMessage = null
                            SubscriptionManager.purchasePro(null) { success, err ->
                                isProcessing = false
                                if (success) {
                                    SubscriptionManager.dismissPaywall()
                                } else {
                                    errorMessage = err ?: copy.purchaseFailed
                                }
                            }
                        },
                    color = PillButtonBg,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Cloud,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (selectedPlan == 1) copy.startYearlyCta else copy.startMonthlyCta,
                                color = Cloud,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Restore Purchases
                Text(
                    text = copy.restorePurchases,
                    color = Mist,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(enabled = !isProcessing) {
                            isProcessing = true
                            errorMessage = null
                            SubscriptionManager.restorePurchases { success, err ->
                                isProcessing = false
                                if (success) {
                                    SubscriptionManager.dismissPaywall()
                                } else {
                                    errorMessage = err ?: copy.noActiveSubscription
                                }
                            }
                        }
                        .padding(6.dp)
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(title: String, subtitle: String, checkColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "✓",
            color = checkColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Cloud,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Mist.copy(alpha = 0.75f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun PlanCard(
    modifier: Modifier = Modifier,
    title: String,
    price: String,
    subtitle: String,
    isSelected: Boolean,
    badge: String?,
    primaryColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) primaryColor.copy(alpha = 0.12f) else InputBg.copy(alpha = 0.4f),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) primaryColor else InputBorder.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = primaryColor,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = badge,
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            } else {
                Spacer(Modifier.height(16.dp))
            }

            Text(
                text = title,
                color = if (isSelected) Cloud else Mist,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = price,
                color = Cloud,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = subtitle,
                color = Mist,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

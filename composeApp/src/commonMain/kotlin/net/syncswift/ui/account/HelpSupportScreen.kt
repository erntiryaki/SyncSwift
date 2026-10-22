package net.syncswift.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.*
import net.syncswift.ui.theme.*

@Composable
fun HelpSupportScreen(
    copy: Copy,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current

    // Expanded states for FAQs (first one open by default)
    var expandedFaq by remember { mutableStateOf<Int?>(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(16.dp))

        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(InputBg)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                ChevronLeftIcon(Modifier.size(18.dp), color = Cloud)
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = copy.helpSupportMenu,
                color = Cloud,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
        }

        Spacer(Modifier.height(24.dp))

        // Contact Section: Header
        Text(
            text = copy.helpContactTitle,
            color = Cloud,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = copy.helpContactDesc,
            color = Mist,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(14.dp))

        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

        // Support Email Card (Minimalist & Flat)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    try {
                        uriHandler.openUri("mailto:support@syncswift.net")
                    } catch (e: Exception) {
                        clipboardManager.setText(AnnotatedString("support@syncswift.net"))
                        showNativeToast(copy.emailCopiedToast)
                    }
                },
            shape = RoundedCornerShape(16.dp),
            color = CardSurface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Blue.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    EmailIcon(Modifier.size(20.dp), color = Blue)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "support@syncswift.net",
                        color = Cloud,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = copy.helpSupportMenuDesc,
                        color = Mist,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                // Send Email Direct Action Button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(PillButtonBg)
                        .clickable {
                            try {
                                uriHandler.openUri("mailto:support@syncswift.net")
                            } catch (e: Exception) {
                                clipboardManager.setText(AnnotatedString("support@syncswift.net"))
                                showNativeToast(copy.emailCopiedToast)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = copy.sendEmailAction,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(30.dp))

        // FAQ Section Header
        Text(
            text = copy.helpFaqTitle,
            color = Cloud,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
        )

        Spacer(Modifier.height(14.dp))

        // FAQ List (Boxless, Expandable Accordion with Dividers)
        val faqs = remember(copy) {
            listOf(
                copy.faqQ1 to copy.faqA1,
                copy.faqQ2 to copy.faqA2,
                copy.faqQ3 to copy.faqA3,
                copy.faqQ4 to copy.faqA4,
                copy.faqQ5 to copy.faqA5,
                copy.faqQ6 to copy.faqA6
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            faqs.forEachIndexed { index, (question, answer) ->
                val isExpanded = expandedFaq == index

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            expandedFaq = if (isExpanded) null else index
                        }
                        .padding(vertical = 14.dp, horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = question,
                            color = if (isExpanded) Blue else Cloud,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (isExpanded) "−" else "+",
                            color = if (isExpanded) Blue else Mist,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = answer,
                                color = Mist,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                if (index < faqs.lastIndex) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = InputBorder.copy(alpha = 0.25f)
                    )
                }
            }
        }

            Spacer(Modifier.height(24.dp))
        }

        val isProUser by net.syncswift.subscription.SubscriptionManager.isPro.collectAsState()
        if (!isProUser) {
            net.syncswift.ui.components.AdBannerView(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

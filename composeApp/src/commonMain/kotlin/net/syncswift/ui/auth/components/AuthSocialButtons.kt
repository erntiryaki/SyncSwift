package net.syncswift.ui.auth.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.Copy
import net.syncswift.IdentityProvider
import net.syncswift.SocialProviderIcon

@Composable
fun AuthGoogleWideButton(
    text: String,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(AuthColors.secondary(isDark))
            .border(
                BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0xFFE2E8F0)),
                RoundedCornerShape(26.dp)
            )
            .clickable(enabled = enabled && !isLoading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = AuthColors.button,
                strokeWidth = 2.5.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                SocialProviderIcon(
                    provider = IdentityProvider.Google,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )

                Spacer(Modifier.width(12.dp))

                Text(
                    text = text,
                    color = AuthColors.textPrimary(isDark),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AuthAppleWideButton(
    text: String,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(AuthColors.secondary(isDark))
            .border(
                BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0xFFE2E8F0)),
                RoundedCornerShape(26.dp)
            )
            .clickable(enabled = enabled && !isLoading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = AuthColors.button,
                strokeWidth = 2.5.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                SocialProviderIcon(
                    provider = IdentityProvider.Apple,
                    modifier = Modifier.size(20.dp),
                    tint = if (isDark) Color.White else Color(0xFF121829)
                )

                Spacer(Modifier.width(12.dp))

                Text(
                    text = text,
                    color = AuthColors.textPrimary(isDark),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AuthSocialHalfButton(
    provider: IdentityProvider,
    text: String,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(AuthColors.secondary(isDark))
            .border(
                BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0xFFE2E8F0)),
                RoundedCornerShape(27.dp)
            )
            .clickable(enabled = enabled && !isLoading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = AuthColors.button,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                SocialProviderIcon(
                    provider = provider,
                    modifier = Modifier.size(24.dp),
                    tint = if (provider == IdentityProvider.Apple) {
                        if (isDark) Color.White else Color(0xFF121829)
                    } else {
                        Color.Unspecified
                    }
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = text,
                    color = AuthColors.textPrimary(isDark),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun AuthTermsDisclaimer(
    copy: Copy,
    isDark: Boolean,
    onOpenMembership: () -> Unit,
    onOpenUsage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val linkColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val textColor = AuthColors.textSecondary(isDark)

    val annotatedString = buildAnnotatedString {
        append(copy.termsNoticePrefix)

        pushStringAnnotation(tag = "membership", annotation = "membership")
        withStyle(
            SpanStyle(
                color = linkColor,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(copy.termsOfService)
        }
        pop()

        append(copy.termsAnd)

        pushStringAnnotation(tag = "usage", annotation = "usage")
        withStyle(
            SpanStyle(
                color = linkColor,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(copy.privacyPolicy)
        }
        pop()

        if (copy.termsNoticeSuffix.isNotBlank()) {
            append(copy.termsNoticeSuffix)
        }
    }

    androidx.compose.foundation.text.ClickableText(
        text = annotatedString,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        style = androidx.compose.ui.text.TextStyle(
            color = textColor,
            fontSize = 11.5.sp,
            textAlign = TextAlign.Center,
            fontFamily = net.syncswift.platformFontFamily()
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "membership", start = offset, end = offset)
                .firstOrNull()?.let { onOpenMembership() }
            annotatedString.getStringAnnotations(tag = "usage", start = offset, end = offset)
                .firstOrNull()?.let { onOpenUsage() }
        }
    )
}

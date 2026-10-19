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
                modifier = Modifier.size(20.dp),
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
                    modifier = Modifier.size(20.dp),
                    tint = if (provider == IdentityProvider.Apple) {
                        if (isDark) Color.White else Color(0xFF121829)
                    } else {
                        Color.Unspecified
                    }
                )

                Spacer(Modifier.width(10.dp))

                Text(
                    text = text,
                    color = AuthColors.textPrimary(isDark),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
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

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flexible wrapped text for terms
            Text(
                text = buildAnnotatedString {
                    append(copy.termsNoticePrefix)
                },
                color = textColor,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center
            )
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = copy.termsOfService,
                color = linkColor,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onOpenMembership() }
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            )

            Text(
                text = copy.termsAnd,
                color = textColor,
                fontSize = 11.5.sp
            )

            Text(
                text = copy.privacyPolicy,
                color = linkColor,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onOpenUsage() }
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            )

            if (copy.termsNoticeSuffix.isNotBlank()) {
                Text(
                    text = copy.termsNoticeSuffix,
                    color = textColor,
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

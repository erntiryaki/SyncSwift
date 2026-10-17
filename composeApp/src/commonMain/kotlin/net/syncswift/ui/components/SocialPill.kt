package net.syncswift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.IdentityProvider
import net.syncswift.LockIcon
import net.syncswift.SocialProviderIcon
import net.syncswift.platformFontFamily
import net.syncswift.ui.theme.*

import androidx.compose.foundation.BorderStroke

@Composable
fun SocialPill(
    label: String,
    provider: IdentityProvider,
    light: Boolean,
    action: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = action,
        modifier = modifier.height(54.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        shape = RoundedCornerShape(27.dp),
        border = if (light) null else BorderStroke(1.dp, InputBorder.copy(alpha = 0.45f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (light) Cloud else InputBg,
            contentColor = if (light) DeepInk else Cloud
        ),
        elevation = null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            SocialProviderIcon(provider, Modifier.size(24.dp), if (light) DeepInk else Cloud)
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontFamily = platformFontFamily(),
                fontSize = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BiometricCircleButton(
    action: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(InputBg)
            .clickable { action() },
        contentAlignment = Alignment.Center
    ) {
        LockIcon(Modifier.size(22.dp), color = Cloud)
    }
}

package net.syncswift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.CheckIcon
import net.syncswift.Copy
import net.syncswift.ui.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border

@Composable
fun CustomCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (checked) Blue else InputBg)
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = if (checked) Blue else InputBorder.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(7.dp)
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            CheckIcon(Modifier.size(13.dp), color = DeepInk)
        }
    }
}

@Composable
fun OrDivider(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = Color(0xFF334155))
        Text(
            text = label.uppercase(),
            color = Mist,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = Color(0xFF334155))
    }
}

@Composable
fun Dot(active: Boolean) {
    Box(
        modifier = Modifier
            .height(6.dp)
            .width(if (active) 28.dp else 8.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (active) Blue else Color(0xFF334155))
    )
}

@Composable
fun PasswordRequirements(password: String, copy: Copy? = null) {
    val min8 = password.length >= 8
    val upper = password.any { it.isUpperCase() }
    val lower = password.any { it.isLowerCase() }
    val special = password.any { !it.isLetterOrDigit() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RequirementRow(copy?.reqMin8 ?: "Min. 8 characters", min8)
        RequirementRow(copy?.reqUpper ?: "At least 1 uppercase letter (A-Z)", upper)
        RequirementRow(copy?.reqLower ?: "At least 1 lowercase letter (a-z)", lower)
        RequirementRow(copy?.reqSpecial ?: "At least 1 special character (!@#$)", special)
    }
}

@Composable
private fun RequirementRow(label: String, fulfilled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (fulfilled) SuccessGreen else Color(0xFF334155))
                .border(
                    BorderStroke(
                        width = 1.dp,
                        color = if (fulfilled) SuccessGreen else InputBorder.copy(alpha = 0.4f)
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (fulfilled) {
                CheckIcon(Modifier.size(10.dp), color = Color.White)
            }
        }
        Text(
            text = label,
            color = if (fulfilled) SuccessGreen else Mist,
            fontSize = 12.sp,
            fontWeight = if (fulfilled) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

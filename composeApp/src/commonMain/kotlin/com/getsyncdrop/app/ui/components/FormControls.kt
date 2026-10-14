package com.getsyncdrop.app.ui.components

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
import com.getsyncdrop.app.CheckIcon
import com.getsyncdrop.app.ui.theme.*

@Composable
fun CustomCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) Blue else InputBg)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            CheckIcon(Modifier.size(12.dp), color = DeepInk)
        }
    }
}

@Composable
fun OrDivider(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(Modifier.weight(1f), color = InputBg)
        Text(
            text = label,
            color = Mist,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        HorizontalDivider(Modifier.weight(1f), color = InputBg)
    }
}

@Composable
fun Dot(active: Boolean) {
    Box(
        modifier = Modifier
            .height(6.dp)
            .width(if (active) 32.dp else 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) Blue else InputBg)
    )
}

@Composable
fun PasswordRequirements(password: String) {
    val min8 = password.length >= 8
    val upper = password.any { it.isUpperCase() }
    val lower = password.any { it.isLowerCase() }
    val special = password.any { !it.isLetterOrDigit() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        RequirementRow("Min. 8 karakter", min8)
        RequirementRow("En az 1 büyük harf (A-Z)", upper)
        RequirementRow("En az 1 küçük harf (a-z)", lower)
        RequirementRow("En az 1 özel karakter (!@#$)", special)
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
                .size(14.dp)
                .clip(CircleShape)
                .background(if (fulfilled) Color(0xFF10B981) else Color(0xFF334155)),
            contentAlignment = Alignment.Center
        ) {
            if (fulfilled) {
                Text("✓", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text = label,
            color = if (fulfilled) Color(0xFF10B981) else Mist,
            fontSize = 12.sp,
            fontWeight = if (fulfilled) FontWeight.Bold else FontWeight.Medium
        )
    }
}

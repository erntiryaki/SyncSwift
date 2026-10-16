package net.syncswift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.AppLanguage
import androidx.compose.foundation.BorderStroke
import net.syncswift.BrandLogo
import net.syncswift.CheckIcon
import net.syncswift.ui.theme.*

@Composable
fun Header(language: AppLanguage, setLanguage: (AppLanguage) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandLogo(Modifier.height(64.dp))
        LanguageControl(language, setLanguage)
    }
}

@Composable
fun LanguageControl(current: AppLanguage, setLanguage: (AppLanguage) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = CircleShape,
            color = InputBg,
            border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.4f)),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = current.nativeName,
                    color = Cloud,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "▾",
                    color = Mist,
                    fontSize = 10.sp
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = CardSurface,
            border = BorderStroke(1.dp, InputBorder.copy(alpha = 0.5f)),
            shadowElevation = 8.dp,
            modifier = Modifier.width(180.dp).heightIn(max = 380.dp)
        ) {
            AppLanguage.entries.forEach { option ->
                val isSelected = option == current
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.nativeName,
                                color = if (isSelected) Cloud else Mist,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                            if (isSelected) {
                                CheckIcon(Modifier.size(12.dp), color = Blue)
                            }
                        }
                    },
                    modifier = Modifier.background(
                        if (isSelected) InputBg else Color.Transparent
                    ),
                    onClick = {
                        setLanguage(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

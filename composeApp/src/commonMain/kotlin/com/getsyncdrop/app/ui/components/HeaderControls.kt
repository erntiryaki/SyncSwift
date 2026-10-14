package com.getsyncdrop.app.ui.components

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
import com.getsyncdrop.app.AppLanguage
import com.getsyncdrop.app.BrandLogo
import com.getsyncdrop.app.CheckIcon
import com.getsyncdrop.app.ui.theme.*

@Composable
fun Header(language: AppLanguage, setLanguage: (AppLanguage) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandLogo(Modifier.height(42.dp))
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
                Spacer(Modifier.width(4.dp))
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
            shape = RoundedCornerShape(18.dp),
            containerColor = CardSurface,
            shadowElevation = 0.dp,
            modifier = Modifier.width(150.dp)
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
                                color = if (isSelected) Blue else Cloud,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            if (isSelected) {
                                CheckIcon(Modifier.size(12.dp), color = Blue)
                            }
                        }
                    },
                    modifier = Modifier.background(
                        if (isSelected) Blue.copy(alpha = 0.15f) else Color.Transparent
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

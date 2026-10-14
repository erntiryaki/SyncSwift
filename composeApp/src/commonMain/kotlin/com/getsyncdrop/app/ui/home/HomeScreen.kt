package com.getsyncdrop.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getsyncdrop.app.AppLanguage
import com.getsyncdrop.app.Copy
import com.getsyncdrop.app.ui.components.Header
import com.getsyncdrop.app.ui.components.MainPill
import com.getsyncdrop.app.ui.theme.*

@Composable
fun HomeScreen(
    copy: Copy,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Header(language, setLanguage)
        Spacer(Modifier.height(36.dp))
        Text(
            copy.devices,
            color = Cloud,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp
        )
        Text(
            copy.devicesReady,
            color = Mist,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(28.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DeviceCard("MacBook Pro", "Bu cihaz · Online", "⌘")
            DeviceCard("iPhone 16 Pro", "Online", "◌")
            DeviceCard("Galaxy Tab", "Last seen 2m ago", "▯")
        }

        Spacer(Modifier.height(36.dp))
        Text(copy.send, color = Cloud, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        MainPill("↑   ${copy.send}", {})

        Spacer(Modifier.height(28.dp))
        Text(copy.clipboard, color = Cloud, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(copy.clipboardReady, color = Mist, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun DeviceCard(name: String, detail: String, glyph: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Navy),
                contentAlignment = Alignment.Center
            ) {
                Text(glyph, color = Blue, fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Cloud, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = Mist, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Text("›", color = Mist, fontSize = 22.sp)
        }
    }
}

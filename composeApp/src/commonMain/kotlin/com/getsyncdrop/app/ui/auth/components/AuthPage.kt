package com.getsyncdrop.app.ui.auth.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getsyncdrop.app.AppLanguage
import com.getsyncdrop.app.BrandLogo
import com.getsyncdrop.app.HeroSync3DGraphic
import com.getsyncdrop.app.ui.components.LanguageControl
import com.getsyncdrop.app.ui.theme.*

@Composable
fun AuthPage(
    title: String,
    detail: String,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit,
    heroPage: Int = 0,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandLogo(Modifier.height(46.dp))
            LanguageControl(language, setLanguage)
        }

        Spacer(Modifier.height(8.dp))

        // Hero Graphic Header (Only shown on Email screen heroPage == 0)
        if (heroPage == 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(175.dp),
                contentAlignment = Alignment.Center
            ) {
                HeroSync3DGraphic(Modifier.size(175.dp), page = 0)
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(20.dp))
        }

        // Bottom Form Sheet Card (Extends edge-to-edge seamlessly to bottom)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            color = CardSurface
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 26.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = title,
                        color = Cloud,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = detail,
                        color = Mist,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Column(content = content)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SyncDrop - Cross Device Sync",
                        color = Mist,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

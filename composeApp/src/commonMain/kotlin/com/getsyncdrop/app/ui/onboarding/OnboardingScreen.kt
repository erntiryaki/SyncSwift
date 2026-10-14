package com.getsyncdrop.app.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getsyncdrop.app.AppLanguage
import com.getsyncdrop.app.BrandLogo
import com.getsyncdrop.app.Copy
import com.getsyncdrop.app.HeroSync3DGraphic
import com.getsyncdrop.app.ui.components.Dot
import com.getsyncdrop.app.ui.components.LanguageControl
import com.getsyncdrop.app.ui.components.MainPill
import com.getsyncdrop.app.ui.theme.*

@Composable
fun OnboardingScreen(
    copy: Copy,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit,
    continueToAuth: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val titles = listOf(
        copy.onboardingTitle1,
        copy.onboardingTitle2,
        copy.onboardingTitle3
    )
    val descriptions = listOf(
        copy.onboardingDesc1,
        copy.onboardingDesc2,
        copy.onboardingDesc3
    )
    val next: () -> Unit = { if (page < 2) { page++ } else { continueToAuth() } }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .pointerInput(page) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> dragDistance += amount },
                    onDragEnd = {
                        when {
                            dragDistance < -60f && page < 2 -> page++
                            dragDistance > 60f && page > 0 -> page--
                        }
                        dragDistance = 0f
                    }
                )
            }
    ) {
        // Top Header Bar - Logo on Top Left!
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandLogo(Modifier.height(46.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LanguageControl(language, setLanguage)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = copy.skip,
                    color = Blue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { continueToAuth() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Top Hero Section
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1.1f),
            contentAlignment = Alignment.Center
        ) {
            HeroSync3DGraphic(Modifier.size(240.dp), page = page)
        }

        // Bottom Flat Sheet Card Container
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
                    .padding(horizontal = 28.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = titles[page],
                        color = Cloud,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = descriptions[page],
                        color = Mist,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) { Dot(active = it == page) }
                    }
                }

                Column {
                    MainPill(
                        text = if (page == 2) copy.continueText else copy.continueText,
                        action = next
                    )
                }
            }
        }
    }
}

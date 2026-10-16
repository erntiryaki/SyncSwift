package net.syncswift.ui.auth.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.syncswift.AppLanguage
import net.syncswift.BrandLogo
import net.syncswift.HeroSync3DGraphic
import net.syncswift.ui.components.LanguageControl
import net.syncswift.ui.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun ChevronBackIcon(modifier: Modifier = Modifier, color: Color = Cloud) = Canvas(modifier) {
    val strokeWidth = size.minDimension * 0.15f
    val s = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val w = size.width
    val h = size.height
    drawPath(Path().apply {
        moveTo(w * 0.65f, h * 0.2f)
        lineTo(w * 0.35f, h * 0.5f)
        lineTo(w * 0.65f, h * 0.8f)
    }, color, style = s)
}

@Composable
fun AuthPage(
    title: String,
    detail: String,
    language: AppLanguage,
    setLanguage: (AppLanguage) -> Unit,
    heroPage: Int = 0,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(InputBg)
                            .border(
                                BorderStroke(1.dp, InputBorder.copy(alpha = 0.4f)),
                                CircleShape
                            )
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        ChevronBackIcon(Modifier.size(16.dp), color = Cloud)
                    }
                }
                BrandLogo(Modifier.height(64.dp))
            }
            LanguageControl(language, setLanguage)
        }

        // Hero Graphic Header (Only shown on Email screen heroPage == 0)
        if (heroPage == 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                HeroSync3DGraphic(Modifier.size(160.dp), page = 0)
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(12.dp))
        }

        // Bottom Form Sheet Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, BorderHighlight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(scrollState)
            ) {
                Spacer(Modifier.height(16.dp))
                // Subtle sheet notch handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(InputBorder.copy(alpha = 0.5f))
                    )
                }

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
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

package io.github.xavitje.signcoach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.xavitje.signcoach.resources.Res
import io.github.xavitje.signcoach.resources.*

// ─── SignCoach Redesign — typografie ───────────────────────────────────────
// Baloo 2 voor karakter (koppen, knoppen), Plus Jakarta Sans om te lezen
// (lopende tekst, labels). Zie de SignCoach Redesign-artifact.

@Composable
private fun signCoachDisplayFamily(): FontFamily = FontFamily(
    Font(Res.font.baloo2_medium, FontWeight.Medium),
    Font(Res.font.baloo2_semibold, FontWeight.SemiBold),
    Font(Res.font.baloo2_bold, FontWeight.Bold),
    Font(Res.font.baloo2_extrabold, FontWeight.ExtraBold),
)

@Composable
private fun signCoachBodyFamily(): FontFamily = FontFamily(
    Font(Res.font.jakarta_regular, FontWeight.Normal),
    Font(Res.font.jakarta_medium, FontWeight.Medium),
    Font(Res.font.jakarta_semibold, FontWeight.SemiBold),
    Font(Res.font.jakarta_bold, FontWeight.Bold),
    Font(Res.font.jakarta_extrabold, FontWeight.ExtraBold),
)

@Composable
fun signCoachTypography(): Typography {
    val display = signCoachDisplayFamily()
    val body = signCoachBodyFamily()
    return Typography(
        displayLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
        displayMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
        displaySmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
        headlineLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
        titleSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
        bodySmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        labelSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    )
}

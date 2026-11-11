package me.ttdrp.tagwerk.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ttdrp.tagwerk.R

/** Lora steht unter der SIL Open Font License, siehe assets/licenses/Lora-OFL.txt. */
val SerifFamily = FontFamily(Font(R.font.lora_regular))

val CardShape = RoundedCornerShape(20.dp)

// Die Werte des dunklen Schemas sind aus Screenshots der Vorlage gemessen.
private val Dark = darkColorScheme(
    primary = Color(0xFFAF8DFF),
    onPrimary = Color(0xFF1B1B1B),
    primaryContainer = Color(0xFF3B3743),
    onPrimaryContainer = Color(0xFFDCCFFF),
    secondary = Color(0xFF937FC1),
    onSecondary = Color(0xFF1B1B1B),
    secondaryContainer = Color(0xFF1B1B1B),
    onSecondaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF040404),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF363636),
    onSurfaceVariant = Color(0xFF9A9A9A),
    surfaceContainer = Color(0xFF1B1B1B),
    surfaceContainerHigh = Color(0xFF272727),
    surfaceContainerLow = Color(0xFF121212),
    inverseSurface = Color(0xFFFAF5F0),
    inverseOnSurface = Color(0xFF121212),
    outline = Color(0xFF515150),
    outlineVariant = Color(0xFF3E3E3E),
)

// Vom Hellmodus der Vorlage gibt es keine Screenshots: Dieses Schema ist eine eigene Ableitung.
private val Light = lightColorScheme(
    primary = Color(0xFF7A58E0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2DAF5),
    onPrimaryContainer = Color(0xFF7A58E0),
    secondary = Color(0xFF937FC1),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0EAE4),
    onSecondaryContainer = Color(0xFF141414),
    background = Color(0xFFFAF5F0),
    onBackground = Color(0xFF141414),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141414),
    surfaceVariant = Color(0xFFE6DFD8),
    onSurfaceVariant = Color(0xFF6E6A66),
    surfaceContainer = Color(0xFFF0EAE4),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF1B1B1B),
    inverseOnSurface = Color(0xFFFFFFFF),
    outline = Color(0xFFBDB6AF),
    outlineVariant = Color(0xFFE6DFD8),
)

private val Base = Typography()

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = SerifFamily, fontSize = 54.sp, lineHeight = 62.sp),
    headlineLarge = TextStyle(fontFamily = SerifFamily, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = SerifFamily, fontSize = 25.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = SerifFamily, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = SerifFamily, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = Base.titleMedium.copy(fontSize = 15.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    titleSmall = Base.titleSmall.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    bodyLarge = Base.bodyLarge.copy(fontSize = 16.sp, letterSpacing = 0.2.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 14.5.sp, letterSpacing = 0.2.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 13.sp, letterSpacing = 0.2.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelMedium = Base.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp),
    labelSmall = Base.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
)

private val TINTS = listOf(
    Color(0xFFC85E71),
    Color(0xFFCDD8FF),
    Color(0xFFBADCDD),
    Color(0xFFD4DFAB),
    Color(0xFF706DE3),
    Color(0xFFF2CDA0),
)

fun tintColor(index: Int): Color = TINTS[Math.floorMod(index, TINTS.size)]

@Composable
fun TagwerkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        content = content,
    )
}

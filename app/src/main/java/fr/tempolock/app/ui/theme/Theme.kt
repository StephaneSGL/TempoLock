package fr.tempolock.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Night = Color(0xFF0B1220)
val NightRaised = Color(0xFF121D2D)
val NightSoft = Color(0xFF1B2A3F)
val Mint = Color(0xFF73E7BF)
val MintDeep = Color(0xFF0B6B58)
val Sky = Color(0xFFA9C8FF)
val Amber = Color(0xFFFFC17A)
val Coral = Color(0xFFFF7878)
val Ivory = Color(0xFFF4F6F2)
val Ink = Color(0xFF132019)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF002117),
    primaryContainer = Color(0xFF164E43),
    onPrimaryContainer = Color(0xFFB4F7DE),
    secondary = Sky,
    onSecondary = Color(0xFF0D2A50),
    tertiary = Amber,
    background = Night,
    onBackground = Color(0xFFE8EEF7),
    surface = NightRaised,
    onSurface = Color(0xFFE8EEF7),
    surfaceVariant = NightSoft,
    onSurfaceVariant = Color(0xFFB9C5D5),
    error = Coral,
)

private val LightColors = lightColorScheme(
    primary = MintDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F7DE),
    onPrimaryContainer = Color(0xFF063B30),
    secondary = Color(0xFF365F98),
    tertiary = Color(0xFF8C5200),
    background = Ivory,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE3EAE4),
    onSurfaceVariant = Color(0xFF46534C),
    error = Color(0xFFB3261E),
)

private val TempoTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
)

@Composable
fun TempoLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TempoTypography,
        content = content,
    )
}

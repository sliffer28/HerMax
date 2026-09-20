package com.hermes.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Color Palette (HerMax Warm Coral & Terracotta Theme) ────────────

val HerMaxCoral = Color(0xFFF28B82)
val HerMaxCoralDark = Color(0xFFC95244)
val HerMaxCoralLight = Color(0xFFFFB4AB)
val HerMaxDustyRose = Color(0xFFE5989B)
val HerMaxAmber = Color(0xFFE8A87C)

private val DarkColorScheme = darkColorScheme(
    primary = HerMaxCoral,
    onPrimary = Color(0xFF2E120F),
    primaryContainer = Color(0xFF4D2622),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = HerMaxDustyRose,
    onSecondary = Color(0xFF3B1E21),
    secondaryContainer = Color(0xFF533437),
    onSecondaryContainer = Color(0xFFFFD9DC),
    tertiary = HerMaxAmber,
    onTertiary = Color(0xFF442B00),
    tertiaryContainer = Color(0xFF623F00),
    onTertiaryContainer = Color(0xFFFFDDB6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131011),
    onBackground = Color(0xFFF5EEEE),
    surface = Color(0xFF1B1617),
    onSurface = Color(0xFFF0E5E5),
    surfaceVariant = Color(0xFF282123),
    onSurfaceVariant = Color(0xFFD6C3C3),
    outline = Color(0xFF5E494B),
    outlineVariant = Color(0xFF3D3032),
    surfaceContainerLow = Color(0xFF1F1A1B),
    surfaceContainerHigh = Color(0xFF2A2325),
    surfaceContainerHighest = Color(0xFF352D2F)
)

private val LightColorScheme = lightColorScheme(
    primary = HerMaxCoralDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF3F0B08),
    secondary = Color(0xFF8C5156),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9DC),
    onSecondaryContainer = Color(0xFF381016),
    tertiary = Color(0xFF805615),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB6),
    onTertiaryContainer = Color(0xFF2A1800),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFCF8F7),
    onBackground = Color(0xFF211A1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF211A1B),
    surfaceVariant = Color(0xFFF4E6E6),
    onSurfaceVariant = Color(0xFF524344),
    outline = Color(0xFF857374),
    outlineVariant = Color(0xFFD7C2C3),
    surfaceContainerLow = Color(0xFFF9F2F1),
    surfaceContainerHigh = Color(0xFFEFE6E5),
    surfaceContainerHighest = Color(0xFFE7DDDC)
)

// ─── Typography ─────────────────────────────────────────────────────

val HermesTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// ─── Theme ──────────────────────────────────────────────────────────

@Composable
fun HermesAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HermesTypography,
        content = content
    )
}

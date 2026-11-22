package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Immutable
data class SmartPdfExtendedColors(
    val background: Color,
    val cardBackground: Color,
    val secondaryBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val primaryBlue: Color,
    val iconCircleBg: Color,
    val isDark: Boolean
)

val LocalSmartPdfColors = staticCompositionLocalOf {
    SmartPdfExtendedColors(
        background = DarkBackground,
        cardBackground = DarkCardBackground,
        secondaryBackground = DarkSecondaryBackground,
        textPrimary = TextWhite,
        textSecondary = SecondaryTextDark,
        divider = DividerDark,
        primaryBlue = PrimaryBlue,
        iconCircleBg = DarkCardBackground,
        isDark = true
    )
}

private val SmartDarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = TextWhite,
    primaryContainer = DarkSecondaryBackground,
    onPrimaryContainer = TextWhite,
    secondary = PrimaryBlueLight,
    onSecondary = TextWhite,
    secondaryContainer = DarkSecondaryBackground,
    onSecondaryContainer = TextWhite,
    tertiary = FeatureCyan,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = TextWhite,
    surface = DarkCardBackground,
    onSurface = TextWhite,
    surfaceVariant = DarkSecondaryBackground,
    onSurfaceVariant = SecondaryTextDark,
    outline = DividerDark,
    outlineVariant = DarkSecondaryBackground,
    error = ErrorRed,
    onError = TextWhite
)

private val SmartLightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = TextWhite,
    primaryContainer = Color(0xFFE3E7FF),
    onPrimaryContainer = TextPrimaryLight,
    secondary = PrimaryBlueLight,
    onSecondary = TextWhite,
    secondaryContainer = LightSecondaryBackground,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = FeatureCyan,
    onTertiary = TextWhite,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightCardBackground,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSecondaryBackground,
    onSurfaceVariant = SecondaryTextLight,
    outline = DividerLight,
    outlineVariant = LightSecondaryBackground,
    error = ErrorRed,
    onError = TextWhite
)

val SmartPdfShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SmartPdfMakerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SmartDarkColorScheme else SmartLightColorScheme
    val extendedColors = if (darkTheme) {
        SmartPdfExtendedColors(
            background = DarkBackground,
            cardBackground = DarkCardBackground,
            secondaryBackground = DarkSecondaryBackground,
            textPrimary = TextWhite,
            textSecondary = SecondaryTextDark,
            divider = DividerDark,
            primaryBlue = PrimaryBlue,
            iconCircleBg = DarkCardBackground,
            isDark = true
        )
    } else {
        SmartPdfExtendedColors(
            background = LightBackground,
            cardBackground = LightCardBackground,
            secondaryBackground = LightSecondaryBackground,
            textPrimary = TextPrimaryLight,
            textSecondary = SecondaryTextLight,
            divider = DividerLight,
            primaryBlue = PrimaryBlue,
            iconCircleBg = LightCardBackground,
            isDark = false
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalSmartPdfColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SmartPdfTypography,
            shapes = SmartPdfShapes,
            content = content
        )
    }
}

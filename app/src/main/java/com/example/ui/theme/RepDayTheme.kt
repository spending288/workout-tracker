package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.R
import com.example.data.model.ThemeConfig

val PlusJakartaSansFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold)
)

val DmSansFamily = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(R.font.dm_sans_bold, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

val MonoTabularStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontFeatureSettings = "tnum"
)

val RepDayTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

val RepDayShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalRepDayColors = staticCompositionLocalOf<RepDayColors> {
    ThemeEngine.deriveColors(ThemeEngine.PRESETS.first().dark)
}

@Composable
fun RepDayTheme(
    themeConfig: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val palette = ThemeEngine.resolveEffectivePalette(themeConfig, systemDark)
    val repColors = ThemeEngine.deriveColors(palette)

    val m3Scheme = if (repColors.isDark) {
        darkColorScheme(
            primary = repColors.accent,
            onPrimary = repColors.accentFg,
            primaryContainer = repColors.accentSoft,
            onPrimaryContainer = repColors.accentSoftFg,
            secondary = repColors.textSecondary,
            onSecondary = repColors.canvas,
            secondaryContainer = repColors.surfaceElevated,
            onSecondaryContainer = repColors.text,
            background = repColors.canvas,
            onBackground = repColors.text,
            surface = repColors.surface,
            onSurface = repColors.text,
            surfaceVariant = repColors.surfaceAlt,
            onSurfaceVariant = repColors.textSecondary,
            surfaceContainerLowest = repColors.canvas,
            surfaceContainerLow = repColors.surfaceAlt,
            surfaceContainer = repColors.surface,
            surfaceContainerHigh = repColors.surfaceElevated,
            surfaceContainerHighest = repColors.surfaceElevated,
            outline = repColors.border,
            outlineVariant = repColors.borderStrong,
            error = repColors.danger,
            onError = repColors.canvas,
            errorContainer = repColors.dangerSurface,
            onErrorContainer = repColors.danger
        )
    } else {
        lightColorScheme(
            primary = repColors.accent,
            onPrimary = repColors.accentFg,
            primaryContainer = repColors.accentSoft,
            onPrimaryContainer = repColors.accentSoftFg,
            secondary = repColors.textSecondary,
            onSecondary = repColors.canvas,
            secondaryContainer = repColors.surfaceElevated,
            onSecondaryContainer = repColors.text,
            background = repColors.canvas,
            onBackground = repColors.text,
            surface = repColors.surface,
            onSurface = repColors.text,
            surfaceVariant = repColors.surfaceAlt,
            onSurfaceVariant = repColors.textSecondary,
            surfaceContainerLowest = repColors.canvas,
            surfaceContainerLow = repColors.surfaceAlt,
            surfaceContainer = repColors.surface,
            surfaceContainerHigh = repColors.surfaceElevated,
            surfaceContainerHighest = repColors.surfaceElevated,
            outline = repColors.border,
            outlineVariant = repColors.borderStrong,
            error = repColors.danger,
            onError = repColors.canvas,
            errorContainer = repColors.dangerSurface,
            onErrorContainer = repColors.danger
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !repColors.isDark
                    isAppearanceLightNavigationBars = !repColors.isDark
                }
            }
        }
    }

    CompositionLocalProvider(LocalRepDayColors provides repColors) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = RepDayTypography,
            shapes = RepDayShapes,
            content = content
        )
    }
}

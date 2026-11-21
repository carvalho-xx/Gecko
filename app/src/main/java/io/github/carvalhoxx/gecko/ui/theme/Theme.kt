package io.github.carvalhoxx.gecko.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

val GeckoOledDarkColorScheme: ColorScheme = darkColorScheme(
    primary = GeckoPrimary,
    onPrimary = GeckoOnPrimary,
    primaryContainer = GeckoPrimaryContainer,
    onPrimaryContainer = GeckoOnPrimaryContainer,
    secondary = GeckoSecondary,
    onSecondary = GeckoOnSecondary,
    secondaryContainer = GeckoSecondaryContainer,
    onSecondaryContainer = GeckoOnSecondaryContainer,
    tertiary = GeckoTertiary,
    onTertiary = GeckoOnTertiary,
    tertiaryContainer = GeckoTertiaryContainer,
    onTertiaryContainer = GeckoOnTertiaryContainer,
    background = GeckoBackground,
    onBackground = GeckoTextPrimary,
    surface = GeckoSurface,
    onSurface = GeckoTextPrimary,
    surfaceVariant = GeckoSurfaceVariant,
    onSurfaceVariant = GeckoTextSecondary,
    surfaceTint = GeckoPrimary,
    surfaceContainerLowest = GeckoBackground,
    surfaceContainerLow = GeckoBackgroundElevated,
    surfaceContainer = GeckoSurface,
    surfaceContainerHigh = GeckoSurfaceContainer,
    surfaceContainerHighest = GeckoSurfaceHigh,
    outline = GeckoOutline,
    outlineVariant = GeckoOutlineVariant,
    error = GeckoError,
    onError = GeckoOnError,
    errorContainer = GeckoErrorContainer,
    onErrorContainer = GeckoOnErrorContainer
)

/**
 * Tema Material Design 3 OLED Dark estrito do Gecko (RNF04).
 * Mantém Tonal Elevation sobre superfícies escuras (#0D1210..#242E28) sem dynamic color
 * por padrão para garantir contraste consistente e economia em telas OLED.
 */
@Composable
fun GeckoTheme(darkTheme: Boolean = true, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme && !dynamicColor) {
        GeckoOledDarkColorScheme
    } else {
        GeckoOledDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

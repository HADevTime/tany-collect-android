package ma.tany.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** User appearance preference (Compte › Apparence): Automatique / Clair / Sombre. */
enum class ThemePreference { SYSTEM, LIGHT, DARK }

val LocalTanyColors = staticCompositionLocalOf { LightTanyColors }
val LocalTanyTypography = staticCompositionLocalOf { DefaultTanyTypography }
val LocalTanySpacing = staticCompositionLocalOf { TanySpacing() }
val LocalTanyRadii = staticCompositionLocalOf { TanyRadii() }
val LocalTanyElevation = staticCompositionLocalOf { TanyElevation() }

/**
 * TANY theme. Material 3 is the technical base (components, accessibility, motion); TANY semantic tokens sit
 * above it and are the only colors screens may use. No business logic depends on the appearance.
 */
@Composable
fun TanyTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val colors = if (dark) DarkTanyColors else LightTanyColors
    val material = if (dark) darkColorScheme().tany(colors) else lightColorScheme().tany(colors)
    val typography = DefaultTanyTypography
    CompositionLocalProvider(
        LocalTanyColors provides colors,
        LocalTanyTypography provides typography,
        LocalTanySpacing provides TanySpacing(),
        LocalTanyRadii provides TanyRadii(),
        LocalTanyElevation provides TanyElevation(),
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = Typography(
                headlineLarge = typography.largeTitle,
                headlineMedium = typography.title,
                headlineSmall = typography.title,
                titleLarge = typography.title,
                titleMedium = typography.headline,
                bodyLarge = typography.body,
                bodyMedium = typography.body,
                labelLarge = typography.bodyStrong,
                labelMedium = typography.label,
                bodySmall = typography.caption,
                labelSmall = typography.caption,
            ),
            content = content,
        )
    }
}

/** Maps TANY roles onto the Material scheme so stock M3 components (fields, switches, menus) blend in. */
private fun ColorScheme.tany(colors: TanyColors): ColorScheme = copy(
    primary = colors.primaryAction,
    onPrimary = colors.onPrimaryAction,
    primaryContainer = colors.accentContainer,
    onPrimaryContainer = colors.onAccentContainer,
    secondary = colors.accent,
    onSecondary = colors.onAccent,
    secondaryContainer = colors.accentContainer,
    onSecondaryContainer = colors.onAccentContainer,
    tertiary = colors.accent,
    onTertiary = colors.onAccent,
    background = colors.page,
    onBackground = colors.textPrimary,
    surface = colors.surface,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.neutral,
    onSurfaceVariant = colors.textMuted,
    surfaceTint = colors.surface,
    surfaceBright = colors.surface,
    surfaceDim = colors.page,
    surfaceContainerLowest = colors.surface,
    surfaceContainerLow = colors.surface,
    surfaceContainer = colors.elevated,
    surfaceContainerHigh = colors.elevated,
    surfaceContainerHighest = colors.neutral,
    inverseSurface = colors.chrome,
    inverseOnSurface = colors.onChrome,
    outline = colors.border,
    outlineVariant = colors.divider,
    error = colors.destructive,
    onError = colors.onDestructive,
    errorContainer = colors.danger.container,
    onErrorContainer = colors.danger.content,
    scrim = colors.scrim,
)

/** Accessors: `TanyTheme.colors.accent`, `TanyTheme.typography.body`… */
object TanyTheme {
    val colors: TanyColors
        @Composable @ReadOnlyComposable
        get() = LocalTanyColors.current
    val typography: TanyTypography
        @Composable @ReadOnlyComposable
        get() = LocalTanyTypography.current
    val spacing: TanySpacing
        @Composable @ReadOnlyComposable
        get() = LocalTanySpacing.current
    val radii: TanyRadii
        @Composable @ReadOnlyComposable
        get() = LocalTanyRadii.current
    val elevation: TanyElevation
        @Composable @ReadOnlyComposable
        get() = LocalTanyElevation.current
}

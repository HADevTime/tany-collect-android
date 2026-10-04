package ma.tany.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
    val material = if (dark) {
        darkColorScheme(
            primary = colors.primaryAction,
            onPrimary = colors.onPrimaryAction,
            secondary = colors.accent,
            onSecondary = colors.onAccent,
            tertiary = colors.accent,
            background = colors.page,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.neutral,
            onSurfaceVariant = colors.textMuted,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.elevated,
            surfaceContainerHigh = colors.elevated,
            outline = colors.border,
            outlineVariant = colors.border,
            error = colors.destructive,
            onError = colors.onDestructive,
            scrim = colors.scrim,
        )
    } else {
        lightColorScheme(
            primary = colors.primaryAction,
            onPrimary = colors.onPrimaryAction,
            secondary = colors.accent,
            onSecondary = colors.onAccent,
            tertiary = colors.accent,
            background = colors.page,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.neutral,
            onSurfaceVariant = colors.textMuted,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.elevated,
            surfaceContainerHigh = colors.elevated,
            outline = colors.border,
            outlineVariant = colors.border,
            error = colors.destructive,
            onError = colors.onDestructive,
            scrim = colors.scrim,
        )
    }
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
                headlineMedium = typography.title,
                titleLarge = typography.title,
                titleMedium = typography.headline,
                bodyLarge = typography.body,
                bodyMedium = typography.body,
                labelLarge = typography.bodyStrong,
                labelMedium = typography.label,
                bodySmall = typography.caption,
            ),
            content = content,
        )
    }
}

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

package ma.tany.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic TANY colors. Values come from the canonical web tokens (tany-backend `app/globals.css`:
 * `--tany-*`, dark block `.tany-backoffice`, status scales emerald / amber / red), so Android, iOS and web share one
 * identity. Screens use these roles only — never raw hex values.
 */
@Immutable
data class TanyColors(
    val isDark: Boolean,
    /** Screen background. */
    val page: Color,
    /** Cards, rows. */
    val surface: Color,
    /** Sheets, dialogs, menus. */
    val elevated: Color,
    /** Subtle fills (chips, skeletons). */
    val neutral: Color,
    val warm: Color,
    val input: Color,
    val border: Color,
    val textPrimary: Color,
    val textMuted: Color,
    /** Dark bars in both themes (Collect header, top chrome). */
    val chrome: Color,
    val onChrome: Color,
    /** Main call to action (TANY black; light in dark mode). */
    val primaryAction: Color,
    val onPrimaryAction: Color,
    /** TANY pink — brand accent, focus, selected states. */
    val accent: Color,
    val onAccent: Color,
    val destructive: Color,
    val onDestructive: Color,
    val success: StatusColors,
    val warning: StatusColors,
    val danger: StatusColors,
    val info: StatusColors,
    val neutralStatus: StatusColors,
    /** Product / Asset photos and QR codes: WHITE studio surface in BOTH themes (permanent TANY rule). */
    val media: Color,
    val mediaBorder: Color,
    val scrim: Color,
)

/** Container + content pair for a status tone (≥ 4.5:1 contrast in both themes). */
@Immutable
data class StatusColors(val container: Color, val content: Color, val accent: Color)

internal object TanyPalette {
    val Black = Color(0xFF0B0B0B)
    val White = Color(0xFFFFFFFF)
    val Warm = Color(0xFFFAF8F5)
    val Neutral = Color(0xFFF4F4F2)
    val Border = Color(0xFFE8E8E5)
    val Muted = Color(0xFF717176)
    val Pink = Color(0xFFE5155B)

    val DarkPage = Color(0xFF0E0E10)
    val DarkChrome = Color(0xFF17171A)
    val DarkSurface = Color(0xFF1A1A1D)
    val DarkElevated = Color(0xFF232327)
    val DarkNeutral = Color(0xFF26262B)
    val DarkWarm = Color(0xFF1D1D20)
    val DarkInput = Color(0xFF121215)
    val DarkBorder = Color(0xFF2E2E34)
    val DarkText = Color(0xFFEDEDEF)
    val DarkMuted = Color(0xFFA1A1AA)
}

val LightTanyColors = TanyColors(
    isDark = false,
    page = TanyPalette.Neutral,
    surface = TanyPalette.White,
    elevated = TanyPalette.White,
    neutral = TanyPalette.Neutral,
    warm = TanyPalette.Warm,
    input = TanyPalette.White,
    border = TanyPalette.Border,
    textPrimary = TanyPalette.Black,
    textMuted = TanyPalette.Muted,
    chrome = TanyPalette.Black,
    onChrome = TanyPalette.White,
    primaryAction = TanyPalette.Black,
    onPrimaryAction = TanyPalette.White,
    accent = TanyPalette.Pink,
    onAccent = TanyPalette.White,
    destructive = Color(0xFFDC2626),
    onDestructive = TanyPalette.White,
    success = StatusColors(container = Color(0xFFD1FAE5), content = Color(0xFF064E3B), accent = Color(0xFF047857)),
    warning = StatusColors(container = Color(0xFFFEF3C7), content = Color(0xFF78350F), accent = Color(0xFFB45309)),
    danger = StatusColors(container = Color(0xFFFEE2E2), content = Color(0xFF991B1B), accent = Color(0xFFDC2626)),
    info = StatusColors(container = Color(0xFFE0F2FE), content = Color(0xFF0C4A6E), accent = Color(0xFF0369A1)),
    neutralStatus = StatusColors(container = TanyPalette.Neutral, content = TanyPalette.Black, accent = TanyPalette.Muted),
    media = TanyPalette.White,
    mediaBorder = TanyPalette.Border,
    scrim = Color(0x99000000),
)

val DarkTanyColors = TanyColors(
    isDark = true,
    page = TanyPalette.DarkPage,
    surface = TanyPalette.DarkSurface,
    elevated = TanyPalette.DarkElevated,
    neutral = TanyPalette.DarkNeutral,
    warm = TanyPalette.DarkWarm,
    input = TanyPalette.DarkInput,
    border = TanyPalette.DarkBorder,
    textPrimary = TanyPalette.DarkText,
    textMuted = TanyPalette.DarkMuted,
    chrome = TanyPalette.DarkChrome,
    onChrome = TanyPalette.White,
    primaryAction = TanyPalette.DarkText,
    onPrimaryAction = TanyPalette.Black,
    accent = TanyPalette.Pink,
    onAccent = TanyPalette.White,
    destructive = Color(0xFFF05252),
    onDestructive = TanyPalette.Black,
    success = StatusColors(container = Color(0xFF113125), content = Color(0xFFB0E4CB), accent = Color(0xFF5CC196)),
    warning = StatusColors(container = Color(0xFF36290F), content = Color(0xFFFBD78F), accent = Color(0xFFF6B544)),
    danger = StatusColors(container = Color(0xFF3B1619), content = Color(0xFFFECACA), accent = Color(0xFFF87171)),
    info = StatusColors(container = Color(0xFF0C2230), content = Color(0xFFBAE6FD), accent = Color(0xFF7DD3FC)),
    neutralStatus = StatusColors(container = TanyPalette.DarkNeutral, content = TanyPalette.DarkText, accent = TanyPalette.DarkMuted),
    // Same white studio as light mode: product images are never tinted or recolored.
    media = TanyPalette.White,
    mediaBorder = TanyPalette.DarkBorder,
    scrim = Color(0xB3000000),
)

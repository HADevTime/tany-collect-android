package ma.tany.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic TANY colors. Values come from the canonical web tokens (tany-backend `app/globals.css`:
 * `--tany-*`, dark block `.tany-backoffice`, status scales emerald / amber / red), so Android, iOS and web share one
 * identity. Screens use these roles only — never raw hex values.
 *
 * Premium layering: `page` < `surface` < `elevated` (dark mode gets clearly separated, slightly warm greys instead of
 * near-identical blacks). The TANY pink is an ACCENT: solid only for the brand mark, badges and selected marks; status
 * and selection backgrounds use the soft [accentContainer].
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
    /** Hairline dividers inside cards (lighter than [border]). */
    val divider: Color,
    val textPrimary: Color,
    val textMuted: Color,
    /** Third level text (timestamps, footers) — still ≥ 4.5:1 on [surface]. */
    val textSubtle: Color,
    /** Dark bars in both themes (Collect header, top chrome). */
    val chrome: Color,
    val onChrome: Color,
    /** Secondary text on [chrome]. */
    val onChromeMuted: Color,
    /** Raised tile on [chrome] (pills, icon tiles on the dark header card). */
    val chromeRaised: Color,
    /** Main call to action (TANY black; light in dark mode). */
    val primaryAction: Color,
    val onPrimaryAction: Color,
    /** TANY pink — brand accent, focus, selected states. */
    val accent: Color,
    val onAccent: Color,
    /** Soft pink container (selected nav item, ACTION status, tonal buttons). */
    val accentContainer: Color,
    val onAccentContainer: Color,
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
    /** Skeleton base + highlight (shimmer). */
    val skeleton: Color,
    val skeletonHighlight: Color,
)

/** Container + content pair for a status tone (≥ 4.5:1 contrast in both themes). */
@Immutable
data class StatusColors(val container: Color, val content: Color, val accent: Color)

internal object TanyPalette {
    val Black = Color(0xFF0B0B0B)
    val White = Color(0xFFFFFFFF)
    val Warm = Color(0xFFFAF8F5)
    val Page = Color(0xFFF5F5F3)
    val Neutral = Color(0xFFF0F0EE)
    val Border = Color(0xFFE6E6E3)
    val Divider = Color(0xFFEFEFEC)
    val Muted = Color(0xFF6B6B70)
    val Subtle = Color(0xFF6E6E74)
    val Pink = Color(0xFFE5155B)
    val PinkSoft = Color(0xFFFDE8EF)
    val PinkDeep = Color(0xFF9E0B3E)

    val DarkPage = Color(0xFF0C0C0E)
    val DarkChrome = Color(0xFF151518)
    val DarkChromeRaised = Color(0xFF26262B)
    val DarkSurface = Color(0xFF17171A)
    val DarkElevated = Color(0xFF202024)
    val DarkNeutral = Color(0xFF26262B)
    val DarkWarm = Color(0xFF1C1C1F)
    val DarkInput = Color(0xFF121215)
    val DarkBorder = Color(0xFF2C2C32)
    val DarkDivider = Color(0xFF242429)
    val DarkText = Color(0xFFF2F2F4)
    val DarkMuted = Color(0xFFA8A8B1)
    val DarkSubtle = Color(0xFF94949D)
    val DarkPink = Color(0xFFFF4D85)
    val DarkPinkSoft = Color(0xFF3A1123)
    val DarkPinkText = Color(0xFFFFB8CE)
}

val LightTanyColors = TanyColors(
    isDark = false,
    page = TanyPalette.Page,
    surface = TanyPalette.White,
    elevated = TanyPalette.White,
    neutral = TanyPalette.Neutral,
    warm = TanyPalette.Warm,
    input = TanyPalette.White,
    border = TanyPalette.Border,
    divider = TanyPalette.Divider,
    textPrimary = TanyPalette.Black,
    textMuted = TanyPalette.Muted,
    textSubtle = TanyPalette.Subtle,
    chrome = TanyPalette.Black,
    onChrome = TanyPalette.White,
    onChromeMuted = Color(0xFFB4B4BA),
    chromeRaised = Color(0xFF232327),
    primaryAction = TanyPalette.Black,
    onPrimaryAction = TanyPalette.White,
    accent = TanyPalette.Pink,
    onAccent = TanyPalette.White,
    accentContainer = TanyPalette.PinkSoft,
    onAccentContainer = TanyPalette.PinkDeep,
    destructive = Color(0xFFD42020),
    onDestructive = TanyPalette.White,
    success = StatusColors(container = Color(0xFFDDF6EA), content = Color(0xFF064E3B), accent = Color(0xFF047857)),
    warning = StatusColors(container = Color(0xFFFEF1CC), content = Color(0xFF713F12), accent = Color(0xFFB45309)),
    danger = StatusColors(container = Color(0xFFFDE4E4), content = Color(0xFF991B1B), accent = Color(0xFFDC2626)),
    info = StatusColors(container = Color(0xFFE3F1FC), content = Color(0xFF0C4A6E), accent = Color(0xFF0369A1)),
    neutralStatus = StatusColors(container = TanyPalette.Neutral, content = Color(0xFF2B2B2F), accent = TanyPalette.Muted),
    media = TanyPalette.White,
    mediaBorder = TanyPalette.Border,
    scrim = Color(0x99000000),
    skeleton = Color(0xFFEDEDEA),
    skeletonHighlight = Color(0xFFF8F8F6),
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
    divider = TanyPalette.DarkDivider,
    textPrimary = TanyPalette.DarkText,
    textMuted = TanyPalette.DarkMuted,
    textSubtle = TanyPalette.DarkSubtle,
    chrome = TanyPalette.DarkChrome,
    onChrome = TanyPalette.White,
    onChromeMuted = Color(0xFFA8A8B1),
    chromeRaised = TanyPalette.DarkChromeRaised,
    primaryAction = TanyPalette.DarkText,
    onPrimaryAction = TanyPalette.Black,
    accent = TanyPalette.DarkPink,
    onAccent = TanyPalette.Black,
    accentContainer = TanyPalette.DarkPinkSoft,
    onAccentContainer = TanyPalette.DarkPinkText,
    destructive = Color(0xFFF46B6B),
    onDestructive = TanyPalette.Black,
    success = StatusColors(container = Color(0xFF0F2C21), content = Color(0xFFA7E3C6), accent = Color(0xFF5CC196)),
    warning = StatusColors(container = Color(0xFF33270E), content = Color(0xFFFBD78F), accent = Color(0xFFF6B544)),
    danger = StatusColors(container = Color(0xFF3A1518), content = Color(0xFFFECACA), accent = Color(0xFFF87171)),
    info = StatusColors(container = Color(0xFF0D2231), content = Color(0xFFBAE6FD), accent = Color(0xFF7DD3FC)),
    neutralStatus = StatusColors(container = TanyPalette.DarkNeutral, content = TanyPalette.DarkText, accent = TanyPalette.DarkMuted),
    // Same white studio as light mode: product images are never tinted or recolored.
    media = TanyPalette.White,
    mediaBorder = TanyPalette.DarkBorder,
    scrim = Color(0xB3000000),
    skeleton = Color(0xFF1F1F23),
    skeletonHighlight = Color(0xFF2A2A2F),
)

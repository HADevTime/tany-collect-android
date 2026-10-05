package ma.tany.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * System typography (platform sans-serif, which also covers Arabic script) with a TANY scale.
 * All sizes are in `sp` so they follow the user's font scale. Amounts use tabular figures.
 *
 * Hierarchy: [largeTitle] (tab roots) › [title] (sections, sheets) › [headline] (cards) › [bodyStrong] / [body] ›
 * [label] / [overline] (section headers) › [caption]. Amounts: [amount] › [amountLarge] › [amountHero].
 */
@Immutable
data class TanyTypography(
    val display: TextStyle,
    val largeTitle: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    /** Section headers above grouped cards (no forced uppercase: it means nothing in Arabic). */
    val overline: TextStyle,
    val caption: TextStyle,
    val amount: TextStyle,
    val amountLarge: TextStyle,
    /** The single most important amount of a screen (cash to collect / hand back / settle). */
    val amountHero: TextStyle,
    val code: TextStyle,
    /** Big monospace codes (6-digit fallback cells, settlement short code). */
    val codeLarge: TextStyle,
)

private val Base = TextStyle(fontFamily = FontFamily.Default)

val DefaultTanyTypography = TanyTypography(
    display = Base.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    largeTitle = Base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    title = Base.copy(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    headline = Base.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    body = Base.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodyStrong = Base.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    label = Base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    overline = Base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    caption = Base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    amount = Base.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    amountLarge = Base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
    amountHero = Base.copy(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, fontFeatureSettings = "tnum"),
    code = Base.copy(fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    codeLarge = Base.copy(fontFamily = FontFamily.Monospace, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
)

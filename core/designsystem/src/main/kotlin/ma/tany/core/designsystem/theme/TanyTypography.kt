package ma.tany.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * System typography (platform sans-serif, which also covers Arabic script) with a TANY scale.
 * All sizes are in `sp` so they follow the user's font scale. Amounts use tabular figures.
 */
@Immutable
data class TanyTypography(
    val display: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val amount: TextStyle,
    val amountLarge: TextStyle,
    val code: TextStyle,
)

private val Base = TextStyle(fontFamily = FontFamily.Default)

val DefaultTanyTypography = TanyTypography(
    display = Base.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    title = Base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    headline = Base.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    body = Base.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodyStrong = Base.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    label = Base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    caption = Base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    amount = Base.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    amountLarge = Base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
    code = Base.copy(fontFamily = FontFamily.Monospace, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
)

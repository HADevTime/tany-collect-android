package ma.tany.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class TanySpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** Horizontal screen padding. */
    val screen: Dp = 16.dp,
    /** Vertical gap between screen sections (header › cards › lists). */
    val section: Dp = 24.dp,
)

@Immutable
data class TanyRadii(
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    /** Cards and grouped lists. */
    val card: Dp = 20.dp,
) {
    val small get() = RoundedCornerShape(s)
    val medium get() = RoundedCornerShape(m)
    val large get() = RoundedCornerShape(l)
    val cardShape get() = RoundedCornerShape(card)
    val sheet get() = RoundedCornerShape(topStart = xl, topEnd = xl)
    val pill get() = RoundedCornerShape(percent = 50)
}

/** TANY favors borders over shadows; elevation is reserved for sheets and floating elements. */
@Immutable
data class TanyElevation(val none: Dp = 0.dp, val raised: Dp = 2.dp, val sheet: Dp = 8.dp)

object TanyDimens {
    /** Accessibility: minimum touch target (Material / WCAG). */
    val MinTouchTarget: Dp = 48.dp
    val ButtonHeight: Dp = 52.dp
    val BorderWidth: Dp = 1.dp
    /** Hairline used for dividers inside cards. */
    val Hairline: Dp = 0.75.dp
}

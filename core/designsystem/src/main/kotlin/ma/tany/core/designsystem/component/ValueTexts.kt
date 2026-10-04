package ma.tany.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import ma.tany.core.designsystem.format.TanyFormatters
import ma.tany.core.designsystem.format.TanyLanguage
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.common.MoneyAmount
import java.time.Instant

/** Formatters for the current app language — provided once at the app root. */
val LocalTanyFormatters = staticCompositionLocalOf { TanyFormatters(TanyLanguage.FR) }

/** Displays a SERVER amount (never recomputed client-side) with tabular figures. */
@Composable
fun MoneyText(
    amount: MoneyAmount,
    modifier: Modifier = Modifier,
    style: TextStyle = TanyTheme.typography.amount,
    color: Color = TanyTheme.colors.textPrimary,
) {
    val formatters = LocalTanyFormatters.current
    val text = remember(amount, formatters) { formatters.money(amount) }
    Text(text = text, modifier = modifier, style = style, color = color, maxLines = 1)
}

/** Business date-time (pickup / return): ALWAYS Africa/Casablanca, never the phone time zone. */
@Composable
fun BusinessDateTimeText(
    instant: Instant,
    modifier: Modifier = Modifier,
    end: Instant? = null,
    style: TextStyle = TanyTheme.typography.body,
    color: Color = TanyTheme.colors.textPrimary,
) {
    val formatters = LocalTanyFormatters.current
    val text = remember(instant, end, formatters) {
        if (end != null) formatters.businessWindow(instant, end) else formatters.businessDayTime(instant)
    }
    Text(text = text, modifier = modifier, style = style, color = color)
}

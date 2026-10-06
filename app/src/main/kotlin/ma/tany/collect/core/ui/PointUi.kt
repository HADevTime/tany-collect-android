package ma.tany.collect.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ma.tany.collect.R
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.model.collect.MerchantPoint
import ma.tany.core.model.common.OpeningStateKind
import ma.tany.core.model.common.Weekday
import java.time.DayOfWeek
import java.time.format.TextStyle

/**
 * Localized opening status of a point, composed from the server's STRUCTURED `openingState` (kind + time + day) —
 * never the French `openingStatus` prose. Falls back to the server boolean `isOpenNow`.
 */
@Composable
fun MerchantPoint.openingLabel(): String {
    val formatters = LocalTanyFormatters.current
    val state = openingState
    val time = state?.time?.let(formatters::clock)
    return when (state?.kind) {
        OpeningStateKind.OPEN_UNTIL -> if (time != null) stringResource(R.string.point_open_until, time) else stringResource(R.string.point_open)
        OpeningStateKind.OPENS_TODAY -> if (time != null) stringResource(R.string.point_opens_today, time) else stringResource(R.string.point_closed)
        OpeningStateKind.OPENS_TOMORROW -> if (time != null) stringResource(R.string.point_opens_tomorrow, time) else stringResource(R.string.point_closed)
        OpeningStateKind.OPENS_ON_DAY -> {
            val day = state?.day?.toDayOfWeek()?.getDisplayName(TextStyle.FULL, formatters.language.locale)
            if (time != null && day != null) stringResource(R.string.point_opens_on_day, day, time) else stringResource(R.string.point_closed)
        }
        OpeningStateKind.CLOSED -> stringResource(R.string.point_closed)
        else -> stringResource(if (isOpenNow) R.string.point_open else R.string.point_closed)
    }
}

fun MerchantPoint.openingTone(): TanyTone = if (isOpenNow) TanyTone.SUCCESS else TanyTone.NEUTRAL

private fun Weekday.toDayOfWeek(): DayOfWeek? = when (this) {
    Weekday.MON -> DayOfWeek.MONDAY
    Weekday.TUE -> DayOfWeek.TUESDAY
    Weekday.WED -> DayOfWeek.WEDNESDAY
    Weekday.THU -> DayOfWeek.THURSDAY
    Weekday.FRI -> DayOfWeek.FRIDAY
    Weekday.SAT -> DayOfWeek.SATURDAY
    Weekday.SUN -> DayOfWeek.SUNDAY
    Weekday.UNKNOWN -> null
}

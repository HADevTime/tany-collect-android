package ma.tany.collect.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ma.tany.collect.R
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.model.common.BusinessTime
import java.time.Instant
import java.time.LocalDate

/** Day of a server instant relative to today, both as Africa/Casablanca business days (display only). */
enum class RelativeDay { YESTERDAY, TODAY, TOMORROW, OTHER }

fun relativeDay(date: LocalDate, today: LocalDate): RelativeDay = when (date) {
    today -> RelativeDay.TODAY
    today.plusDays(1) -> RelativeDay.TOMORROW
    today.minusDays(1) -> RelativeDay.YESTERDAY
    else -> RelativeDay.OTHER
}

/** « Aujourd'hui » / « Demain » / « Hier » / « mar. 7 oct. » for a server instant. */
@Composable
fun dayLabel(instant: Instant, now: Instant = Instant.now()): String {
    val date = BusinessTime.businessDate(instant)
    return when (relativeDay(date, BusinessTime.businessDate(now))) {
        RelativeDay.TODAY -> stringResource(R.string.day_today)
        RelativeDay.TOMORROW -> stringResource(R.string.day_tomorrow)
        RelativeDay.YESTERDAY -> stringResource(R.string.day_yesterday)
        RelativeDay.OTHER -> LocalTanyFormatters.current.businessDay(date)
    }
}

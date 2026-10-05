package ma.tany.collect.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ma.tany.collect.R
import ma.tany.core.designsystem.component.LocalTanyFormatters
import java.time.LocalDate

/**
 * Splits a SERVER-ordered list into runs of consecutive items sharing the same civil day (Africa/Casablanca, computed
 * by the caller). Order is never changed: a day that reappears later starts a new run.
 */
fun <T> groupConsecutiveByDay(items: List<T>, dayOf: (T) -> LocalDate): List<Pair<LocalDate, List<T>>> {
    val groups = mutableListOf<Pair<LocalDate, MutableList<T>>>()
    items.forEach { item ->
        val day = dayOf(item)
        val last = groups.lastOrNull()
        if (last != null && last.first == day) last.second += item else groups += day to mutableListOf(item)
    }
    return groups.map { (day, list) -> day to list.toList() }
}

/** « Aujourd’hui » / « Hier » / « Demain » / « Lundi 5 octobre » relative to the business [today]. */
@Composable
fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.day_today)
    today.minusDays(1) -> stringResource(R.string.day_yesterday)
    today.plusDays(1) -> stringResource(R.string.day_tomorrow)
    else -> LocalTanyFormatters.current.businessLongDay(day)
}

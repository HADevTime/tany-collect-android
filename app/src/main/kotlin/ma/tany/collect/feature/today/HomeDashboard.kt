package ma.tany.collect.feature.today

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.tany.collect.R
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyCardStyle
import ma.tany.core.designsystem.component.TanyIllustration
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.colors
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyDimens
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.Operation
import ma.tany.core.model.collect.OperationKind
import ma.tany.core.model.collect.TodayCounts

/** Test tags of the Home dashboard (Compose tests). */
object HomeTags {
    const val STATS = "home.stats"
    const val HERO = "home.hero"
    const val NO_NEXT = "home.noNext"
}

/** One of the four Home counters (server `counts.*`) and where its tap leads. */
data class HomeStat(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val value: Int,
    /** Tone used when [value] > 0 (zero stays neutral). */
    val tone: TanyTone,
    val target: HomeSection,
)

/** The four counters in the iOS order (2 × 2): À collecter · À retourner / En attente client · En retard. */
fun homeStats(counts: TodayCounts): List<HomeStat> = listOf(
    HomeStat(R.string.count_pickups, DsR.drawable.ic_tany_pickup, counts.toCollect, TanyTone.ACTION, HomeSection.TO_COLLECT),
    HomeStat(R.string.count_returns, DsR.drawable.ic_tany_return, counts.toReturn, TanyTone.NEUTRAL, HomeSection.TO_RETURN),
    HomeStat(R.string.count_awaiting_customer, DsR.drawable.ic_tany_clock, counts.awaitingCustomer, TanyTone.WARNING, HomeSection.AWAITING_CUSTOMER),
    HomeStat(R.string.count_late, DsR.drawable.ic_tany_warning, counts.late, TanyTone.DANGER, HomeSection.NOW),
)

/**
 * 2 × 2 grid of real stat cards (iOS « KPITile »): semantic icon, large number, label, a chevron when the card leads
 * somewhere. Zeros stay quiet; a late return is the only card with a danger outline. Pure presentation of the SERVER
 * counters; a tap only scrolls the Home.
 */
@Composable
fun HomeStatsGrid(counts: TodayCounts, onJump: (HomeSection) -> Unit, modifier: Modifier = Modifier) {
    val stats = homeStats(counts)
    Column(modifier.testTag(HomeTags.STATS), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { stat -> StatCard(stat, Modifier.weight(1f), onClick = { onJump(stat.target) }.takeIf { stat.value > 0 }) }
            }
        }
    }
}

@Composable
private fun StatCard(stat: HomeStat, modifier: Modifier, onClick: (() -> Unit)?) {
    val colors = TanyTheme.colors
    val active = stat.value > 0
    val tone = stat.tone.takeIf { active }
    val iconTint = when {
        !active -> colors.textSubtle
        tone == TanyTone.NEUTRAL -> colors.textPrimary
        tone == TanyTone.ACTION -> colors.accent
        else -> tone!!.colors().accent
    }
    val late = active && stat.tone == TanyTone.DANGER
    val label = stringResource(stat.label)
    val description = stringResource(R.string.home_stat_a11y, label, stat.value)
    Column(
        modifier = modifier
            .clip(TanyTheme.radii.large)
            .background(colors.surface)
            .border(TanyDimens.BorderWidth, if (late) colors.danger.accent else colors.border, TanyTheme.radii.large)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (onClick != null) role = Role.Button
            }
            .heightIn(min = 104.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(stat.icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.weight(1f))
            if (onClick != null) {
                Icon(painterResource(DsR.drawable.ic_tany_chevron), contentDescription = null, tint = colors.textSubtle, modifier = Modifier.size(14.dp))
            }
        }
        Text(
            ltrIsolated(stat.value.toString()),
            style = TanyTheme.typography.display.copy(fontFeatureSettings = "tnum"),
            color = when {
                late -> colors.danger.content
                active -> colors.textPrimary
                else -> colors.textSubtle
            },
            maxLines = 1,
        )
        Text(label, style = TanyTheme.typography.label, color = if (late) colors.danger.content else colors.textMuted)
    }
}

/** What the hero CTA does: open the booking (overview) or open it straight on the guided flow. */
data class HeroAction(@StringRes val label: Int, val startFlow: Boolean)

/**
 * Contextual CTA of the hero, from the SERVER phase: a real start only when the server lets the operation be worked on
 * now (the booking screen re-checks the stage before opening the flow).
 */
fun Operation.heroAction(): HeroAction = when (phase) {
    MerchantPhase.PICKUP_UPCOMING -> HeroAction(R.string.hero_cta_prepare, startFlow = false)
    MerchantPhase.PICKUP_READY -> HeroAction(R.string.stage_start_pickup, startFlow = true)
    MerchantPhase.PICKUP_IN_PROGRESS, MerchantPhase.RETURN_IN_PROGRESS -> HeroAction(R.string.hero_cta_continue, startFlow = true)
    MerchantPhase.RETURN_DUE, MerchantPhase.RETURN_LATE -> HeroAction(R.string.stage_start_return, startFlow = true)
    MerchantPhase.DEPOSIT_TO_REFUND -> HeroAction(R.string.stage_resume_deposit, startFlow = true)
    MerchantPhase.WITH_CUSTOMER -> HeroAction(R.string.hero_cta_view_rental, startFlow = false)
    else -> HeroAction(R.string.hero_cta_open, startFlow = false)
}

/**
 * « Prochaine opération » (iOS NextOperationCard): the strongest surface of the app — dark chrome card in both themes,
 * type badge, the TIME as the dominant value (window below), the full product name, customer · unit code, cash as a
 * quiet line, and a full-width TANY pink CTA whose label follows the server phase.
 */
@Composable
fun NextOperationHero(operation: Operation, onOpen: () -> Unit, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TanyTheme.colors
    val formatters = LocalTanyFormatters.current
    val time = operation.rowTime()
    val late = operation.phase == MerchantPhase.RETURN_LATE
    val action = operation.heroAction()
    val period = operation.effectiveUsagePeriod()
    val kind = stringResource(operation.kind.label())
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(TanyTheme.radii.large)
            .background(colors.chrome)
            .border(TanyDimens.BorderWidth, colors.chromeRaised, TanyTheme.radii.large)
            .testTag(HomeTags.HERO)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.hero_next).uppercase(formatters.language.locale),
                style = TanyTheme.typography.overline,
                color = colors.onChromeMuted,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            // Compact type badge: COLLECTE / RETOUR (danger when the return is late) — text, never colour alone.
            Text(
                kind.uppercase(formatters.language.locale),
                style = TanyTheme.typography.overline,
                color = colors.onAccent,
                modifier = Modifier
                    .clip(TanyTheme.radii.pill)
                    .background(if (late) colors.danger.accent else colors.accent)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    ltrIsolated(formatters.businessTime(time.start)),
                    style = TanyTheme.typography.display.copy(fontSize = 40.sp, lineHeight = 46.sp, fontFeatureSettings = "tnum"),
                    color = colors.onChrome,
                )
                Text(
                    ltrIsolated(operation.reference),
                    style = TanyTheme.typography.body.copy(fontFamily = TanyTheme.typography.code.fontFamily),
                    color = colors.onChromeMuted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            val secondary = when {
                time.end != null -> stringResource(R.string.hero_window, ltrIsolated(formatters.businessTimeRange(time.start, time.end)))
                operation.kind == OperationKind.RETURN && operation.phase.ui().section != OperationSection.AWAITING_CUSTOMER ->
                    stringResource(R.string.hero_return_before)
                else -> null
            }
            secondary?.let { Text(it, style = TanyTheme.typography.label, color = colors.onChromeMuted) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Full product name — wraps, never « Nettoyeur Pro des… ».
            Text(operation.product.name, style = TanyTheme.typography.title, color = colors.onChrome)
            if (period.dayCount > 1) {
                Text(
                    "${pluralStringResource(R.plurals.booking_days, period.dayCount, period.dayCount)} · ${formatters.usagePeriod(period.startDate, period.endDate)}",
                    style = TanyTheme.typography.label,
                    color = colors.onChrome,
                )
            }
            Text(
                listOfNotNull(operation.customer.shortName, operation.assetCode?.let(::ltrIsolated)).joinToString(" · "),
                style = TanyTheme.typography.body,
                color = colors.onChromeMuted,
            )
            if (late) {
                operation.lateMinutes?.takeIf { it > 0 }?.let {
                    Text(stringResource(R.string.row_exception_late_for, durationText(it)), style = TanyTheme.typography.bodyStrong, color = colors.danger.accent)
                }
            }
            // Cash is a quiet line here; it becomes the primary value inside the payment step.
            operation.rowMoney()?.let { money ->
                Text(
                    "${formatters.money(money.amount)} · ${stringResource(money.label).lowercase(formatters.language.locale)}",
                    style = TanyTheme.typography.label,
                    color = colors.onChromeMuted,
                )
            }
        }
        TanyButton(
            stringResource(action.label),
            if (action.startFlow) onStart else onOpen,
            style = TanyButtonStyle.ACCENT,
            icon = DsR.drawable.ic_tany_chevron,
        )
    }
}

/** No next operation: a calm premium state in place of the hero (iOS « Tout est sous contrôle »). */
@Composable
fun NoNextOperationCard(completedToday: Int, modifier: Modifier = Modifier) {
    TanyCard(modifier = modifier.testTag(HomeTags.NO_NEXT), style = TanyCardStyle.OUTLINED, contentPadding = 20.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TanyIllustration(
                if (completedToday > 0) DsR.drawable.ic_tany_check else DsR.drawable.ic_tany_calendar,
                tone = if (completedToday > 0) TanyTone.SUCCESS else TanyTone.NEUTRAL,
                size = 56.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(if (completedToday > 0) R.string.today_all_done_title else R.string.home_no_next_title),
                    style = TanyTheme.typography.headline,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.home_no_next_message), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
            }
        }
    }
}

/**
 * The Scanner header pill: tonal (soft accent container) so that it stays one tap away without competing with the
 * point name nor with the hero's pink CTA; the bar's centre Scanner action stays the filled one.
 */
@Composable
fun ScannerPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TanyTheme.colors
    val label = stringResource(R.string.nav_scan)
    val hint = stringResource(R.string.today_scan_hint)
    Row(
        modifier = modifier
            .heightIn(min = TanyDimens.MinTouchTarget)
            .clip(TanyTheme.radii.pill)
            .background(colors.accentContainer)
            .clickable(role = Role.Button, onClickLabel = hint, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(DsR.drawable.ic_tany_scan), contentDescription = null, tint = colors.onAccentContainer, modifier = Modifier.size(18.dp))
        Text(label, style = TanyTheme.typography.bodyStrong, color = colors.onAccentContainer)
        Spacer(Modifier.width(2.dp))
    }
}

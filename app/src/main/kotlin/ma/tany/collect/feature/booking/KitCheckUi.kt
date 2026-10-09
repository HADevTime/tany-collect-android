package ma.tany.collect.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ma.tany.collect.R
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.ProductImageSurface
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyChipSize
import ma.tany.core.designsystem.component.TanyDivider
import ma.tany.core.designsystem.component.TanySegment
import ma.tany.core.designsystem.component.TanySegmentedControl
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.theme.TanyDimens
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.KitCheckState
import ma.tany.core.model.collect.KitItemType
import ma.tany.core.model.collect.RentalKit
import ma.tany.core.model.collect.RentalKitItem
import ma.tany.core.model.collect.returnItems
import ma.tany.core.network.ApiEndpoint

// Rental kit V1 (tany-backend docs/RENTAL-KIT.md) on TANY Collect: the merchant records what physically goes out and comes
// back — fast (everything is present by default, one tap per difference), never blocking, never an amount. A difference
// at the return becomes TANY's incident; the deposit decision is never the merchant's.

/** Number of return issues (MISSING / DAMAGED) among the handed-over elements — UI summary of the merchant's own input. */
fun RentalKit.returnIssueCount(issues: Map<String, KitCheckState>): Int =
    returnItems.count { issues[it.id] == KitCheckState.MISSING || issues[it.id] == KitCheckState.DAMAGED }

@Composable
private fun RentalKitItem.title(): String =
    if (quantity > 1) stringResource(R.string.kit_item_with_quantity, name, quantity) else name

@Composable
private fun RentalKitItem.caption(): String? = when (type) {
    KitItemType.TRANSPORT_BAG -> stringResource(R.string.kit_type_bag)
    else -> description?.takeIf { it.isNotBlank() }
}

/** Thumbnail on the white studio (`ProductImageSurface`), neutral placeholder when the server has no image. */
@Composable
private fun KitThumbnail(item: RentalKitItem, endpoint: ApiEndpoint) {
    ProductImageSurface(
        url = endpoint.resolveMedia(item.image),
        contentDescription = null,
        modifier = Modifier.width(44.dp),
        shape = TanyTheme.radii.medium,
        padding = 4.dp,
    )
}

@Composable
private fun KitItemText(item: RentalKitItem, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Product data: shown verbatim (served in the app language when TANY stored a translation).
        Text(item.title(), style = TanyTheme.typography.bodyStrong, maxLines = 2, overflow = TextOverflow.Ellipsis)
        item.caption()?.let { Text(it, style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/**
 * « Kit à remettre » (handover step): every element is ticked; one tap notes an element that is NOT handed over (and back).
 * The check goes out with « Confirmer la remise » (`kit.checks`, differences only). Never blocking.
 */
@Composable
internal fun HandoverKitChecklist(kit: RentalKit, missing: Set<String>, endpoint: ApiEndpoint, onToggle: (String) -> Unit) {
    TanyCard(modifier = Modifier.testTag(KitTags.HANDOVER)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.kit_handover_title), style = TanyTheme.typography.headline, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.kit_handover_hint), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
            kit.items.forEachIndexed { index, item ->
                if (index > 0) TanyDivider()
                val handed = item.id !in missing
                val state = stringResource(if (handed) R.string.kit_state_handed_over else R.string.kit_state_not_handed_over)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TanyDimens.MinTouchTarget)
                        .toggleable(value = handed, role = Role.Checkbox, onValueChange = { onToggle(item.id) })
                        .semantics {
                            contentDescription = item.name
                            stateDescription = state
                        }
                        .testTag(KitTags.item(item.id)),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KitThumbnail(item, endpoint)
                    KitItemText(item, Modifier.weight(1f))
                    CheckTile(handed)
                }
            }
            if (missing.isEmpty()) {
                TanyStatusChip(stringResource(R.string.kit_handover_all), TanyTone.SUCCESS, size = TanyChipSize.SMALL)
            } else {
                TanyStatusChip(pluralStringResource(R.plurals.kit_not_handed_over_count, missing.size, missing.size), TanyTone.WARNING, size = TanyChipSize.SMALL)
                Text(stringResource(R.string.kit_handover_missing_note), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
            }
        }
    }
}

/** Square check tile (same look as `TanyCheckRow`): filled + check when ticked, outlined otherwise. */
@Composable
private fun CheckTile(checked: Boolean) {
    val colors = TanyTheme.colors
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(TanyTheme.radii.small)
            .then(if (checked) Modifier.background(colors.primaryAction) else Modifier.border(2.dp, colors.textSubtle, TanyTheme.radii.small)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(painterResource(DsR.drawable.ic_tany_check), contentDescription = null, tint = colors.onPrimaryAction, modifier = Modifier.size(16.dp))
    }
}

/**
 * « Kit rendu » (return statement): « Tout est présent » in one gesture (the default), otherwise Présent / Manquant /
 * Endommagé per element handed over. Elements not handed over at the pickup are listed, never expected. A difference never
 * blocks the reception: TANY instructs it (incident), the merchant never chooses an amount.
 */
@Composable
internal fun ReturnKitCheck(
    kit: RentalKit,
    issues: Map<String, KitCheckState>,
    endpoint: ApiEndpoint,
    onState: (String, KitCheckState) -> Unit,
    onAllPresent: () -> Unit,
) {
    val issueCount = kit.returnIssueCount(issues)
    Column(Modifier.testTag(KitTags.RETURN), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.kit_return_title), style = TanyTheme.typography.headline, modifier = Modifier.semantics { heading() })
        if (issueCount == 0) {
            TanyStatusChip(stringResource(R.string.kit_return_all), TanyTone.SUCCESS, size = TanyChipSize.SMALL)
        } else {
            TanyStatusChip(pluralStringResource(R.plurals.kit_return_to_check, issueCount, issueCount), TanyTone.WARNING, size = TanyChipSize.SMALL)
            TanyButton(
                stringResource(R.string.kit_all_present),
                onAllPresent,
                style = TanyButtonStyle.TONAL,
                icon = DsR.drawable.ic_tany_check,
                compact = true,
                modifier = Modifier.testTag(KitTags.ALL_PRESENT),
            )
        }
        val segments = listOf(
            TanySegment(KitCheckState.PRESENT, stringResource(R.string.kit_state_present)),
            TanySegment(KitCheckState.MISSING, stringResource(R.string.kit_state_missing)),
            TanySegment(KitCheckState.DAMAGED, stringResource(R.string.kit_state_damaged)),
        )
        kit.items.forEachIndexed { index, item ->
            if (index > 0) TanyDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag(KitTags.item(item.id))) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    KitThumbnail(item, endpoint)
                    KitItemText(item, Modifier.weight(1f))
                    if (!item.handedOver) TanyStatusChip(stringResource(R.string.kit_state_not_handed_over), TanyTone.NEUTRAL, size = TanyChipSize.SMALL)
                }
                if (item.handedOver) {
                    TanySegmentedControl(
                        options = segments,
                        selected = issues[item.id] ?: KitCheckState.PRESENT,
                        onSelect = { onState(item.id, it) },
                    )
                }
            }
        }
        if (issueCount > 0) {
            Text(stringResource(R.string.kit_return_issue_note), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
        }
    }
}

/**
 * Booking overview « Kit de location »: the frozen snapshot with the latest recorded state of each element (return, else
 * handover). Read-only.
 */
@Composable
internal fun KitOverviewCard(kit: RentalKit, endpoint: ApiEndpoint) {
    TanyCard(modifier = Modifier.testTag(KitTags.OVERVIEW)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.kit_overview_title), style = TanyTheme.typography.headline, modifier = Modifier.semantics { heading() })
            Text(pluralStringResource(R.plurals.kit_items_count, kit.items.size, kit.items.size), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
            kit.items.forEachIndexed { index, item ->
                if (index > 0) TanyDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KitThumbnail(item, endpoint)
                    KitItemText(item, Modifier.weight(1f))
                    item.overviewState()?.let { (label, tone) -> TanyStatusChip(stringResource(label), tone, size = TanyChipSize.SMALL) }
                }
            }
        }
    }
}

/** Latest recorded state of an element (server values), as a label + tone; null = nothing recorded yet. */
internal fun RentalKitItem.overviewState(): Pair<Int, TanyTone>? = when {
    !handedOver -> R.string.kit_state_not_handed_over to TanyTone.NEUTRAL
    returnState == KitCheckState.MISSING -> R.string.kit_state_missing to TanyTone.WARNING
    returnState == KitCheckState.DAMAGED -> R.string.kit_state_damaged to TanyTone.WARNING
    returnState == KitCheckState.PRESENT -> R.string.kit_state_returned to TanyTone.SUCCESS
    pickupState == KitCheckState.PRESENT -> R.string.kit_state_handed_over to TanyTone.SUCCESS
    else -> null
}

/** Test tags (Robolectric / UI tests). */
object KitTags {
    const val HANDOVER = "kit.handover"
    const val RETURN = "kit.return"
    const val OVERVIEW = "kit.overview"
    const val ALL_PRESENT = "kit.allPresent"

    fun item(id: String) = "kit.item.$id"
}

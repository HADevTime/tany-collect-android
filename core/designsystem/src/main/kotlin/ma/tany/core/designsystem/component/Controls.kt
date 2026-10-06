package ma.tany.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ma.tany.core.designsystem.R
import ma.tany.core.designsystem.theme.TanyDimens
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.common.MoneyAmount

/** One option of a [TanySegmentedControl]. */
data class TanySegment<T>(val value: T, val label: String, @DrawableRes val icon: Int? = null)

/**
 * Segmented single choice (appearance, language, scanner mode): radio-group semantics, ≥ 48 dp segments, selected
 * segment raised on a neutral track (selection also carries a check glyph for TalkBack-free clarity).
 */
@Composable
fun <T> TanySegmentedControl(
    options: List<TanySegment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    onChrome: Boolean = false,
) {
    val colors = TanyTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(TanyTheme.radii.large)
            .background(if (onChrome) colors.chromeRaised else colors.neutral)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            val container = when {
                !isSelected -> null
                onChrome -> colors.onChrome
                else -> colors.surface
            }
            val content = when {
                isSelected && onChrome -> colors.chrome
                isSelected -> colors.textPrimary
                onChrome -> colors.onChromeMuted
                else -> colors.textMuted
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = TanyDimens.MinTouchTarget)
                    .clip(TanyTheme.radii.medium)
                    .then(if (container != null) Modifier.background(container) else Modifier)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option.value) })
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                option.icon?.let { Icon(painterResource(it), contentDescription = null, tint = content, modifier = Modifier.size(18.dp)) }
                Text(
                    option.label,
                    style = if (isSelected) TanyTheme.typography.bodyStrong else TanyTheme.typography.body,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Pill search field with leading glyph and a clear button (identifiers typed LTR are handled by the server search). */
@Composable
fun TanySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = TanyTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = TanyTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_tany_search), contentDescription = null, tint = colors.textMuted) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(painterResource(R.drawable.ic_tany_close), contentDescription = stringResource(R.string.tany_action_clear), tint = colors.textMuted)
                }
            }
        } else {
            null
        },
        singleLine = true,
        textStyle = TanyTheme.typography.body,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = TanyTheme.radii.pill,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedBorderColor = colors.textPrimary,
            unfocusedBorderColor = colors.border,
            cursorColor = colors.accent,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedPlaceholderColor = colors.textSubtle,
            unfocusedPlaceholderColor = colors.textSubtle,
        ),
    )
}

/** Field colors shared by the app's text inputs (scanner fallback, incident description, auth). */
@Composable
fun tanyFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = TanyTheme.colors.input,
    unfocusedContainerColor = TanyTheme.colors.input,
    focusedBorderColor = TanyTheme.colors.textPrimary,
    unfocusedBorderColor = TanyTheme.colors.border,
    focusedLabelColor = TanyTheme.colors.textPrimary,
    unfocusedLabelColor = TanyTheme.colors.textMuted,
    cursorColor = TanyTheme.colors.accent,
    focusedTextColor = TanyTheme.colors.textPrimary,
    unfocusedTextColor = TanyTheme.colors.textPrimary,
)

/**
 * KPI tile (server counts only): big tabular number, label below. [tone] colors the number when it calls for
 * attention (late > 0) — the label always says what it counts.
 */
@Composable
fun TanyMetricTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tone: TanyTone? = null,
    @DrawableRes icon: Int? = null,
    onChrome: Boolean = false,
    /** A zero / inactive counter: text and glyph are muted (the label still says what it counts). */
    muted: Boolean = false,
    /** Tile is a shortcut (e.g. jump to its section): button semantics, ≥ 48 dp. */
    onClick: (() -> Unit)? = null,
) {
    val colors = TanyTheme.colors
    val toneColors = tone?.colors()
    val container = when {
        onChrome -> colors.chromeRaised
        toneColors != null -> toneColors.container
        else -> colors.surface
    }
    val valueColor = when {
        muted -> if (onChrome) colors.onChromeMuted else colors.textSubtle
        onChrome && tone != null -> toneColors!!.accent
        onChrome -> colors.onChrome
        toneColors != null -> toneColors.content
        else -> colors.textPrimary
    }
    val labelColor = when {
        onChrome -> colors.onChromeMuted
        toneColors != null -> toneColors.content
        else -> colors.textMuted
    }
    Column(
        modifier = modifier
            .clip(TanyTheme.radii.large)
            .background(container)
            .then(if (!onChrome && toneColors == null) Modifier.border(TanyDimens.BorderWidth, colors.border, TanyTheme.radii.large) else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = TanyDimens.MinTouchTarget)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = TanyTheme.typography.amountLarge.copy(fontSize = TanyTheme.typography.title.fontSize), color = valueColor, maxLines = 1)
            if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = valueColor, modifier = Modifier.size(16.dp))
        }
        Text(label, style = TanyTheme.typography.caption, color = labelColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The key amount of a financial step (« À encaisser », « À rendre au client », « À remettre à TANY »): label, SERVER
 * amount in hero size, optional caption and breakdown rows. Never computed client-side.
 */
@Composable
fun TanyAmountPanel(
    label: String,
    amount: MoneyAmount,
    modifier: Modifier = Modifier,
    tone: TanyTone = TanyTone.NEUTRAL,
    caption: String? = null,
    @DrawableRes icon: Int? = null,
    details: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = TanyTheme.colors
    val c = tone.colors()
    val container = if (tone == TanyTone.NEUTRAL) colors.neutral else c.container
    val content = if (tone == TanyTone.NEUTRAL) colors.textPrimary else c.content
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(TanyTheme.radii.large)
            .background(container)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Text(label, style = TanyTheme.typography.label, color = content)
            }
            MoneyText(amount, style = TanyTheme.typography.amountHero, color = content)
            if (caption != null) Text(caption, style = TanyTheme.typography.caption, color = content)
        }
        if (details != null) {
            Column(Modifier.padding(top = 8.dp), content = details)
        }
    }
}

/** State of one checklist / timeline step — rendered from the server's facts, never inferred. */
enum class TanyStepState { DONE, TODO, WAITING }

/**
 * Vertical timeline step: state glyph on a rail (done ✓ · to do ○ · waiting ◷), title, optional supporting text,
 * trailing value and an action slot. The state is exposed as text to TalkBack ([stateLabel]).
 */
@Composable
fun TanyTimelineStep(
    title: String,
    state: TanyStepState,
    stateLabel: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    isLast: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    action: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = TanyTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
            StepGlyph(state)
            if (!isLast) {
                Box(
                    Modifier
                        .padding(vertical = 2.dp)
                        .width(2.dp)
                        .weight(1f)
                        .clip(TanyTheme.radii.pill)
                        .background(if (state == TanyStepState.DONE) colors.success.accent else colors.border),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp)
                    .semantics(mergeDescendants = true) { stateDescription = stateLabel },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = if (state == TanyStepState.DONE) TanyTheme.typography.body else TanyTheme.typography.bodyStrong,
                        color = if (state == TanyStepState.DONE) colors.textMuted else colors.textPrimary,
                    )
                    if (supporting != null) Text(supporting, style = TanyTheme.typography.caption, color = colors.textMuted)
                }
                trailing?.invoke()
            }
            if (action != null) action()
        }
    }
}

@Composable
private fun StepGlyph(state: TanyStepState) {
    val colors = TanyTheme.colors
    when (state) {
        TanyStepState.DONE -> Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.success.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_tany_check), contentDescription = null, tint = colors.surface, modifier = Modifier.size(16.dp))
        }
        TanyStepState.WAITING -> Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.warning.container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_tany_clock), contentDescription = null, tint = colors.warning.content, modifier = Modifier.size(16.dp))
        }
        TanyStepState.TODO -> Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(2.dp, colors.textSubtle, CircleShape),
        )
    }
}

/** Checkbox-like row (accessories): toggleable semantics, check tile + label, ≥ 48 dp. */
@Composable
fun TanyCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = TanyTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TanyDimens.MinTouchTarget)
            .clip(TanyTheme.radii.medium)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(TanyTheme.radii.small)
                .then(if (checked) Modifier.background(colors.primaryAction) else Modifier.border(2.dp, colors.textSubtle, TanyTheme.radii.small)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(painterResource(R.drawable.ic_tany_check), contentDescription = null, tint = colors.onPrimaryAction, modifier = Modifier.size(16.dp))
        }
        Text(label, style = TanyTheme.typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
    }
}

/** Selectable choice chip (incident type…): radio semantics inside a `selectableGroup`, check glyph when selected. */
@Composable
fun TanyChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TanyTheme.colors
    Surface(
        modifier = modifier
            .heightIn(min = TanyDimens.MinTouchTarget)
            .clip(TanyTheme.radii.pill)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = TanyTheme.radii.pill,
        color = if (selected) colors.primaryAction else colors.surface,
        contentColor = if (selected) colors.onPrimaryAction else colors.textPrimary,
        border = if (selected) null else BorderStroke(TanyDimens.BorderWidth, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) Icon(painterResource(R.drawable.ic_tany_check), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = TanyTheme.typography.label, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

/** Vertical rule used between inline metadata (keeps rows readable in RTL without « · » separators). */
@Composable
fun TanyVerticalRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(TanyDimens.Hairline)
            .fillMaxHeight()
            .background(TanyTheme.colors.divider),
    )
}

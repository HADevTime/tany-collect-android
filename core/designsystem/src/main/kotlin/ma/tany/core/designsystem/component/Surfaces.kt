package ma.tany.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ma.tany.core.designsystem.R
import ma.tany.core.designsystem.theme.TanyDimens
import ma.tany.core.designsystem.theme.TanyTheme

/** Bordered TANY card (borders rather than shadows). */
@Composable
fun TanyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = TanyTheme.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(TanyTheme.radii.large).clickable(role = Role.Button, onClick = onClick) else Modifier),
        shape = TanyTheme.radii.large,
        color = colors.surface,
        contentColor = colors.textPrimary,
        border = BorderStroke(TanyDimens.BorderWidth, colors.border),
    ) {
        Column(modifier = Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** Tappable list row: optional leading icon, title / subtitle, trailing slot, chevron (mirrored in RTL). */
@Composable
fun TanyRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = TanyTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TanyDimens.MinTouchTarget)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leadingIcon != null) TanyIconContainer(icon = leadingIcon, contentDescription = null)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TanyTheme.typography.bodyStrong, color = colors.textPrimary)
            if (subtitle != null) Text(subtitle, style = TanyTheme.typography.caption, color = colors.textMuted)
        }
        trailing?.invoke()
        if (showChevron) {
            Icon(painterResource(R.drawable.ic_tany_chevron), contentDescription = null, tint = colors.textMuted)
        }
    }
}

/**
 * Semantic label / value row (« Caution · 300 DH »). Merged for TalkBack so label and value are read together.
 * [value] may be any composable (money, date, chip).
 */
@Composable
fun TanyInfoRow(
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = if (emphasized) TanyTheme.typography.bodyStrong else TanyTheme.typography.body,
            color = if (emphasized) TanyTheme.colors.textPrimary else TanyTheme.colors.textMuted,
        )
        value()
    }
}

/** Rounded icon tile used in rows, empty states and confirmation sheets. */
@Composable
fun TanyIconContainer(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    container: Color = TanyTheme.colors.neutral,
    tint: Color = TanyTheme.colors.textPrimary,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(TanyTheme.radii.medium)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

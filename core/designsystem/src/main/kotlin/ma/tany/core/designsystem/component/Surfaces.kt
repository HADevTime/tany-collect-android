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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ma.tany.core.designsystem.R
import ma.tany.core.designsystem.theme.TanyDimens
import ma.tany.core.designsystem.theme.TanyTheme

/**
 * Card look: OUTLINED (default, hairline border — TANY favors borders over shadows) · FILLED (neutral fill, no border:
 * secondary information) · CHROME (dark identity card in both themes — Today header).
 */
enum class TanyCardStyle { OUTLINED, FILLED, CHROME }

/**
 * TANY card. [accent] draws a status bar on the leading edge (mirrored in RTL) to prioritise a card in a list —
 * always in addition to a text status (chip), never alone.
 */
@Composable
fun TanyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    style: TanyCardStyle = TanyCardStyle.OUTLINED,
    accent: TanyTone? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = TanyTheme.colors
    val shape = TanyTheme.radii.cardShape
    val container = when (style) {
        TanyCardStyle.OUTLINED -> colors.surface
        TanyCardStyle.FILLED -> colors.neutral
        TanyCardStyle.CHROME -> colors.chrome
    }
    val contentColor = if (style == TanyCardStyle.CHROME) colors.onChrome else colors.textPrimary
    val accentColor = accent?.colors()?.accent
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = container,
        contentColor = contentColor,
        border = if (style == TanyCardStyle.OUTLINED) BorderStroke(TanyDimens.BorderWidth, colors.border) else null,
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (accentColor != null) {
                        Modifier.drawBehind {
                            val bar = 4.dp.toPx()
                            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - bar else 0f
                            drawRect(accentColor, topLeft = Offset(x, 0f), size = Size(bar, size.height))
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Hairline divider between rows of a grouped card ([inset] aligns it with the row text after an icon). */
@Composable
fun TanyDivider(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(TanyDimens.Hairline)
            .background(TanyTheme.colors.divider),
    )
}

/** Section header above a card group, with an optional trailing action / count. */
@Composable
fun TanySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = TanyTheme.typography.overline,
            color = TanyTheme.colors.textMuted,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (trailing != null) {
            Text(
                trailing,
                style = TanyTheme.typography.label,
                color = if (onTrailingClick != null) TanyTheme.colors.textPrimary else TanyTheme.colors.textMuted,
                modifier = if (onTrailingClick != null) {
                    Modifier
                        .clip(TanyTheme.radii.small)
                        .clickable(role = Role.Button, onClick = onTrailingClick)
                        .heightIn(min = TanyDimens.MinTouchTarget)
                        .padding(horizontal = 8.dp, vertical = 14.dp)
                } else {
                    Modifier
                },
            )
        }
    }
}

/**
 * Tappable list row: optional leading icon (tinted by [leadingTone]), title / subtitle, trailing value or slot,
 * chevron (mirrored in RTL). [destructive] colors the title and icon (sign-out) — the label still says what happens.
 */
@Composable
fun TanyRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    leadingTone: TanyTone? = null,
    value: String? = null,
    destructive: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = TanyTheme.colors
    val titleColor = if (destructive) colors.destructive else colors.textPrimary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (leadingIcon != null) {
            val tone = leadingTone?.colors()
            TanyIconContainer(
                icon = leadingIcon,
                contentDescription = null,
                container = when {
                    destructive -> colors.danger.container
                    tone != null -> tone.container
                    else -> colors.neutral
                },
                tint = when {
                    destructive -> colors.destructive
                    tone != null -> tone.content
                    else -> colors.textPrimary
                },
                size = 36.dp,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TanyTheme.typography.bodyStrong, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = TanyTheme.typography.caption, color = colors.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) Text(value, style = TanyTheme.typography.label, color = colors.textMuted, maxLines = 1)
        trailing?.invoke()
        if (showChevron) {
            Icon(painterResource(R.drawable.ic_tany_chevron), contentDescription = null, tint = colors.textSubtle, modifier = Modifier.size(20.dp))
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
    @DrawableRes icon: Int? = null,
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
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = TanyTheme.colors.textSubtle, modifier = Modifier.size(18.dp))
        }
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

/** Icon tile tinted by a status tone. */
@Composable
fun TanyToneIcon(@DrawableRes icon: Int, tone: TanyTone, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val c = tone.colors()
    TanyIconContainer(icon = icon, contentDescription = null, modifier = modifier, container = c.container, tint = c.content, size = size)
}

/** Monogram avatar (no customer photo is ever invented: initials of the server's display name). */
@Composable
fun TanyAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 48.dp, onChrome: Boolean = false) {
    val initials = name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    val colors = TanyTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(TanyTheme.radii.pill)
            .background(if (onChrome) colors.chromeRaised else colors.accentContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = if (size >= 56.dp) TanyTheme.typography.title else TanyTheme.typography.bodyStrong,
            color = if (onChrome) colors.onChrome else colors.onAccentContainer,
            maxLines = 1,
        )
    }
}

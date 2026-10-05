package ma.tany.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ma.tany.core.designsystem.R
import ma.tany.core.designsystem.theme.StatusColors
import ma.tany.core.designsystem.theme.TanyTheme

/** Status tones (aligned with the backend's SUCCESS · INFO · ACTION · WARNING · DANGER · NEUTRAL vocabulary). */
enum class TanyTone { NEUTRAL, INFO, SUCCESS, ACTION, WARNING, DANGER }

/**
 * Container / content / accent for a tone. ACTION uses the SOFT pink container (the solid pink is reserved for the
 * brand mark and badges) so a list full of « to do » items stays calm and readable.
 */
@Composable
fun TanyTone.colors(): StatusColors {
    val c = TanyTheme.colors
    return when (this) {
        TanyTone.NEUTRAL -> c.neutralStatus
        TanyTone.INFO -> c.info
        TanyTone.SUCCESS -> c.success
        TanyTone.ACTION -> StatusColors(container = c.accentContainer, content = c.onAccentContainer, accent = c.accent)
        TanyTone.WARNING -> c.warning
        TanyTone.DANGER -> c.danger
    }
}

/** Glyph paired with a tone (state is never conveyed by color alone). */
fun TanyTone.icon(): Int? = when (this) {
    TanyTone.SUCCESS -> R.drawable.ic_tany_check
    TanyTone.WARNING -> R.drawable.ic_tany_warning
    TanyTone.DANGER -> R.drawable.ic_tany_error
    TanyTone.INFO -> R.drawable.ic_tany_info
    TanyTone.ACTION -> R.drawable.ic_tany_bolt
    TanyTone.NEUTRAL -> null
}

enum class TanyChipSize { SMALL, MEDIUM }

/**
 * Status chip: tone color + icon + TEXT — a state is never conveyed by color alone (accessibility).
 * The label is the localized rendering of a server state code.
 */
@Composable
fun TanyStatusChip(label: String, tone: TanyTone, modifier: Modifier = Modifier, size: TanyChipSize = TanyChipSize.MEDIUM) {
    val colors = tone.colors()
    val small = size == TanyChipSize.SMALL
    Row(
        modifier = modifier
            .clip(TanyTheme.radii.pill)
            .background(colors.container)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 3.dp else 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tone.icon()?.let {
            Icon(painterResource(it), contentDescription = null, tint = colors.content, modifier = Modifier.size(if (small) 12.dp else 14.dp))
        }
        Text(
            label,
            style = if (small) TanyTheme.typography.caption else TanyTheme.typography.label,
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small count / marker badge (e.g. unread count). Solid accent: the one place the pink is loud. */
@Composable
fun TanyBadge(text: String, modifier: Modifier = Modifier, tone: TanyTone = TanyTone.ACTION) {
    val c = TanyTheme.colors
    val container = if (tone == TanyTone.ACTION) c.accent else tone.colors().accent
    val content = if (tone == TanyTone.ACTION) c.onAccent else c.surface
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .clip(TanyTheme.radii.pill)
            .background(container)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = TanyTheme.typography.caption, color = content, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** Unread / attention dot (always paired with a text label for TalkBack by the caller). */
@Composable
fun TanyDot(modifier: Modifier = Modifier, tone: TanyTone = TanyTone.ACTION) {
    val color = if (tone == TanyTone.ACTION) TanyTheme.colors.accent else tone.colors().accent
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

/** Identifier pill (booking reference, asset code): monospace, LTR-isolated by the caller. */
@Composable
fun TanyCodePill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(TanyTheme.radii.small)
            .background(TanyTheme.colors.neutral)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = TanyTheme.typography.caption.copy(fontFamily = TanyTheme.typography.code.fontFamily),
        color = TanyTheme.colors.textMuted,
        maxLines = 1,
    )
}

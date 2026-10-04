package ma.tany.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ma.tany.core.designsystem.R
import ma.tany.core.designsystem.theme.StatusColors
import ma.tany.core.designsystem.theme.TanyTheme

/** Status tones (aligned with the backend's SUCCESS · INFO · ACTION · WARNING · DANGER · NEUTRAL vocabulary). */
enum class TanyTone { NEUTRAL, INFO, SUCCESS, ACTION, WARNING, DANGER }

@Composable
fun TanyTone.colors(): StatusColors {
    val c = TanyTheme.colors
    return when (this) {
        TanyTone.NEUTRAL -> c.neutralStatus
        TanyTone.INFO -> c.info
        TanyTone.SUCCESS -> c.success
        TanyTone.ACTION -> StatusColors(container = c.accent, content = c.onAccent, accent = c.accent)
        TanyTone.WARNING -> c.warning
        TanyTone.DANGER -> c.danger
    }
}

private fun TanyTone.icon(): Int? = when (this) {
    TanyTone.SUCCESS -> R.drawable.ic_tany_check
    TanyTone.WARNING -> R.drawable.ic_tany_warning
    TanyTone.DANGER -> R.drawable.ic_tany_error
    TanyTone.INFO -> R.drawable.ic_tany_info
    TanyTone.ACTION, TanyTone.NEUTRAL -> null
}

/**
 * Status chip: tone color + icon + TEXT — a state is never conveyed by color alone (accessibility).
 * The label is the localized rendering of a server state code.
 */
@Composable
fun TanyStatusChip(label: String, tone: TanyTone, modifier: Modifier = Modifier) {
    val colors = tone.colors()
    Row(
        modifier = modifier
            .clip(TanyTheme.radii.pill)
            .background(colors.container)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tone.icon()?.let { Icon(painterResource(it), contentDescription = null, tint = colors.content, modifier = Modifier.size(14.dp)) }
        Text(label, style = TanyTheme.typography.label, color = colors.content, maxLines = 1)
    }
}

/** Small count / marker badge (e.g. unread count). */
@Composable
fun TanyBadge(text: String, modifier: Modifier = Modifier, tone: TanyTone = TanyTone.ACTION) {
    val colors = tone.colors()
    Text(
        text = text,
        modifier = modifier
            .clip(TanyTheme.radii.pill)
            .background(colors.container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = TanyTheme.typography.caption,
        color = colors.content,
        maxLines = 1,
    )
}

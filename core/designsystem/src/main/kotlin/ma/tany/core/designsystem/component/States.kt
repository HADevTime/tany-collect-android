package ma.tany.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ma.tany.core.designsystem.R
import ma.tany.core.designsystem.theme.TanyTheme

/** Full-screen spinner — only for app-level transitions; operational screens use skeletons ([TanyListSkeleton]). */
@Composable
fun TanyLoadingState(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.tany_state_loading)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = TanyTheme.colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
    }
}

/**
 * Empty state: layered illustration tile, title, explanation, up to two actions and an optional [extra] slot (tips,
 * shortcuts). Scrolls when the content is taller than the screen (large fonts).
 */
@Composable
fun TanyEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    @DrawableRes icon: Int = R.drawable.ic_tany_inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tone: TanyTone = TanyTone.NEUTRAL,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    CenteredMessage(modifier, icon, title, message, actionLabel, onAction, secondaryLabel, onSecondary, tone, isError = false, extra = extra)
}

/** Error state: the message is the localized mapping of the semantic error code (never the server's French text). */
@Composable
fun TanyErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.tany_error_title),
    onRetry: (() -> Unit)? = null,
) {
    CenteredMessage(
        modifier,
        R.drawable.ic_tany_error,
        title,
        message,
        if (onRetry != null) stringResource(R.string.tany_action_retry) else null,
        onRetry,
        null,
        null,
        TanyTone.DANGER,
        isError = true,
        extra = null,
    )
}

/** Layered « halo » illustration: a soft ring around a tinted icon tile. */
@Composable
fun TanyIllustration(@DrawableRes icon: Int, tone: TanyTone = TanyTone.NEUTRAL, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val colors = TanyTheme.colors
    val c = tone.colors()
    val inner = if (tone == TanyTone.NEUTRAL) colors.surface else c.container
    val ring = if (tone == TanyTone.NEUTRAL) colors.neutral else c.container.copy(alpha = 0.45f)
    Box(
        modifier = modifier
            .size(size)
            .clip(TanyTheme.radii.pill)
            .background(ring),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.66f)
                .clip(TanyTheme.radii.pill)
                .background(inner),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (tone == TanyTone.NEUTRAL) colors.textPrimary else c.content,
                modifier = Modifier.size(size * 0.3f),
            )
        }
    }
}

@Composable
private fun CenteredMessage(
    modifier: Modifier,
    @DrawableRes icon: Int,
    title: String,
    message: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    secondaryLabel: String?,
    onSecondary: (() -> Unit)?,
    tone: TanyTone,
    isError: Boolean,
    extra: (@Composable ColumnScope.() -> Unit)?,
) {
    val colors = TanyTheme.colors
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Scrolls on its own only when given a bounded height (never nested inside another vertical scroll).
        val bounded = constraints.hasBoundedHeight
        val minHeight = if (bounded) maxHeight else 0.dp
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (bounded) Modifier.verticalScroll(scroll) else Modifier)
                .heightIn(min = minHeight)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .semantics(mergeDescendants = true) { if (isError) liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TanyIllustration(icon = icon, tone = tone)
                Spacer(Modifier.height(8.dp))
                Text(
                    title,
                    style = TanyTheme.typography.title,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                if (message != null) Text(message, style = TanyTheme.typography.body, color = colors.textMuted, textAlign = TextAlign.Center)
            }
            if ((actionLabel != null && onAction != null) || (secondaryLabel != null && onSecondary != null)) {
                Spacer(Modifier.height(24.dp))
                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (actionLabel != null && onAction != null) {
                        TanyButton(text = actionLabel, onClick = onAction, style = if (isError) TanyButtonStyle.SECONDARY else TanyButtonStyle.PRIMARY)
                    }
                    if (secondaryLabel != null && onSecondary != null) {
                        TanyButton(text = secondaryLabel, onClick = onSecondary, style = TanyButtonStyle.TEXT)
                    }
                }
            }
            if (extra != null) {
                Spacer(Modifier.height(24.dp))
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = extra)
            }
        }
    }
}

/**
 * Inline notice (info / waiting / warning / refusal). [tone] DANGER and WARNING are announced politely to TalkBack.
 * The text is localized app copy — never the server's French prose.
 */
@Composable
fun TanyNotice(
    message: String,
    tone: TanyTone,
    modifier: Modifier = Modifier,
    title: String? = null,
    @DrawableRes icon: Int? = null,
) {
    val c = tone.colors()
    val announce = tone == TanyTone.DANGER || tone == TanyTone.WARNING
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(TanyTheme.radii.large)
            .background(if (tone == TanyTone.NEUTRAL) TanyTheme.colors.neutral else c.container)
            .semantics(mergeDescendants = true) { if (announce) liveRegion = LiveRegionMode.Polite }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val glyph = icon ?: tone.icon() ?: R.drawable.ic_tany_info
        Icon(painterResource(glyph), contentDescription = null, tint = c.content, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (title != null) Text(title, style = TanyTheme.typography.bodyStrong, color = c.content)
            Text(message, style = TanyTheme.typography.label, color = if (tone == TanyTone.NEUTRAL) TanyTheme.colors.textMuted else c.content)
        }
    }
}

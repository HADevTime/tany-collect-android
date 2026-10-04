package ma.tany.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ma.tany.core.designsystem.R
import ma.tany.core.designsystem.theme.TanyTheme

@Composable
fun TanyLoadingState(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.tany_state_loading)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = TanyTheme.colors.accent)
    }
}

@Composable
fun TanyEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    @DrawableRes icon: Int = R.drawable.ic_tany_inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    CenteredMessage(modifier, icon, title, message, actionLabel, onAction, isError = false)
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
        isError = true,
    )
}

@Composable
private fun CenteredMessage(
    modifier: Modifier,
    @DrawableRes icon: Int,
    title: String,
    message: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    isError: Boolean,
) {
    val colors = TanyTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .semantics(mergeDescendants = true) { if (isError) liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TanyIconContainer(
            icon = icon,
            contentDescription = null,
            container = if (isError) colors.danger.container else colors.neutral,
            tint = if (isError) colors.danger.content else colors.textMuted,
            size = 56.dp,
        )
        Text(title, style = TanyTheme.typography.headline, color = colors.textPrimary, textAlign = TextAlign.Center)
        if (message != null) Text(message, style = TanyTheme.typography.body, color = colors.textMuted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            TanyButton(text = actionLabel, onClick = onAction, style = TanyButtonStyle.SECONDARY, fillWidth = false)
        }
    }
}

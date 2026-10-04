package ma.tany.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import ma.tany.core.designsystem.R
import ma.tany.core.designsystem.theme.TanyTheme

/**
 * TANY top bar. [chrome] = dark bar in both themes (TANY Collect header); otherwise page-colored.
 * The back icon is auto-mirrored in RTL.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TanyTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    chrome: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = TanyTheme.colors
    val container = if (chrome) colors.chrome else colors.page
    val content = if (chrome) colors.onChrome else colors.textPrimary
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = TanyTheme.typography.headline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_tany_back), contentDescription = stringResource(R.string.tany_action_back))
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = container,
            scrolledContainerColor = container,
            titleContentColor = content,
            navigationIconContentColor = content,
            actionIconContentColor = content,
        ),
    )
}

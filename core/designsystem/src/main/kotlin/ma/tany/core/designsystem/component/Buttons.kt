package ma.tany.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ma.tany.core.designsystem.theme.TanyDimens
import ma.tany.core.designsystem.theme.TanyTheme

/**
 * PRIMARY = the one main action of a screen / card (TANY black, light in dark mode) · TONAL = an available step among
 * several (soft accent container) · SECONDARY = outlined alternative · DESTRUCTIVE · TEXT · ACCENT = the TANY pink
 * call to action on a dark chrome surface (« Prochaine opération » hero, like TANY Collect iOS).
 */
enum class TanyButtonStyle { PRIMARY, SECONDARY, TONAL, DESTRUCTIVE, TEXT, ACCENT }

/**
 * Guards against double taps: a click is ignored while [busy] or within [windowMs] of the previous click.
 * Business actions (booking, confirmations, payments) must also disable the button while the request runs.
 */
@Composable
fun rememberSingleTap(windowMs: Long = 700L, busy: Boolean = false, onClick: () -> Unit): () -> Unit {
    var last by remember { mutableLongStateOf(0L) }
    return {
        val now = System.currentTimeMillis()
        if (!busy && now - last >= windowMs) {
            last = now
            onClick()
        }
    }
}

/**
 * The TANY button. Full-width by default, ≥ 52 dp tall (touch target ≥ 48 dp), loading state announced to
 * TalkBack, double-tap safe. Destructive actions use [TanyButtonStyle.DESTRUCTIVE] — never color alone: the label
 * must say what happens.
 */
@Composable
fun TanyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TanyButtonStyle = TanyButtonStyle.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingDescription: String? = null,
    fillWidth: Boolean = true,
    @DrawableRes icon: Int? = null,
    compact: Boolean = false,
) {
    val colors = TanyTheme.colors
    val guarded = rememberSingleTap(busy = loading, onClick = onClick)
    val base = modifier
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .heightIn(min = if (compact) TanyDimens.MinTouchTarget else TanyDimens.ButtonHeight)
        .semantics { if (loading && loadingDescription != null) stateDescription = loadingDescription }
    val shape = TanyTheme.radii.large
    val isEnabled = enabled && !loading
    val content: @Composable (Color) -> Unit = { contentColor ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = contentColor, strokeWidth = 2.dp)
            } else if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(text = text, style = TanyTheme.typography.bodyStrong, textAlign = TextAlign.Center)
        }
    }
    when (style) {
        TanyButtonStyle.PRIMARY, TanyButtonStyle.DESTRUCTIVE, TanyButtonStyle.ACCENT -> {
            val container = when (style) {
                TanyButtonStyle.PRIMARY -> colors.primaryAction
                TanyButtonStyle.ACCENT -> colors.accent
                else -> colors.destructive
            }
            val onContainer = when (style) {
                TanyButtonStyle.PRIMARY -> colors.onPrimaryAction
                TanyButtonStyle.ACCENT -> colors.onAccent
                else -> colors.onDestructive
            }
            Button(
                onClick = guarded,
                modifier = base,
                enabled = isEnabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = container,
                    contentColor = onContainer,
                    disabledContainerColor = colors.neutral,
                    disabledContentColor = colors.textMuted,
                ),
            ) { content(onContainer) }
        }
        TanyButtonStyle.TONAL -> Button(
            onClick = guarded,
            modifier = base,
            enabled = isEnabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentContainer,
                contentColor = colors.onAccentContainer,
                disabledContainerColor = colors.neutral,
                disabledContentColor = colors.textMuted,
            ),
        ) { content(colors.onAccentContainer) }
        TanyButtonStyle.SECONDARY -> OutlinedButton(
            onClick = guarded,
            modifier = base,
            enabled = isEnabled,
            shape = shape,
            border = BorderStroke(TanyDimens.BorderWidth, colors.border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = colors.surface,
                contentColor = colors.textPrimary,
                disabledContentColor = colors.textMuted,
            ),
        ) { content(colors.textPrimary) }
        TanyButtonStyle.TEXT -> TextButton(
            onClick = guarded,
            modifier = base,
            enabled = isEnabled,
            shape = shape,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.textPrimary, disabledContentColor = colors.textMuted),
        ) { content(colors.textPrimary) }
    }
}

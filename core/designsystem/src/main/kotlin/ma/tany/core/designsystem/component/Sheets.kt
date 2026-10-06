package ma.tany.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ma.tany.core.designsystem.R
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.common.MoneyAmount

/** TANY-styled Material modal bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TanyBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { dismissible || it != androidx.compose.material3.SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = { if (dismissible) onDismiss() },
        modifier = modifier,
        sheetState = state,
        shape = TanyTheme.radii.sheet,
        containerColor = TanyTheme.colors.elevated,
        contentColor = TanyTheme.colors.textPrimary,
        scrimColor = TanyTheme.colors.scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TanyTheme.colors.textSubtle.copy(alpha = 0.4f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

enum class ConfirmationKind {
    /** Regular important step. */
    STANDARD,

    /** Money changes hands (cash, deposit): the exact amount is the visual focus. */
    FINANCIAL,

    /** Irreversible / penalizing action (cancellation…): destructive CTA. */
    DESTRUCTIVE,
}

/** What a confirmation sheet shows. Texts are already localized by the feature. */
data class ConfirmationRequest(
    val id: String,
    val title: String,
    val message: String,
    val confirmLabel: String,
    val kind: ConfirmationKind = ConfirmationKind.STANDARD,
    val amount: MoneyAmount? = null,
    @DrawableRes val icon: Int? = null,
    /** Label above the amount of a FINANCIAL confirmation (« Montant reçu en espèces »). */
    val amountLabel: String? = null,
)

/**
 * Holds AT MOST one visible confirmation: a second request while one is shown is ignored
 * (one confirmation at a time, no stacked sheets).
 */
@Stable
class ConfirmationState {
    var current: ConfirmationRequest? by mutableStateOf(null)
        private set
    var processing: Boolean by mutableStateOf(false)

    /** @return false when another confirmation is already visible. */
    fun show(request: ConfirmationRequest): Boolean {
        if (current != null) return false
        processing = false
        current = request
        return true
    }

    fun dismiss() {
        if (!processing) current = null
    }

    /** Closes after the action finished (success or handled error). */
    fun finish() {
        processing = false
        current = null
    }
}

@Composable
fun rememberConfirmationState(): ConfirmationState = remember { ConfirmationState() }

/**
 * Host to place once per screen. Confirm triggers [onConfirm] once (double-tap safe, CTA disabled while
 * [ConfirmationState.processing]); dismissal is blocked while processing.
 */
@Composable
fun ConfirmationSheetHost(state: ConfirmationState, onConfirm: (ConfirmationRequest) -> Unit) {
    val request = state.current ?: return
    TanyBottomSheet(onDismiss = state::dismiss, dismissible = !state.processing) {
        val tone = when (request.kind) {
            ConfirmationKind.DESTRUCTIVE -> TanyTone.DANGER
            ConfirmationKind.FINANCIAL -> TanyTone.SUCCESS
            ConfirmationKind.STANDARD -> TanyTone.NEUTRAL
        }
        val icon = request.icon ?: when (request.kind) {
            ConfirmationKind.DESTRUCTIVE -> R.drawable.ic_tany_warning
            ConfirmationKind.FINANCIAL -> R.drawable.ic_tany_cash
            ConfirmationKind.STANDARD -> R.drawable.ic_tany_check
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TanyIllustration(icon = icon, tone = tone, size = 72.dp)
            Text(
                request.title,
                style = TanyTheme.typography.title,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(request.message, style = TanyTheme.typography.body, color = TanyTheme.colors.textMuted, textAlign = TextAlign.Center)
        }
        if (request.kind == ConfirmationKind.FINANCIAL && request.amount != null) {
            // The exact server amount is the visual focus of a money confirmation.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TanyTheme.radii.large)
                    .background(TanyTheme.colors.neutral)
                    .semantics(mergeDescendants = true) {}
                    .padding(vertical = 16.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                request.amountLabel?.let { Text(it, style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted, textAlign = TextAlign.Center) }
                MoneyText(request.amount, style = TanyTheme.typography.amountHero)
            }
        }
        Spacer(Modifier.height(4.dp))
        TanyButton(
            text = request.confirmLabel,
            style = if (request.kind == ConfirmationKind.DESTRUCTIVE) TanyButtonStyle.DESTRUCTIVE else TanyButtonStyle.PRIMARY,
            loading = state.processing,
            onClick = {
                if (!state.processing) {
                    state.processing = true
                    onConfirm(request)
                }
            },
        )
        TanyButton(
            text = stringResource(R.string.tany_action_cancel),
            style = TanyButtonStyle.TEXT,
            enabled = !state.processing,
            onClick = state::dismiss,
        )
    }
}

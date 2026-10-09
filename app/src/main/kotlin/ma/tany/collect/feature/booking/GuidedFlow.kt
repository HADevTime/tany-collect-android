package ma.tany.collect.feature.booking

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import ma.tany.collect.R
import ma.tany.collect.core.ui.dayLabel
import ma.tany.collect.feature.operations.confirmHaptic
import ma.tany.collect.feature.operations.text
import ma.tany.collect.feature.today.durationText
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.component.MoneyText
import ma.tany.core.designsystem.component.ProductImageSurface
import ma.tany.core.designsystem.component.TanyAmountPanel
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyCodePill
import ma.tany.core.designsystem.component.TanyIllustration
import ma.tany.core.designsystem.component.TanyInfoRow
import ma.tany.core.designsystem.component.TanyNotice
import ma.tany.core.designsystem.component.TanyProgressBar
import ma.tany.core.designsystem.component.TanySegment
import ma.tany.core.designsystem.component.TanySegmentedControl
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.model.collect.AssetStatus
import ma.tany.core.model.collect.MerchantBookingDetail
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.common.AssetCondition
import ma.tany.core.model.common.MoneyAmount
import ma.tany.core.model.common.PhotoType
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.CollectOperationError

/**
 * Guided pickup / return: ONE active step at a time, chosen from the server's facts ([pickupStep] / [returnStep]). Each
 * step offers its single gesture; the server accepts or refuses it and the next read moves the flow forward. The
 * merchant never finalises: the customer confirms in TANY.
 */
@Composable
internal fun GuidedFlow(booking: MerchantBookingDetail, endpoint: ApiEndpoint, ui: PickupUiState, actions: PickupActions) {
    val returnFlow = booking.isReturnFlow()
    val pickupStep = booking.pickupStep(ui.photosAccepted)
    val returnStep = booking.returnStep(ui.photosAccepted)
    val position = if (returnFlow) returnStep.position() else pickupStep.position()
    val total = if (returnFlow) booking.returnStepCount() else PICKUP_STEP_COUNT
    val stepKey: Any = if (returnFlow) returnStep else pickupStep
    // Haptic confirmation each time the SERVER moves the flow forward (QR / label / photo / payment accepted).
    val view = LocalView.current
    var lastPosition by remember { mutableStateOf(position) }
    LaunchedEffect(position) {
        if (position > lastPosition) view.confirmHaptic()
        lastPosition = position
    }
    val progressLabel = stringResource(R.string.flow_step_of, position, total)
    Column(Modifier.fillMaxSize()) {
        TanyProgressBar(
            progress = position / total.toFloat(),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .semantics { stateDescription = progressLabel },
        )
        AnimatedContent(
            targetState = stepKey,
            transitionSpec = { tanyScreenTransition(forward = true) },
            label = "flow-step",
        ) { step ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (step) {
                    is PickupStep -> PickupStepContent(step, booking, endpoint, ui, actions)
                    is ReturnStep -> ReturnStepContent(step, booking, endpoint, ui, actions)
                    else -> Unit
                }
                // Refusals of the current gesture, typed from the server code (never a generic message).
                ui.error?.let { error -> TanyNotice(title = stringResource(R.string.flow_refused), message = error.text(), tone = TanyTone.DANGER) }
            }
        }
    }
}

/** Step title + one-line explanation. */
@Composable
private fun StepHeader(title: String, message: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = TanyTheme.typography.largeTitle, modifier = Modifier.semantics { heading() })
        message?.let { Text(it, style = TanyTheme.typography.body, color = TanyTheme.colors.textMuted) }
    }
}

/** Large object card: image, full product name, unit code to find on the shelf. */
@Composable
private fun ObjectCard(booking: MerchantBookingDetail, endpoint: ApiEndpoint) {
    TanyCard(contentPadding = 18.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImageSurface(
                url = endpoint.resolveMedia(booking.product.displayImage),
                contentDescription = booking.product.name,
                modifier = Modifier.width(112.dp),
                padding = 10.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(booking.product.name, style = TanyTheme.typography.title)
                (booking.asset?.code ?: booking.assetCode)?.let {
                    Text(stringResource(R.string.flow_asset_code), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
                    Text(ltrIsolated(it), style = TanyTheme.typography.codeLarge)
                }
            }
        }
    }
}

@Composable
private fun PickupStepContent(step: PickupStep, booking: MerchantBookingDetail, endpoint: ApiEndpoint, ui: PickupUiState, actions: PickupActions) {
    val busy = ui.busy != null
    when (step) {
        PickupStep.CUSTOMER -> {
            StepHeader(stringResource(R.string.flow_customer_title), stringResource(R.string.flow_customer_pickup_message))
            CustomerCard(booking, endpoint, prominent = true)
            TanyButton(stringResource(R.string.pickup_scan_customer), actions.scanCustomer, icon = DsR.drawable.ic_tany_qr, enabled = !busy)
            Text(stringResource(R.string.flow_customer_code_hint), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
        }
        PickupStep.ASSET -> {
            StepHeader(stringResource(R.string.flow_asset_title), stringResource(R.string.flow_asset_message))
            ObjectCard(booking, endpoint)
            TanyButton(stringResource(R.string.flow_scan_label), actions.scanAsset, icon = DsR.drawable.ic_tany_tag, enabled = !busy)
        }
        PickupStep.PHOTO -> PhotoStep(
            title = stringResource(R.string.flow_photo_pickup_title),
            booking = booking,
            endpoint = endpoint,
            type = PhotoType.PICKUP,
            photoCount = booking.pickup?.photoCount ?: 0,
            ui = ui,
            actions = actions,
            allowIncident = true,
        )
        PickupStep.PAYMENT -> {
            val due = booking.amountDueAtPickup() ?: MoneyAmount.ZERO
            StepHeader(stringResource(R.string.flow_payment_title), stringResource(R.string.flow_payment_message))
            TanyAmountPanel(
                label = stringResource(R.string.money_to_collect),
                amount = due,
                icon = DsR.drawable.ic_tany_cash,
                caption = stringResource(R.string.pickup_amount_caption),
            ) {
                // Server breakdown only (rental + deposit), never recomputed.
                (booking.payment?.rentalAmount ?: booking.pricing?.rentalTotal)?.let {
                    TanyInfoRow(stringResource(R.string.booking_rental_amount)) { MoneyText(it) }
                }
                (booking.payment?.depositAmount ?: booking.pricing?.deposit)?.takeIf { !it.isZero }?.let {
                    TanyInfoRow(stringResource(R.string.booking_deposit_amount)) { MoneyText(it) }
                }
                TanyInfoRow(stringResource(R.string.money_payment)) {
                    Text(stringResource(R.string.money_payment_cash), style = TanyTheme.typography.bodyStrong)
                }
            }
            TanyButton(
                stringResource(R.string.pickup_cash_cta_amount, LocalTanyFormatters.current.money(due)),
                { actions.cash(due) },
                icon = DsR.drawable.ic_tany_cash,
                enabled = !busy,
                loading = ui.busy == PickupGesture.PAYMENT,
            )
        }
        PickupStep.HANDOVER -> {
            StepHeader(stringResource(R.string.flow_handover_title), stringResource(R.string.flow_handover_message))
            ObjectCard(booking, endpoint)
            CustomerCard(booking, endpoint)
            // Rental kit: « Kit à remettre » — everything ticked, one tap per element not handed over.
            booking.kit?.takeIf { it.items.isNotEmpty() }?.let { kit ->
                HandoverKitChecklist(kit, ui.kitNotHandedOver, endpoint, actions.toggleHandoverKitItem)
            }
            if (ui.incidentReported) {
                TanyNotice(message = stringResource(R.string.incident_reported_continue), tone = TanyTone.WARNING, icon = DsR.drawable.ic_tany_shield)
            }
            TanyButton(
                stringResource(R.string.pickup_handover_action),
                actions.handover,
                icon = DsR.drawable.ic_tany_pickup,
                enabled = !busy,
                loading = ui.busy == PickupGesture.HANDOVER,
            )
        }
        PickupStep.CUSTOMER_CONFIRMATION -> {
            StepHeader(stringResource(R.string.flow_waiting_title), stringResource(R.string.flow_waiting_pickup_message))
            booking.pickup?.completionDeadline?.let {
                TanyInfoRow(stringResource(R.string.pickup_complete_before), icon = DsR.drawable.ic_tany_clock) {
                    Text(LocalTanyFormatters.current.businessTime(it), style = TanyTheme.typography.bodyStrong)
                }
            }
            WaitingPanel(booking, ui, actions)
        }
        PickupStep.DONE -> DonePanel(booking, actions)
    }
}

@Composable
private fun ReturnStepContent(step: ReturnStep, booking: MerchantBookingDetail, endpoint: ApiEndpoint, ui: PickupUiState, actions: PickupActions) {
    val busy = ui.busy != null
    // Lateness is information only: the return is always received (backend rule, never blocking).
    if (step in setOf(ReturnStep.CUSTOMER, ReturnStep.ASSET, ReturnStep.PHOTO, ReturnStep.STATEMENT)) {
        booking.lateMinutes?.takeIf { it > 0 }?.let { late ->
            TanyNotice(
                title = stringResource(R.string.return_late_title, durationText(late)),
                message = stringResource(R.string.return_late_message),
                tone = TanyTone.WARNING,
                icon = DsR.drawable.ic_tany_clock,
            )
        }
    }
    when (step) {
        ReturnStep.CUSTOMER -> {
            StepHeader(stringResource(R.string.flow_customer_title), stringResource(R.string.flow_customer_return_message))
            CustomerCard(booking, endpoint, prominent = true)
            TanyButton(stringResource(R.string.pickup_scan_customer), actions.scanCustomer, icon = DsR.drawable.ic_tany_qr, enabled = !busy)
            Text(stringResource(R.string.flow_customer_code_hint), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
        }
        ReturnStep.ASSET -> {
            StepHeader(stringResource(R.string.flow_asset_title), stringResource(R.string.flow_asset_return_message))
            ObjectCard(booking, endpoint)
            TanyButton(stringResource(R.string.flow_scan_label), actions.scanAsset, icon = DsR.drawable.ic_tany_tag, enabled = !busy)
        }
        ReturnStep.PHOTO -> PhotoStep(
            title = stringResource(R.string.flow_photo_return_title),
            booking = booking,
            endpoint = endpoint,
            type = PhotoType.RETURN,
            photoCount = booking.returnInfo?.photoCount ?: 0,
            ui = ui,
            actions = actions,
            allowIncident = false,
        )
        ReturnStep.STATEMENT -> {
            StepHeader(stringResource(R.string.flow_statement_title), stringResource(R.string.flow_statement_message))
            TanyCard { ReturnStatementForm(booking, ui.returnForm, endpoint, actions.updateReturn) }
            TanyButton(
                stringResource(R.string.return_statement_action),
                actions.declareReturn,
                icon = DsR.drawable.ic_tany_check,
                enabled = !busy,
                loading = ui.busy == PickupGesture.RETURN_STATEMENT,
            )
        }
        ReturnStep.CUSTOMER_CONFIRMATION -> {
            StepHeader(stringResource(R.string.flow_waiting_title), stringResource(R.string.flow_waiting_return_message))
            WaitingPanel(booking, ui, actions)
        }
        ReturnStep.DEPOSIT -> DepositStep(booking, ui, actions)
        ReturnStep.DEPOSIT_CONFIRMATION -> {
            StepHeader(stringResource(R.string.flow_waiting_title), stringResource(R.string.flow_waiting_deposit_message))
            WaitingPanel(booking, ui, actions)
        }
        ReturnStep.DONE -> DonePanel(booking, actions)
    }
}

/**
 * Condition + photo, progressively: the condition first (« Bon état » / « Signaler un problème »), then the capture; once
 * the server has the photo, a preview with « Reprendre une photo » (adds one, within the server's limit) and « Continuer ».
 */
@Composable
private fun PhotoStep(
    title: String,
    booking: MerchantBookingDetail,
    endpoint: ApiEndpoint,
    type: PhotoType,
    photoCount: Int,
    ui: PickupUiState,
    actions: PickupActions,
    allowIncident: Boolean,
) {
    val busy = ui.busy != null
    StepHeader(title, stringResource(R.string.photo_guide))
    TanyCard(contentPadding = 18.dp) {
        Text(stringResource(R.string.pickup_condition_label), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
        TanySegmentedControl(
            options = listOf(
                TanySegment(AssetCondition.GOOD, stringResource(R.string.pickup_condition_good), DsR.drawable.ic_tany_check),
                TanySegment(AssetCondition.ISSUE_REPORTED, stringResource(R.string.pickup_condition_issue), DsR.drawable.ic_tany_warning),
            ),
            selected = if (ui.photoCondition == AssetCondition.ISSUE_REPORTED) AssetCondition.ISSUE_REPORTED else AssetCondition.GOOD,
            onSelect = actions.onCondition,
        )
        if (ui.photoCondition == AssetCondition.ISSUE_REPORTED) {
            TanyNotice(
                message = stringResource(if (allowIncident) R.string.flow_issue_pickup else R.string.flow_issue_return),
                tone = TanyTone.WARNING,
                icon = DsR.drawable.ic_tany_warning,
            )
            if (allowIncident) {
                TanyButton(
                    stringResource(R.string.incident_report_action),
                    actions.reportProblem,
                    style = TanyButtonStyle.SECONDARY,
                    icon = DsR.drawable.ic_tany_shield,
                    enabled = !busy,
                )
            }
        }
    }
    val photos = booking.photos.filter { it.type == type }
    if (photoCount == 0) {
        TanyButton(
            stringResource(R.string.pickup_take_photo),
            actions.takePhoto,
            icon = DsR.drawable.ic_tany_camera,
            enabled = !busy,
            loading = ui.busy == PickupGesture.PHOTO,
        )
    } else {
        var zoomed by remember { mutableStateOf<String?>(null) }
        Text(
            stringResource(R.string.flow_photo_received, photoCount),
            style = TanyTheme.typography.bodyStrong,
            color = TanyTheme.colors.success.content,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.id }) { photo ->
                    AsyncImage(
                        model = endpoint.resolveMedia(photo.url),
                        contentDescription = stringResource(R.string.flow_photo_preview),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(132.dp)
                            .clip(TanyTheme.radii.large)
                            .clickable(role = Role.Image) { zoomed = photo.url },
                    )
                }
            }
        }
        TanyButton(stringResource(R.string.flow_photo_continue), actions.acceptPhotos, enabled = !busy)
        TanyButton(
            stringResource(R.string.flow_photo_retake),
            actions.takePhoto,
            style = TanyButtonStyle.SECONDARY,
            icon = DsR.drawable.ic_tany_camera,
            enabled = !busy,
            loading = ui.busy == PickupGesture.PHOTO,
        )
        zoomed?.let { url -> PhotoZoom(endpoint, url) { zoomed = null } }
    }
}

/** Test tag of the secondary « Caution reçue / Retenue TANY » lines of the deposit step. */
internal const val DEPOSIT_BREAKDOWN_TAG = "deposit-breakdown"

/**
 * Deposit hand-back = the server's facts: amount (`deposit.refundableAmount`, else `toRefundAmount`), deferred-refund QR check (`refundPickup`).
 * The decision and the amount are TANY's; the customer alone confirms the amount received.
 */
@Composable
private fun DepositStep(booking: MerchantBookingDetail, ui: PickupUiState, actions: PickupActions) {
    val deposit = booking.deposit ?: return
    val money = booking.depositHandBack() ?: return
    val busy = ui.busy != null
    // « À remettre au client » = the server's amount for the current decision, sent back exactly (`expectedAmount`).
    val amount = money.handBack
    val refundPickup = deposit.refundPickup
    StepHeader(stringResource(R.string.flow_deposit_title), stringResource(R.string.deposit_customer_closes))
    // Money hierarchy: the amount to hand back dominates; deposit received and TANY retention stay secondary.
    TanyAmountPanel(
        label = stringResource(R.string.deposit_amount_to_hand_back_customer),
        amount = amount,
        tone = TanyTone.WARNING,
        icon = DsR.drawable.ic_tany_cash,
        caption = when {
            money.isLatePenalty -> stringResource(money.reasonCode.explanationRes())
            refundPickup?.partial == true -> stringResource(R.string.deposit_partial)
            else -> stringResource(R.string.deposit_decided_by_tany)
        },
    )
    money.received?.let { received ->
        Column(Modifier.testTag(DEPOSIT_BREAKDOWN_TAG)) {
            TanyInfoRow(stringResource(R.string.deposit_received_label)) { MoneyText(received) }
            TanyInfoRow(stringResource(R.string.deposit_retention_label)) { MoneyText(money.retention) }
        }
    }
    // Deferred refund: the hand-back is offered once the SERVER recorded the customer's deposit QR (≤ 15 min).
    val qrMissing = ui.failed == PickupGesture.DEPOSIT_REFUND && ui.error == CollectOperationError.DepositRefundQrRequired
    // IMMEDIATE hand-back (same session as the return, incl. a late-return retention): no QR, ever.
    val qrPending = deposit.isDeferredHandBack && refundPickup != null && (refundPickup.verifiedAt == null || qrMissing)
    if (qrPending) {
        TanyNotice(message = stringResource(R.string.flow_deposit_qr_message), tone = TanyTone.INFO, icon = DsR.drawable.ic_tany_qr)
        TanyButton(stringResource(R.string.deposit_scan_qr), actions.scanDepositQr, icon = DsR.drawable.ic_tany_qr, enabled = !busy)
    } else if (amount > MoneyAmount.ZERO) {
        TanyButton(
            stringResource(R.string.flow_deposit_cta, LocalTanyFormatters.current.money(amount)),
            { actions.handBackDeposit(amount) },
            icon = DsR.drawable.ic_tany_cash,
            enabled = !busy,
            loading = ui.busy == PickupGesture.DEPOSIT_REFUND,
        )
    }
}

/**
 * Waiting for the customer's confirmation in TANY (server phase `*_awaiting_customer`): calm state, the screen re-reads
 * by itself, « Relancer le client » (server-throttled). « Toujours en attente » comes from the SERVER flag
 * `customerConfirmationOverdue` only. There is never a « confirm for the customer » action.
 */
@Composable
internal fun WaitingPanel(booking: MerchantBookingDetail, ui: PickupUiState, actions: PickupActions) {
    val overdue = booking.customerConfirmationOverdue
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.nudgeAvailableAt) {
        while (System.currentTimeMillis() < ui.nudgeAvailableAt) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }
    val cooldown = ((ui.nudgeAvailableAt - now + 999) / 1_000).toInt().coerceAtLeast(0)
    TanyCard(contentPadding = 20.dp, accent = TanyTone.DANGER.takeIf { overdue }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TanyIllustration(DsR.drawable.ic_tany_clock, tone = if (overdue) TanyTone.DANGER else TanyTone.WARNING, size = 72.dp)
            Text(
                stringResource(if (overdue) R.string.waiting_overdue else R.string.step_waiting),
                style = TanyTheme.typography.headline,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            booking.waitingMinutes?.takeIf { it > 0 }?.let {
                Text(stringResource(R.string.waiting_since, durationText(it)), style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(DsR.drawable.ic_tany_refresh), contentDescription = null, tint = TanyTheme.colors.textSubtle, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.waiting_auto_refresh), style = TanyTheme.typography.caption, color = TanyTheme.colors.textSubtle)
            }
        }
        TanyButton(
            if (cooldown > 0) stringResource(R.string.nudge_cooldown, cooldown) else stringResource(R.string.nudge_action),
            actions.nudge,
            style = TanyButtonStyle.SECONDARY,
            enabled = cooldown == 0,
            loading = ui.nudging,
            icon = DsR.drawable.ic_tany_bell,
        )
        ui.nudgeOutcome?.let {
            Text(
                stringResource(if (it == NudgeOutcome.SENT) R.string.nudge_sent else R.string.nudge_already_sent),
                style = TanyTheme.typography.label,
                color = TanyTheme.colors.textMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (overdue) Text(stringResource(R.string.waiting_later_note), style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)
        TanyButton(stringResource(R.string.waiting_back_today), actions.finish, style = TanyButtonStyle.TEXT)
    }
}

/** The flow reached its end on a server read without a live confirmation (opened later): calm summary. */
@Composable
private fun DonePanel(booking: MerchantBookingDetail, actions: PickupActions) {
    TanyCard(contentPadding = 20.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TanyIllustration(DsR.drawable.ic_tany_check, tone = TanyTone.SUCCESS, size = 72.dp)
            Text(stringResource(booking.phase.flowDoneTitle()), style = TanyTheme.typography.title, textAlign = TextAlign.Center)
        }
        TanyButton(stringResource(R.string.flow_back_to_booking), actions.dismissCompleted, style = TanyButtonStyle.SECONDARY)
    }
}

private fun MerchantPhase.flowDoneTitle(): Int = when (this) {
    MerchantPhase.WITH_CUSTOMER, MerchantPhase.RETURN_DUE, MerchantPhase.RETURN_LATE -> R.string.success_pickup_title
    else -> R.string.success_return_title
}

/**
 * The customer just confirmed (a server timestamp appeared between two reads): short animated success with what
 * matters next (return date, unit), then « Terminer » back to Today.
 */
@Composable
internal fun SuccessView(step: CompletedStep, booking: MerchantBookingDetail, endpoint: ApiEndpoint, actions: PickupActions) {
    val formatters = LocalTanyFormatters.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(if (visible) 1f else 0.6f, label = "success-scale")
    val view = LocalView.current
    val currentStep by rememberUpdatedState(step)
    LaunchedEffect(currentStep) { view.confirmHaptic() }
    val title = when (step) {
        CompletedStep.PICKUP -> R.string.success_pickup_title
        CompletedStep.RETURN -> R.string.success_return_title
        CompletedStep.DEPOSIT -> R.string.success_deposit_title
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .navigationBarsPadding()
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        TanyIllustration(DsR.drawable.ic_tany_check, tone = TanyTone.SUCCESS, size = 112.dp, modifier = Modifier.scale(scale))
        Text(stringResource(title), style = TanyTheme.typography.largeTitle, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        TanyCard(contentPadding = 18.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductImageSurface(
                    url = endpoint.resolveMedia(booking.product.displayImage),
                    contentDescription = null,
                    modifier = Modifier.width(72.dp),
                    padding = 6.dp,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(booking.product.name, style = TanyTheme.typography.headline)
                    (booking.asset?.code ?: booking.assetCode)?.let { TanyCodePill(ltrIsolated(it)) }
                    Text(booking.customer.shortName, style = TanyTheme.typography.label, color = TanyTheme.colors.textMuted)
                }
            }
            when (step) {
                CompletedStep.PICKUP -> {
                    TanyInfoRow(stringResource(R.string.success_return_expected), icon = DsR.drawable.ic_tany_return) {
                        Text(
                            "${dayLabel(booking.returnDeadline)} · ${ltrIsolated(formatters.businessTime(booking.returnDeadline))}",
                            style = TanyTheme.typography.bodyStrong,
                        )
                    }
                    // Server fact: the deposit is now held at the counter.
                    booking.deposit?.heldAmount?.takeIf { !it.isZero }?.let {
                        Text(stringResource(R.string.success_deposit_held, formatters.money(it)), style = TanyTheme.typography.label)
                    }
                }
                CompletedStep.RETURN -> if (booking.asset?.status == AssetStatus.INSPECTION) {
                    Text(stringResource(R.string.success_inspection), style = TanyTheme.typography.label, color = TanyTheme.colors.warning.content)
                }
                CompletedStep.DEPOSIT -> Text(
                    stringResource(
                        R.string.success_deposit_message,
                        formatters.money(booking.deposit?.refundedAmount ?: MoneyAmount.ZERO),
                        booking.customer.shortName,
                    ),
                    style = TanyTheme.typography.label,
                )
            }
        }
        TanyButton(stringResource(R.string.success_finish), actions.finish)
        TanyButton(stringResource(R.string.flow_back_to_booking), actions.dismissCompleted, style = TanyButtonStyle.TEXT)
    }
}

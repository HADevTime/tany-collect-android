package ma.tany.collect.feature.scanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ma.tany.collect.R
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyIllustration
import ma.tany.core.designsystem.component.TanyInfoRow
import ma.tany.core.designsystem.component.TanyNotice
import ma.tany.core.designsystem.component.TanySegment
import ma.tany.core.designsystem.component.TanySegmentedControl
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.tanyFieldColors
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme

enum class CameraPermission { GRANTED, NOT_REQUESTED, DENIED, PERMANENTLY_DENIED }

private enum class ScanMode { CAMERA, MANUAL }

/**
 * A refused scan as the merchant sees it: title + message (localized from the server's structured code) and the
 * recovery offered. [preferCamera] = the manual code is not the way out (too many wrong codes).
 */
data class ScanFailureUi(
    val title: String,
    val message: String,
    @DrawableRes val icon: Int,
    val preferCamera: Boolean = false,
)

/**
 * Scanner: an immersive dark screen (both themes) built for the counter. Camera permission requested IN CONTEXT
 * (rationale first, settings when permanently denied), live QR preview in a framed viewfinder with an optional torch,
 * and a manual fallback that is always one tap away — the customer's 6-digit code ([ScanTarget.BOOKING_QR]) or the
 * printed label code ([ScanTarget.ASSET_LABEL]). Inputs are OPAQUE ([ScanInput]); the caller sends them to the
 * server, which decides. While [busy] or a [failure] is shown, the camera accepts nothing (one code per arming);
 * « Scanner à nouveau » ([onDismissFailure]) re-arms it.
 */
@Composable
fun ScannerScreen(
    onInput: (ScanInput) -> Unit = {},
    title: String? = null,
    target: ScanTarget = ScanTarget.BOOKING_QR,
    onBack: (() -> Unit)? = null,
    busy: Boolean = false,
    failure: ScanFailureUi? = null,
    onDismissFailure: () -> Unit = {},
    hint: String? = null,
    subHint: String? = null,
    pointName: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var permission by remember { mutableStateOf(context.cameraPermission(requestedBefore = false)) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var captured by remember { mutableStateOf<ScanInput?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(ScanMode.CAMERA) }
    var torch by rememberSaveable { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var cameraUnavailable by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requested = true
        permission = context.cameraPermission(requestedBefore = true)
    }
    // Re-check when returning from system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permission = context.cameraPermission(requestedBefore = requested) }
    LightStatusBarIcons()
    // Too many wrong codes: the QR is the way out.
    LaunchedEffect(failure) { if (failure?.preferCamera == true) mode = ScanMode.CAMERA }

    val labelMode = target == ScanTarget.ASSET_LABEL
    val accept: (ScanInput) -> Unit = { input ->
        if (!busy && failure == null) {
            view.tick()
            captured = input
            onInput(input)
        }
    }
    val submitManual: () -> Unit = {
        // Label codes are typed as printed and sent like a scanned label (opaque, trimmed).
        val input = if (labelMode) ScanInputs.fromCamera(code) else ScanInputs.shortCodeOrNull(code)
        if (input != null) accept(input)
    }
    val colors = TanyTheme.colors
    val cameraActive = mode == ScanMode.CAMERA && !busy && failure == null

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.chrome),
    ) {
        ScannerTopBar(
            title = title ?: stringResource(R.string.scan_title),
            onBack = onBack,
            pointName = pointName,
            torchVisible = mode == ScanMode.CAMERA && permission == CameraPermission.GRANTED && torchAvailable && !cameraUnavailable,
            torch = torch,
            onTorch = { torch = !torch },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    hint ?: stringResource(if (labelMode) R.string.op_scan_asset_hint else R.string.scan_instruction),
                    style = TanyTheme.typography.headline,
                    color = colors.onChrome,
                    textAlign = TextAlign.Center,
                )
                Text(
                    subHint ?: stringResource(if (labelMode) R.string.scan_label_example else R.string.scan_hint_default),
                    style = TanyTheme.typography.label,
                    color = colors.onChromeMuted,
                    textAlign = TextAlign.Center,
                )
            }
            TanySegmentedControl(
                options = listOf(
                    TanySegment(ScanMode.CAMERA, stringResource(R.string.scan_mode_camera), DsR.drawable.ic_tany_scan),
                    TanySegment(
                        ScanMode.MANUAL,
                        stringResource(if (labelMode) R.string.scan_mode_label else R.string.scan_mode_code),
                        DsR.drawable.ic_tany_keyboard,
                    ),
                ),
                selected = mode,
                onSelect = { mode = it },
                onChrome = true,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            if (failure != null) {
                ScanFailureCard(
                    failure = failure,
                    labelMode = labelMode,
                    onRetry = {
                        captured = null
                        onDismissFailure()
                    },
                    onManual = {
                        captured = null
                        onDismissFailure()
                        mode = ScanMode.MANUAL
                    },
                )
            }
            when {
                mode == ScanMode.MANUAL -> ManualEntry(
                    labelMode = labelMode,
                    code = code,
                    onCode = {
                        code = if (labelMode) it.take(ScanInputs.MAX_ASSET_CODE_LENGTH) else it.filter(Char::isDigit).take(ScanInputs.SHORT_CODE_LENGTH)
                    },
                    busy = busy,
                    onSubmit = submitManual,
                )
                permission == CameraPermission.GRANTED && cameraUnavailable -> CameraUnavailablePanel(onManual = { mode = ScanMode.MANUAL })
                permission == CameraPermission.GRANTED -> Viewfinder(
                    busy = busy,
                    active = cameraActive,
                    torch = torch && cameraActive,
                    onTorchAvailable = { torchAvailable = it },
                    onUnavailable = {
                        cameraUnavailable = true
                        mode = ScanMode.MANUAL
                    },
                    onCode = { raw -> ScanInputs.fromCamera(raw)?.let(accept) },
                )
                else -> PermissionPanel(
                    permission = permission,
                    onAllow = { launcher.launch(Manifest.permission.CAMERA) },
                    onSettings = { context.openAppSettings() },
                    onManual = { mode = ScanMode.MANUAL },
                    labelMode = labelMode,
                )
            }
            // Only a typed code is echoed back: a QR payload is an opaque token and is never displayed.
            (captured as? ScanInput.ShortCode)?.takeIf { busy }?.let { input ->
                TanyStatusChip(stringResource(R.string.scan_captured, ltrIsolated(input.code)), TanyTone.INFO)
            }
        }
    }
}

/** Refusal card (iOS « ScanFailureCard »): what happened, then « Scanner à nouveau » and the manual code. */
@Composable
private fun ScanFailureCard(failure: ScanFailureUi, labelMode: Boolean, onRetry: () -> Unit, onManual: () -> Unit) {
    TanyCard(modifier = Modifier.widthIn(max = 420.dp), contentPadding = 18.dp) {
        TanyNotice(title = failure.title, message = failure.message, tone = TanyTone.DANGER, icon = failure.icon)
        TanyButton(stringResource(R.string.scan_again), onRetry, icon = DsR.drawable.ic_tany_scan)
        if (!failure.preferCamera) {
            TanyButton(
                stringResource(if (labelMode) R.string.scan_type_label_instead else R.string.scan_type_code_instead),
                onManual,
                style = TanyButtonStyle.SECONDARY,
                icon = DsR.drawable.ic_tany_keyboard,
            )
        }
    }
}

@Composable
private fun CameraUnavailablePanel(onManual: () -> Unit) {
    TanyCard(modifier = Modifier.widthIn(max = 420.dp), contentPadding = 20.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TanyIllustration(DsR.drawable.ic_tany_camera, tone = TanyTone.WARNING, size = 80.dp)
            Text(stringResource(R.string.scan_camera_unavailable_title), style = TanyTheme.typography.title, textAlign = TextAlign.Center)
            Text(stringResource(R.string.scan_camera_unavailable_message), style = TanyTheme.typography.body, color = TanyTheme.colors.textMuted, textAlign = TextAlign.Center)
        }
        TanyButton(stringResource(R.string.scan_type_code_instead), onManual, icon = DsR.drawable.ic_tany_keyboard)
    }
}

/** Light « tick » on detection (API-level safe haptic). */
private fun View.tick() {
    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}

@Composable
private fun ScannerTopBar(title: String, onBack: (() -> Unit)?, pointName: String?, torchVisible: Boolean, torch: Boolean, onTorch: () -> Unit) {
    val colors = TanyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(painterResource(DsR.drawable.ic_tany_back), contentDescription = stringResource(R.string.scan_back), tint = colors.onChrome)
            }
        }
        Text(
            title,
            style = if (onBack == null) TanyTheme.typography.largeTitle else TanyTheme.typography.headline,
            color = colors.onChrome,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 16.dp else 4.dp)
                .semantics { heading() },
        )
        // Active point, always visible while scanning (a scan is scoped to this point).
        pointName?.let {
            Text(
                it,
                style = TanyTheme.typography.label,
                color = colors.onChrome,
                maxLines = 1,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clip(TanyTheme.radii.pill)
                    .background(colors.chromeRaised)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (torchVisible) {
            IconButton(onClick = onTorch) {
                Icon(
                    painterResource(DsR.drawable.ic_tany_bolt),
                    contentDescription = stringResource(if (torch) R.string.scan_torch_off else R.string.scan_torch_on),
                    tint = if (torch) colors.accent else colors.onChrome,
                )
            }
        }
    }
}

/** Live camera inside a rounded frame with viewfinder corners and a status pill. */
@Composable
private fun Viewfinder(
    busy: Boolean,
    active: Boolean,
    torch: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onUnavailable: () -> Unit,
    onCode: (String) -> Unit,
) {
    val colors = TanyTheme.colors
    val frameColor = if (busy) colors.accent else colors.onChrome
    Box(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(TanyTheme.radii.cardShape)
            .background(colors.chromeRaised),
    ) {
        CameraQrPreview(
            onCode = onCode,
            modifier = Modifier.fillMaxSize(),
            torchOn = torch,
            onTorchAvailable = onTorchAvailable,
            active = active,
            onUnavailable = onUnavailable,
        )
        Canvas(Modifier.fillMaxSize()) {
            val inset = size.minDimension * 0.16f
            val arm = size.minDimension * 0.12f
            val stroke = 4.dp.toPx()
            val l = inset
            val t = inset
            val r = size.width - inset
            val b = size.height - inset
            listOf(
                Triple(Offset(l, t), Offset(l + arm, t), Offset(l, t + arm)),
                Triple(Offset(r, t), Offset(r - arm, t), Offset(r, t + arm)),
                Triple(Offset(l, b), Offset(l + arm, b), Offset(l, b - arm)),
                Triple(Offset(r, b), Offset(r - arm, b), Offset(r, b - arm)),
            ).forEach { (corner, h, v) ->
                drawLine(frameColor, corner, h, strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(frameColor, corner, v, strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .clip(TanyTheme.radii.pill)
                .background(colors.chrome.copy(alpha = 0.78f))
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(14.dp), color = colors.onChrome, strokeWidth = 2.dp)
            Text(
                stringResource(
                    when {
                        busy -> R.string.scan_status_verifying
                        active -> R.string.scan_status_searching
                        else -> R.string.scan_status_paused
                    },
                ),
                style = TanyTheme.typography.label,
                color = colors.onChrome,
            )
        }
    }
}

@Composable
private fun PermissionPanel(
    permission: CameraPermission,
    onAllow: () -> Unit,
    onSettings: () -> Unit,
    onManual: () -> Unit,
    labelMode: Boolean,
) {
    TanyCard(modifier = Modifier.widthIn(max = 420.dp), contentPadding = 20.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TanyIllustration(DsR.drawable.ic_tany_camera, tone = if (permission == CameraPermission.NOT_REQUESTED) TanyTone.NEUTRAL else TanyTone.WARNING, size = 80.dp)
            Text(
                stringResource(R.string.scan_permission_title),
                style = TanyTheme.typography.title,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(
                    when (permission) {
                        CameraPermission.NOT_REQUESTED -> R.string.scan_permission_message
                        CameraPermission.PERMANENTLY_DENIED -> R.string.scan_permission_blocked
                        else -> R.string.scan_permission_denied
                    },
                ),
                style = TanyTheme.typography.body,
                color = TanyTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        TanyInfoRow(stringResource(R.string.scan_reassure_on_device), icon = DsR.drawable.ic_tany_lock) {}
        TanyInfoRow(stringResource(if (labelMode) R.string.scan_reassure_label else R.string.scan_reassure_code), icon = DsR.drawable.ic_tany_keyboard) {}
        if (permission == CameraPermission.PERMANENTLY_DENIED) {
            TanyButton(stringResource(R.string.scan_permission_settings), onSettings, icon = DsR.drawable.ic_tany_camera)
        } else {
            TanyButton(stringResource(R.string.scan_permission_allow), onAllow, icon = DsR.drawable.ic_tany_camera)
        }
        TanyButton(
            stringResource(if (labelMode) R.string.scan_type_label_instead else R.string.scan_type_code_instead),
            onManual,
            style = TanyButtonStyle.SECONDARY,
            icon = DsR.drawable.ic_tany_keyboard,
        )
    }
}

/** Manual fallback: 6 digit cells (customer code) or the printed label code. Always available. */
@Composable
private fun ManualEntry(labelMode: Boolean, code: String, onCode: (String) -> Unit, busy: Boolean, onSubmit: () -> Unit) {
    val ready = if (labelMode) code.isNotBlank() else code.length == ScanInputs.SHORT_CODE_LENGTH
    TanyCard(modifier = Modifier.widthIn(max = 420.dp), contentPadding = 20.dp) {
        Text(
            stringResource(if (labelMode) R.string.scan_label_fallback else R.string.scan_fallback_label),
            style = TanyTheme.typography.headline,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(if (labelMode) R.string.scan_label_fallback_hint else R.string.scan_fallback_hint),
            style = TanyTheme.typography.label,
            color = TanyTheme.colors.textMuted,
        )
        // Codes are LTR identifiers in every language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            if (labelMode) {
                OutlinedTextField(
                    value = code,
                    onValueChange = onCode,
                    singleLine = true,
                    textStyle = TanyTheme.typography.code,
                    colors = tanyFieldColors(),
                    shape = TanyTheme.radii.large,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ready && !busy) onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                CodeCells(code = code, onCode = onCode, onDone = { if (ready && !busy) onSubmit() })
            }
        }
        if (!labelMode) {
            TanyNotice(message = stringResource(R.string.scan_never_reference), tone = TanyTone.NEUTRAL, icon = DsR.drawable.ic_tany_shield)
        }
        TanyButton(
            stringResource(R.string.scan_fallback_submit),
            onSubmit,
            enabled = !busy && ready,
            loading = busy,
            loadingDescription = stringResource(R.string.scan_status_verifying),
        )
    }
}

/** Six large digit cells backed by one numeric field (paste-friendly, one semantic node for TalkBack). */
@Composable
private fun CodeCells(code: String, onCode: (String) -> Unit, onDone: () -> Unit) {
    val colors = TanyTheme.colors
    val description = stringResource(R.string.scan_fallback_label)
    BasicTextField(
        value = code,
        onValueChange = onCode,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        decorationBox = { _ ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(ScanInputs.SHORT_CODE_LENGTH) { index ->
                    val char = code.getOrNull(index)
                    val focused = index == code.length
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clip(TanyTheme.radii.medium)
                            .background(colors.neutral)
                            .border(
                                width = if (focused) 2.dp else 1.dp,
                                color = if (focused) colors.textPrimary else colors.border,
                                shape = TanyTheme.radii.medium,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(char?.toString().orEmpty(), style = TanyTheme.typography.codeLarge, color = colors.textPrimary)
                    }
                }
            }
        },
    )
}

/** The scanner canvas is dark in both themes: status bar icons must be light while it is shown. */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
}

private fun Context.cameraPermission(requestedBefore: Boolean): CameraPermission {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) return CameraPermission.GRANTED
    if (!requestedBefore) return CameraPermission.NOT_REQUESTED
    val activity = findActivity() ?: return CameraPermission.DENIED
    return if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
        CameraPermission.DENIED
    } else {
        CameraPermission.PERMANENTLY_DENIED
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

package ma.tany.collect.feature.scanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ma.tany.collect.R
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyIconContainer
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme

enum class CameraPermission { GRANTED, NOT_REQUESTED, DENIED, PERMANENTLY_DENIED }

/**
 * Scanner foundation: camera permission requested IN CONTEXT (rationale first, settings when permanently denied),
 * live QR preview, and the 6-digit fallback. Inputs are captured as [ScanInput]; sending them to
 * `POST /collect/scan` belongs to the pickup / return slices (not implemented here).
 */
@Composable
fun ScannerScreen(onInput: (ScanInput) -> Unit = {}) {
    val context = LocalContext.current
    var permission by remember { mutableStateOf(context.cameraPermission(requestedBefore = false)) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var captured by remember { mutableStateOf<ScanInput?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requested = true
        permission = context.cameraPermission(requestedBefore = true)
    }
    // Re-check when returning from system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permission = context.cameraPermission(requestedBefore = requested) }

    val accept: (ScanInput) -> Unit = { input ->
        captured = input
        onInput(input)
    }

    Column(Modifier.fillMaxSize()) {
        TanyTopBar(title = stringResource(R.string.scan_title), chrome = true)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (permission) {
                CameraPermission.GRANTED -> CameraQrPreview(
                    onCode = { raw -> ScanInputs.fromCamera(raw)?.let(accept) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(TanyTheme.radii.large),
                )
                else -> TanyCard {
                    TanyIconContainer(DsR.drawable.ic_tany_scan, contentDescription = null)
                    Text(stringResource(R.string.scan_permission_title), style = TanyTheme.typography.headline)
                    Text(
                        stringResource(
                            if (permission == CameraPermission.NOT_REQUESTED) R.string.scan_permission_message else R.string.scan_permission_denied,
                        ),
                        style = TanyTheme.typography.body,
                        color = TanyTheme.colors.textMuted,
                    )
                    if (permission == CameraPermission.PERMANENTLY_DENIED) {
                        TanyButton(stringResource(R.string.scan_permission_settings), { context.openAppSettings() }, style = TanyButtonStyle.SECONDARY)
                    } else {
                        TanyButton(stringResource(R.string.scan_permission_allow), { launcher.launch(Manifest.permission.CAMERA) })
                    }
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(ScanInputs.SHORT_CODE_LENGTH) },
                    label = { Text(stringResource(R.string.scan_fallback_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TanyButton(
                stringResource(R.string.scan_fallback_submit),
                { ScanInputs.shortCodeOrNull(code)?.let(accept) },
                enabled = code.length == ScanInputs.SHORT_CODE_LENGTH,
            )

            captured?.let { input ->
                val shown = when (input) {
                    is ScanInput.Qr -> input.payload.take(12) + "…"
                    is ScanInput.ShortCode -> input.code
                }
                TanyStatusChip(stringResource(R.string.scan_captured, ltrIsolated(shown)), TanyTone.INFO)
            }
        }
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

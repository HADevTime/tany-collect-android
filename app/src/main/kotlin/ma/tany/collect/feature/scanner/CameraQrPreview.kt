package ma.tany.collect.feature.scanner

import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Live camera preview that reports decoded QR codes (CameraX + ML Kit bundled barcode model: on-device, offline,
 * no image leaves the phone). Only call when the CAMERA permission is granted. Results are debounced and
 * delivered on the main thread; the payload is passed through untouched.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraQrPreview(
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    torchOn: Boolean = false,
    onTorchAvailable: (Boolean) -> Unit = {},
    active: Boolean = true,
    onUnavailable: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnTorchAvailable by rememberUpdatedState(onTorchAvailable)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val gate = remember { ScanGate() }
    SideEffect { gate.setActive(active) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        providerFuture.addListener(
            {
                val cameraProvider = runCatching { providerFuture.get() }.getOrNull()
                if (cameraProvider == null) {
                    currentOnUnavailable()
                    return@addListener
                }
                provider = cameraProvider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { proxy ->
                    val image = proxy.image
                    if (image == null) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { codes ->
                            codes.firstNotNullOfOrNull { it.rawValue }?.let { raw -> if (gate.accept(raw)) currentOnCode(raw) }
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                cameraProvider.unbindAll()
                // No back camera / camera held by another app: the manual code takes over (never a crash).
                val bound = runCatching {
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }.getOrNull()
                if (bound == null) {
                    currentOnUnavailable()
                    return@addListener
                }
                camera = bound
                currentOnTorchAvailable(bound.cameraInfo.hasFlashUnit())
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            camera = null
            provider?.unbindAll()
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    // Torch for dim counters (no-op on devices without a flash unit).
    LaunchedEffect(camera, torchOn) {
        camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.cameraControl?.enableTorch(torchOn)
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

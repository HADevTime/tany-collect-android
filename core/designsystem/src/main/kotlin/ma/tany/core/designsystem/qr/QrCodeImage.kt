package ma.tany.core.designsystem.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ma.tany.core.designsystem.theme.TanyTheme

/**
 * QR code drawn BLACK on the WHITE studio surface in both Light and Dark mode (scanners need dark-on-light; same
 * permanent rule as product images). Includes the 4-module quiet zone as padding.
 */
@Composable
fun QrCodeImage(payload: String, contentDescription: String, modifier: Modifier = Modifier) {
    val matrix = remember(payload) { QrMatrix.encode(payload) }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(TanyTheme.radii.large)
            .background(TanyTheme.colors.media)
            .padding(20.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Canvas(Modifier.aspectRatio(1f)) {
            val cell = size.minDimension / matrix.size
            for (y in 0 until matrix.size) {
                for (x in 0 until matrix.size) {
                    if (matrix[x, y]) {
                        // +0.5 px overlap avoids hairline gaps between modules on fractional densities.
                        drawRect(QrInk, topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.5f, cell + 0.5f))
                    }
                }
            }
        }
    }
}

private val QrInk = Color(0xFF000000)

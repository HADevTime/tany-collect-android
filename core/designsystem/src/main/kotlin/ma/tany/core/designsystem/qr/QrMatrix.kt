package ma.tany.core.designsystem.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Square module matrix of a QR code. The payload is OPAQUE (server-issued `<id>.<hmac>`, `TCR1…`): it is encoded
 * byte-for-byte, never parsed or transformed.
 */
class QrMatrix private constructor(val size: Int, private val modules: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    companion object {
        /** Medium error correction: robust to screen glare while keeping modules large on a phone screen. */
        fun encode(payload: String): QrMatrix {
            require(payload.isNotEmpty()) { "Empty QR payload" }
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 0,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            )
            // Size 0 ⇒ the writer returns the natural module grid (1 module = 1 cell).
            val bits = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, hints)
            val size = bits.width
            return QrMatrix(size, BooleanArray(size * size) { i -> bits.get(i % size, i / size) })
        }
    }
}

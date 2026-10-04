package ma.tany.core.designsystem

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import ma.tany.core.designsystem.qr.QrMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QrMatrixTest {
    /** Renders the matrix at 8 px / module with a 4-module quiet zone and decodes it back. */
    private fun decode(matrix: QrMatrix): String {
        val scale = 8
        val quiet = 4
        val side = (matrix.size + 2 * quiet) * scale
        val pixels = ByteArray(side * side) { 0xFF.toByte() }
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) if (matrix[x, y]) {
            for (dy in 0 until scale) for (dx in 0 until scale) {
                pixels[((y + quiet) * scale + dy) * side + (x + quiet) * scale + dx] = 0
            }
        }
        val source = object : LuminanceSource(side, side) {
            override fun getRow(y: Int, row: ByteArray?): ByteArray = pixels.copyOfRange(y * side, (y + 1) * side)
            override fun getMatrix(): ByteArray = pixels
        }
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    @Test
    fun opaqueServerPayloadsRoundTripExactly() {
        listOf(
            "6b1f8c2e-6a3e-4bd8-9a37-2f6d1c0f9e11.Hn3kQ_vZ8mW2pL9xR4tY7uB1cE5gJ0aS",
            "TCR1.eyJjIjoiY29sXzEyMyJ9.abcDEF-123_xyz",
        ).forEach { assertEquals(it, decode(QrMatrix.encode(it))) }
    }

    @Test
    fun emptyPayloadIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { QrMatrix.encode("") }
    }
}

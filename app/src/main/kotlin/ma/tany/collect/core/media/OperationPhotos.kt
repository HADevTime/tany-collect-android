package ma.tany.collect.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Capture + encoding of operation photos (pickup / return). Faked in ViewModel tests. */
interface OperationPhotos {
    fun newCaptureFile(): File

    /** FileProvider uri for the system camera (private cache; nothing in the gallery). */
    fun uriFor(file: File): Uri

    /** EXIF-upright JPEG, longest side ≤ [OperationImageMath.MAX_SIDE], ≤ [OperationImageMath.MAX_BYTES] (server limit 15 MB). */
    suspend fun encode(file: File): ByteArray

    fun discard(file: File)
}

/** Pure sizing rules (JVM-tested). */
object OperationImageMath {
    const val MAX_SIDE = 2048
    const val MAX_BYTES = 1_500_000
    val QUALITIES = intArrayOf(85, 75, 65)

    fun inSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    fun fit(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxSide) return width to height
        val ratio = maxSide.toDouble() / longest
        return max(1, (width * ratio).roundToInt()) to max(1, (height * ratio).roundToInt())
    }

    fun rotationDegrees(exifOrientation: Int): Int = when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
        ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
        else -> 0
    }
}

class AndroidOperationPhotos(private val files: OperationPhotoFiles) : OperationPhotos {
    override fun newCaptureFile(): File = files.newCaptureFile().first

    override fun uriFor(file: File): Uri = files.uriFor(file)

    override suspend fun encode(file: File): ByteArray = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = OperationImageMath.inSampleSize(bounds.outWidth, bounds.outHeight, OperationImageMath.MAX_SIDE)
        }
        var bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, options)) { "Not an image" }
        val orientation = runCatching {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = OperationImageMath.rotationDegrees(orientation)
        if (degrees != 0) {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        val (w, h) = OperationImageMath.fit(bitmap.width, bitmap.height, OperationImageMath.MAX_SIDE)
        if (w != bitmap.width || h != bitmap.height) {
            val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }
        try {
            var bytes = ByteArray(0)
            for (quality in OperationImageMath.QUALITIES) {
                bytes = ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    out.toByteArray()
                }
                if (bytes.size <= OperationImageMath.MAX_BYTES) break
            }
            bytes
        } finally {
            bitmap.recycle()
        }
    }

    override fun discard(file: File) = files.delete(file)
}

package ma.tany.collect.core.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Secure capture target for mandatory operation photos (pickup / return, 1–3 per gesture):
 * - files live in the app's private cache (`cache/photos/`), exposed only through this app's FileProvider
 *   (no storage permission, nothing in the gallery);
 * - uploaded as JPEG multipart (`photo`, `purpose`, `condition`, `collectPointId`, ≤ 15 MB) by the pickup/return
 *   slices, then deleted.
 * Note: because the app declares CAMERA (scanner), a system camera intent also needs that permission granted.
 */
class OperationPhotoFiles(private val context: Context) {
    private val directory: File get() = File(context.cacheDir, "photos").apply { mkdirs() }

    fun newCaptureFile(): Pair<File, Uri> {
        val file = File(directory, "op-${UUID.randomUUID()}.jpg")
        return file to uriFor(file)
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)

    fun delete(file: File) {
        file.delete()
    }

    /** Called on logout: no operation photo survives the session on the device. */
    fun clearAll() {
        directory.listFiles()?.forEach { it.delete() }
    }
}

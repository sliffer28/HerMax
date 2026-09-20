package com.hermes.client.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import com.hermes.client.domain.model.Attachment
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID

object FileAttachmentHelper {

    const val MAX_FILE_SIZE_BYTES: Long = 25 * 1024 * 1024 // 25 MB
    private const val MAX_VISION_DIMENSION = 2048

    /**
     * Creates an Attachment from an Android URI:
     * 1. Detects filename, MIME type, and size.
     * 2. Safely attempts to take persistable URI read permission.
     * 3. Copies the file stream into the app's private files directory so it remains permanently readable.
     */
    fun createAttachmentFromUri(context: Context, uri: Uri): Result<Attachment> = runCatching {
        // Attempt to persist URI permission if supported by the provider
        runCatching {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        }

        val fileName = getFileName(context, uri)
        val mimeType = getMimeType(context, uri, fileName)
        val size = getFileSize(context, uri)

        if (size > MAX_FILE_SIZE_BYTES) {
            throw IllegalArgumentException(
                "File \"$fileName\" (${formatFileSize(size)}) exceeds maximum allowed size of ${formatFileSize(MAX_FILE_SIZE_BYTES)}."
            )
        }

        val attachmentId = UUID.randomUUID().toString()

        // Copy file to app's internal private storage for guaranteed permanent access
        val privateCopyPath = copyToPrivateStorage(context, uri, attachmentId, fileName)

        Attachment(
            id = attachmentId,
            fileName = fileName,
            mimeType = mimeType,
            size = size,
            uri = uri.toString(),
            localPath = privateCopyPath,
            uploadProgress = 0f,
            isUploaded = false
        )
    }

    private fun copyToPrivateStorage(context: Context, uri: Uri, id: String, fileName: String): String? {
        return try {
            val attachmentsDir = File(context.filesDir, "attachments").apply { if (!exists()) mkdirs() }
            val cleanName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val targetFile = File(attachmentsDir, "${id}_$cleanName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0) {
                targetFile.absolutePath
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun getFileName(context: Context, uri: Uri): String {
        var name = "unknown_file"
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val retrievedName = cursor.getString(nameIndex)
                    if (!retrievedName.isNullOrBlank()) {
                        name = retrievedName
                    }
                }
            }
        } else {
            name = uri.lastPathSegment ?: "file"
        }
        return name
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        var size: Long = 0
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        if (size <= 0) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    size = stream.available().toLong()
                }
            } catch (_: Exception) {}
        }
        return size
    }

    fun getMimeType(context: Context, uri: Uri, fileName: String): String {
        var mimeType = context.contentResolver.getType(uri)
        if (mimeType.isNullOrBlank() || mimeType == "application/octet-stream") {
            val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (extension.isNotEmpty()) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            }
        }
        return mimeType ?: "application/octet-stream"
    }

    fun openInputStream(context: Context?, attachment: Attachment): InputStream? {
        // 1. Try local file path first
        attachment.localPath?.let { path ->
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return file.inputStream()
            }
        }
        // 2. Fall back to content resolver
        if (context != null && attachment.uri != null) {
            return try {
                context.contentResolver.openInputStream(Uri.parse(attachment.uri))
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    fun readFileBytes(context: Context?, attachment: Attachment): ByteArray? {
        return try {
            openInputStream(context, attachment)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }

    fun readFileBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }

    fun readTextContent(context: Context?, attachment: Attachment, maxChars: Int = 100000): String? {
        return try {
            openInputStream(context, attachment)?.bufferedReader()?.use { reader ->
                val chars = CharArray(maxChars)
                val read = reader.read(chars, 0, maxChars)
                if (read > 0) String(chars, 0, read) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun readTextContent(context: Context, uri: Uri, maxChars: Int = 50000): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val chars = CharArray(maxChars)
                val read = reader.read(chars, 0, maxChars)
                if (read > 0) String(chars, 0, read) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads and safely encodes an image attachment to base64 for Vision APIs.
     * - Downsamples images exceeding [maxDimension] to avoid high-resolution OOM.
     * - Preserves EXIF rotation orientation.
     * - Compresses to JPEG/PNG.
     * - Returns Pair<MimeType, Base64String>.
     */
    fun readBase64ForVision(
        context: Context?,
        attachment: Attachment,
        maxDimension: Int = MAX_VISION_DIMENSION
    ): Pair<String, String>? {
        return try {
            // First pass: decode bounds
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openInputStream(context, attachment)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            } ?: return null

            val srcWidth = boundsOpts.outWidth
            val srcHeight = boundsOpts.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            // Calculate sample size
            var sampleSize = 1
            while ((srcWidth / sampleSize) > maxDimension || (srcHeight / sampleSize) > maxDimension) {
                sampleSize *= 2
            }

            // Second pass: decode bitmap
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decodedBitmap = openInputStream(context, attachment)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            // Read EXIF orientation
            val orientation = getExifOrientation(context, attachment)
            val finalBitmap = if (orientation != 0) {
                val matrix = Matrix().apply { postRotate(orientation.toFloat()) }
                val rotated = Bitmap.createBitmap(
                    decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true
                )
                if (rotated != decodedBitmap) {
                    decodedBitmap.recycle()
                }
                rotated
            } else {
                decodedBitmap
            }

            // Compress to stream
            val outputStream = ByteArrayOutputStream()
            val isPng = attachment.mimeType.contains("png", ignoreCase = true)
            val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            finalBitmap.compress(format, 85, outputStream)
            finalBitmap.recycle()

            val bytes = outputStream.toByteArray()
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val effectiveMime = if (isPng) "image/png" else "image/jpeg"

            Pair(effectiveMime, base64Data)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads raw base64 data directly from an attachment without re-encoding (for PDFs/documents).
     */
    fun readRawBase64(context: Context?, attachment: Attachment): Pair<String, String>? {
        return try {
            val bytes = readFileBytes(context, attachment) ?: return null
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Pair(attachment.mimeType, base64Data)
        } catch (_: Exception) {
            null
        }
    }

    private fun getExifOrientation(context: Context?, attachment: Attachment): Int {
        return try {
            val exif = if (attachment.localPath != null) {
                ExifInterface(attachment.localPath)
            } else if (context != null && attachment.uri != null) {
                context.contentResolver.openInputStream(Uri.parse(attachment.uri))?.use {
                    ExifInterface(it)
                } ?: return 0
            } else return 0

            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    fun isImageMime(mimeType: String): Boolean {
        return mimeType.startsWith("image/", ignoreCase = true)
    }

    fun isPdf(mimeType: String, fileName: String = ""): Boolean {
        if (mimeType.equals("application/pdf", ignoreCase = true)) return true
        return fileName.endsWith(".pdf", ignoreCase = true)
    }

    fun isTextMime(mimeType: String, fileName: String): Boolean {
        if (mimeType.startsWith("text/", ignoreCase = true)) return true
        if (mimeType.contains("json", ignoreCase = true)) return true
        if (mimeType.contains("xml", ignoreCase = true)) return true
        if (mimeType.contains("csv", ignoreCase = true)) return true
        val textExtensions = setOf(
            "txt", "md", "csv", "json", "xml", "html", "css", "js", "ts",
            "kt", "java", "py", "sh", "sql", "yaml", "yml", "log"
        )
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return textExtensions.contains(ext)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return String.format(Locale.getDefault(), "%.1f %s", size, units[unitIndex])
    }
}

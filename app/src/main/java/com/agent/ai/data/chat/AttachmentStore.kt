package com.agent.ai.data.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the composer holds before a message is sent. */
data class AttachmentDraft(
    val id: String,
    val part: MessagePart,
)

/**
 * Attachments live in the app's private folder. Images are resized to at most 2048 px on
 * the long side and re-encoded, which also drops every EXIF tag including the location.
 */
class AttachmentStore(private val context: Context) {

    private val root: File
        get() = File(context.filesDir, "attachments").apply { mkdirs() }

    suspend fun importImage(uri: Uri): Result<MessagePart.Image> = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(uri) ?: "image"
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                error("This image could not be read.")
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: error("This image could not be read.")

            val scaled = scaleDown(decoded)
            val rotation = rotationOf(uri)
            val final = if (rotation != 0f) {
                Bitmap.createBitmap(
                    scaled,
                    0,
                    0,
                    scaled.width,
                    scaled.height,
                    Matrix().apply { postRotate(rotation) },
                    true,
                ).also { if (it !== scaled) scaled.recycle() }
            } else {
                scaled
            }

            val file = File(root, "${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { out ->
                final.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            val width = final.width
            val height = final.height
            final.recycle()

            MessagePart.Image(
                path = file.absolutePath,
                name = name,
                mime = "image/jpeg",
                width = width,
                height = height,
            )
        }
    }

    suspend fun importText(uri: Uri): Result<MessagePart.File> = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(uri) ?: "document.txt"
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(MAX_TEXT_BYTES)
                var read = 0
                while (read < buffer.size) {
                    val step = input.read(buffer, read, buffer.size - read)
                    if (step <= 0) break
                    read += step
                }
                String(buffer, 0, read, Charsets.UTF_8)
            } ?: error("This file could not be read.")
            if (text.isBlank()) error("This file is empty.")
            saveText(name, "text/plain", text)
        }
    }

    suspend fun importPdf(uri: Uri): Result<MessagePart.File> = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(uri) ?: "document.pdf"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("This PDF could not be read.")
            val text = PdfTextExtractor.extract(bytes)
            if (text.isBlank()) {
                error("No text found in this PDF. It may be a scan.")
            }
            saveText(name, "application/pdf", text)
        }
    }

    private fun saveText(name: String, mime: String, text: String): MessagePart.File {
        val file = File(root, "${UUID.randomUUID()}.txt")
        file.writeText(text)
        return MessagePart.File(
            path = file.absolutePath,
            name = name,
            mime = mime,
            size = text.toByteArray().size.toLong(),
            extracted = text,
        )
    }

    /** Removes the files a conversation's messages point at. */
    fun deleteFilesFor(parts: List<MessagePart>) {
        parts.forEach { part ->
            when (part) {
                is MessagePart.Image -> runCatching { File(part.path).delete() }
                is MessagePart.File -> runCatching { File(part.path).delete() }
                else -> Unit
            }
        }
    }

    /** Bytes of an image for a vision request, as a `data:` URL. */
    fun dataUrl(part: MessagePart.Image): String? = runCatching {
        val bytes = File(part.path).readBytes()
        "data:${part.mime};base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }.getOrNull()

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull() ?: uri.lastPathSegment

    private fun rotationOf(uri: Uri): Float = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_SIDE * 2 || height / sample > MAX_SIDE * 2) sample *= 2
        return sample
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val long = maxOf(bitmap.width, bitmap.height)
        if (long <= MAX_SIDE) return bitmap
        val ratio = MAX_SIDE.toFloat() / long
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    companion object {
        const val MAX_SIDE = 2048
        const val MAX_TEXT_BYTES = 200 * 1024
        const val MAX_ATTACHMENTS = 5
        const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024
    }
}

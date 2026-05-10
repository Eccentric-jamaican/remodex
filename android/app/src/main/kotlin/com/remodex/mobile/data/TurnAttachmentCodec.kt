package com.remodex.mobile.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.Base64
import com.remodex.mobile.core.model.CodexImageAttachment
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

internal object TurnAttachmentCodec {
    private const val MAX_PAYLOAD_DIMENSION = 1600
    private const val THUMBNAIL_SIDE = 70
    private const val JPEG_QUALITY = 80
    private const val SAVED_PREVIEW_MAX_DIMENSION = 1080
    private const val SAVED_PREVIEW_MAX_BYTES = 1024 * 1024
    private const val SAVED_THUMBNAIL_SIDE = 256
    private const val SAVED_THUMBNAIL_QUALITY = 70
    private val savedPreviewDimensions = listOf(SAVED_PREVIEW_MAX_DIMENSION, 900, 720, 540, 360)
    private val savedPreviewQualities = listOf(75, 65, 55, 45)

    fun makeAttachment(
        context: Context,
        uri: Uri,
    ): CodexImageAttachment? {
        val sourceData =
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: return null
        return makeAttachment(sourceData, sourceUrl = uri.toString())
    }

    fun makeAttachment(
        sourceData: ByteArray,
        sourceUrl: String? = null,
    ): CodexImageAttachment? {
        val normalizedPayload =
            normalizePayloadJpeg(
                sourceData = sourceData,
                maxDimension = MAX_PAYLOAD_DIMENSION,
                quality = JPEG_QUALITY,
            ) ?: return null
        val thumbnailBase64 = makeThumbnailBase64JPEG(normalizedPayload) ?: return null
        val payloadDataUrl = "data:image/jpeg;base64,${Base64.encodeToString(normalizedPayload, Base64.NO_WRAP)}"
        return CodexImageAttachment(
            thumbnailBase64JPEG = thumbnailBase64,
            payloadDataURL = payloadDataUrl,
            sourceURL = sourceUrl,
        )
    }

    fun attachmentFromHistorySource(sourceUrl: String?): CodexImageAttachment? {
        val normalizedSource = sourceUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val payloadDataUrl =
            if (normalizedSource.startsWith("data:image", ignoreCase = true)) {
                normalizedSource
            } else {
                null
            }
        val thumbnailBase64 =
            payloadDataUrl
                ?.let(::decodeDataUriImageData)
                ?.let(::makeThumbnailBase64JPEG)
                .orEmpty()
        return CodexImageAttachment(
            thumbnailBase64JPEG = thumbnailBase64,
            payloadDataURL = payloadDataUrl,
            sourceURL = normalizedSource,
        )
    }

    fun makeSavedPreviewDataURL(payloadDataUrl: String?): String? {
        val sourceData = payloadDataUrl?.let(::decodeDataUriImageData) ?: return null
        for (dimension in savedPreviewDimensions) {
            for (quality in savedPreviewQualities) {
                val preview =
                    normalizePayloadJpeg(
                        sourceData = sourceData,
                        maxDimension = dimension,
                        quality = quality,
                    ) ?: continue
                if (preview.size <= SAVED_PREVIEW_MAX_BYTES) {
                    return "data:image/jpeg;base64,${Base64.encodeToString(preview, Base64.NO_WRAP)}"
                }
            }
        }
        return null
    }

    fun makeSavedThumbnailBase64JPEG(payloadDataUrl: String?): String? =
        payloadDataUrl
            ?.let(::decodeDataUriImageData)
            ?.let { imageData ->
                makeThumbnailBase64JPEG(
                    imageData = imageData,
                    side = SAVED_THUMBNAIL_SIDE,
                    quality = SAVED_THUMBNAIL_QUALITY,
                )
            }

    fun isSavedPreviewDataURLWithinLimit(payloadDataUrl: String): Boolean {
        val commaIndex = payloadDataUrl.indexOf(',')
        if (commaIndex <= 0) return false
        val metadata = payloadDataUrl.substring(0, commaIndex).lowercase()
        if (!metadata.startsWith("data:image") || !metadata.contains(";base64")) return false
        val base64Length = payloadDataUrl.length - commaIndex - 1
        val estimatedBytes = (base64Length * 3L) / 4L
        return estimatedBytes <= SAVED_PREVIEW_MAX_BYTES
    }

    fun decodeDataUriImageData(dataUri: String): ByteArray? {
        val commaIndex = dataUri.indexOf(',')
        if (commaIndex <= 0) return null
        val metadata = dataUri.substring(0, commaIndex).lowercase()
        if (!metadata.startsWith("data:image") || !metadata.contains(";base64")) return null
        val base64Part = dataUri.substring(commaIndex + 1)
        return runCatching { Base64.decode(base64Part, Base64.DEFAULT) }.getOrNull()
    }

    private fun normalizePayloadJpeg(
        sourceData: ByteArray,
        maxDimension: Int,
        quality: Int,
    ): ByteArray? {
        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
        BitmapFactory.decodeByteArray(sourceData, 0, sourceData.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            }
        val decoded = BitmapFactory.decodeByteArray(sourceData, 0, sourceData.size, decodeOptions) ?: return null

        val longestSide = max(decoded.width, decoded.height)
        val scaled =
            if (longestSide <= maxDimension) {
                decoded
            } else {
                val scale = maxDimension.toFloat() / longestSide.toFloat()
                val targetWidth = max(1, (decoded.width * scale).roundToInt())
                val targetHeight = max(1, (decoded.height * scale).roundToInt())
                Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true).also {
                    if (it != decoded) decoded.recycle()
                }
            }

        return scaled.compressToJpeg(quality).also {
            scaled.recycle()
        }
    }

    private fun makeThumbnailBase64JPEG(
        imageData: ByteArray,
        side: Int = THUMBNAIL_SIDE,
        quality: Int = JPEG_QUALITY,
    ): String? {
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size) ?: return null
        val thumbnail = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(thumbnail)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scale =
            max(
                side.toFloat() / bitmap.width.toFloat(),
                side.toFloat() / bitmap.height.toFloat(),
            )
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale
        val left = ((side - scaledWidth) / 2f).roundToInt()
        val top = ((side - scaledHeight) / 2f).roundToInt()
        val destination = Rect(left, top, left + scaledWidth.roundToInt(), top + scaledHeight.roundToInt())
        canvas.drawBitmap(bitmap, null, destination, paint)
        bitmap.recycle()
        val jpegData =
            thumbnail.compressToJpeg(quality).also {
                thumbnail.recycle()
            } ?: return null
        return Base64.encodeToString(jpegData, Base64.NO_WRAP)
    }

    private fun Bitmap.compressToJpeg(quality: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        if (!compress(Bitmap.CompressFormat.JPEG, quality, out)) {
            return null
        }
        return out.toByteArray()
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int,
    ): Int {
        var sampleSize = 1
        while (max(width / (sampleSize * 2), height / (sampleSize * 2)) >= maxDimension) {
            sampleSize *= 2
        }
        return max(1, sampleSize)
    }
}

package com.remodex.mobile.data

import com.remodex.mobile.core.model.CodexImageAttachment
import com.remodex.mobile.core.model.ImagePreviewRetentionPreference

internal object ImagePreviewRetentionPolicy {
    const val ELIDED_HISTORY_IMAGE_URL = "remodex://history-image-elided"

    fun retainedAttachment(
        attachment: CodexImageAttachment,
        retention: ImagePreviewRetentionPreference,
        generateLargerPreview: Boolean = true,
    ): CodexImageAttachment =
        when (retention) {
            ImagePreviewRetentionPreference.storageSaver ->
                attachment.copy(
                    thumbnailBase64JPEG = "",
                    payloadDataURL = null,
                    sourceURL = ELIDED_HISTORY_IMAGE_URL,
                )
            ImagePreviewRetentionPreference.smallPreviews ->
                attachment.copy(
                    thumbnailBase64JPEG = attachment.retainedThumbnailBase64(),
                    payloadDataURL = null,
                )
            ImagePreviewRetentionPreference.largerPreviews -> {
                val savedPreview =
                    if (generateLargerPreview) {
                        TurnAttachmentCodec.makeSavedPreviewDataURL(attachment.payloadDataURL)
                    } else {
                        attachment.payloadDataURL
                            ?.takeIf(TurnAttachmentCodec::isSavedPreviewDataURLWithinLimit)
                    }
                attachment.copy(
                    thumbnailBase64JPEG = attachment.retainedThumbnailBase64(savedPreview),
                    payloadDataURL = savedPreview,
                )
            }
        }

    private fun CodexImageAttachment.retainedThumbnailBase64(
        preferredPayloadDataUrl: String? = payloadDataURL,
    ): String =
        TurnAttachmentCodec.makeSavedThumbnailBase64JPEG(preferredPayloadDataUrl)
            ?: thumbnailBase64JPEG.takeIf { it.isNotBlank() }
            ?: ""
}

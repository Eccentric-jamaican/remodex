package com.remodex.mobile.core.model

enum class ImagePreviewRetentionPreference {
    storageSaver,
    smallPreviews,
    largerPreviews,
    ;

    companion object {
        const val storageKey: String = "remodex.imagePreviewRetention"

        val default: ImagePreviewRetentionPreference = smallPreviews

        fun fromStorage(raw: String?): ImagePreviewRetentionPreference =
            if (raw.isNullOrBlank()) {
                default
            } else {
                runCatching { valueOf(raw) }.getOrDefault(default)
            }
    }
}

package com.remodex.mobile.data

import android.content.Context
import com.remodex.mobile.core.model.ImagePreviewRetentionPreference

object ImagePreviewPreferences {
    private const val PREFS_NAME = "remodex_ui"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(context: Context): ImagePreviewRetentionPreference {
        val raw = prefs(context).getString(ImagePreviewRetentionPreference.storageKey, null)
        return ImagePreviewRetentionPreference.fromStorage(raw)
    }

    fun write(
        context: Context,
        preference: ImagePreviewRetentionPreference,
    ) {
        prefs(context).edit().putString(ImagePreviewRetentionPreference.storageKey, preference.name).apply()
    }
}

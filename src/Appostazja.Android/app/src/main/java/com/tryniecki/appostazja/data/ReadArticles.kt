package com.tryniecki.appostazja.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/** Ids of the news articles opened on this device, kept between launches. */
class ReadArticles(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val readIds = MutableStateFlow(preferences.getStringSet(KEY_READ_IDS, null).orEmpty().toSet())

    val ids: StateFlow<Set<String>> = readIds.asStateFlow()

    fun markRead(id: String) {
        if (id in readIds.value) return
        val updated = readIds.updateAndGet { it + id }
        preferences.edit { putStringSet(KEY_READ_IDS, updated) }
    }

    private companion object {
        const val PREFERENCES_NAME = "news"
        const val KEY_READ_IDS = "read_article_ids"
    }
}

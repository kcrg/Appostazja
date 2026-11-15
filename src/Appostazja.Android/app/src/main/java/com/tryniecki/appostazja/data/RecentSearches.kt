package com.tryniecki.appostazja.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.json.Json

/** The last place searches used on this device, newest first, kept between launches. */
class RecentSearches(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val phrases = MutableStateFlow(read())

    val recent: StateFlow<List<String>> = phrases.asStateFlow()

    fun add(phrase: String) = update { withRecentSearch(it, phrase) }

    fun remove(phrase: String) = update { list -> list.filterNot { it == phrase } }

    private fun update(change: (List<String>) -> List<String>) {
        val before = phrases.value
        val after = phrases.updateAndGet(change)
        if (after != before) preferences.edit { putString(KEY_PHRASES, Json.encodeToString(after)) }
    }

    private fun read(): List<String> =
        preferences.getString(KEY_PHRASES, null)
            ?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() }
            .orEmpty()

    private companion object {
        const val PREFERENCES_NAME = "search"
        const val KEY_PHRASES = "recent_phrases"
    }
}

/** How many searches are remembered. */
internal const val RECENT_SEARCH_LIMIT = 3

/**
 * [recent] with [phrase] put first. Blank phrases are ignored; the phrase is trimmed with inner whitespace
 * collapsed, and an earlier entry differing only in letter case is replaced by it.
 */
internal fun withRecentSearch(recent: List<String>, phrase: String): List<String> {
    val normalized = phrase.trim().replace(WHITESPACE, " ")
    if (normalized.isEmpty()) return recent
    return (listOf(normalized) + recent.filterNot { it.equals(normalized, ignoreCase = true) })
        .take(RECENT_SEARCH_LIMIT)
}

private val WHITESPACE = Regex("""\s+""")

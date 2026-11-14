package com.tryniecki.appostazja.ui.declaration

import com.tryniecki.appostazja.api.model.PlaceDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The two parish fields of the declaration a church picked on the map can fill. */
enum class ParishField { BAPTISM, RESIDENCE }

/** A church picked on the map for one of the declaration's parish fields. */
data class ParishPick(val field: ParishField, val churchName: String, val text: String)

/**
 * Carries a church picked on the map to the declaration form. The form may not exist yet (its tab was never
 * opened) or may still be restoring the draft, so the pick waits here until the form takes it, exactly once.
 * A newer pick replaces one that was never taken.
 */
class ParishHandoff {
    private val _pending = MutableStateFlow<ParishPick?>(null)
    val pending: StateFlow<ParishPick?> = _pending.asStateFlow()

    fun send(place: PlaceDto, field: ParishField) {
        _pending.value = ParishPick(field, place.name.trim(), parishText(place.name, place.address))
    }

    /** True for the one caller that gets to apply [pick]; false when it was already taken or replaced. */
    fun take(pick: ParishPick): Boolean = _pending.compareAndSet(pick, null)
}

/**
 * What goes into the form: the field asks for the parish and its address, and the declaration is addressed
 * with it. The map knows the church, which is what people recognise, so its name and address are used and the
 * user can still edit them.
 */
internal fun parishText(name: String, address: String): String {
    val cleanName = name.trim()
    val cleanAddress = address.trim()
    val text = when {
        cleanAddress.isEmpty() -> cleanName
        cleanName.isEmpty() -> cleanAddress
        // Some names already end with the address; do not repeat it.
        cleanName.contains(cleanAddress, ignoreCase = true) -> cleanName
        else -> "$cleanName, $cleanAddress"
    }
    return text.take(PARISH_MAX_LENGTH)
}

internal const val PARISH_MAX_LENGTH = 300

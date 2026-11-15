package com.tryniecki.appostazja.ui.declaration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tryniecki.appostazja.pdf.ApostasyDeclaration
import com.tryniecki.appostazja.security.DeclarationDraft
import com.tryniecki.appostazja.security.SecureDraftStore
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the baptism date starts before anything is picked: a plausible date, so the picker opens near it. */
internal val DEFAULT_BAPTISM_DATE: LocalDate = LocalDate.of(2000, 1, 20)

internal data class DeclarationUiState(
    val isLoaded: Boolean = false,
    val fullName: String = "",
    val baptismDate: LocalDate = DEFAULT_BAPTISM_DATE,
    val baptismParish: String = "",
    val residenceParish: String = "",
    /** The declaration is submitted to the parish of baptism; the residence field is not used then. */
    val residenceSameAsBaptism: Boolean = false,
    val homeAddress: String = "",
    val motivation: String = "",
) {
    /** Where the declaration is submitted. */
    val effectiveResidenceParish: String
        get() = if (residenceSameAsBaptism) baptismParish else residenceParish

    val canGenerate: Boolean
        get() = isLoaded &&
            fullName.isNotBlank() &&
            homeAddress.isNotBlank() &&
            baptismParish.isNotBlank() &&
            effectiveResidenceParish.isNotBlank() &&
            motivation.isNotBlank() &&
            !baptismDate.isAfter(LocalDate.now())
}

/** A church from the map written into the form, with what the field held before so it can be undone. */
internal data class AppliedParish(val pick: ParishPick, val previous: String)

internal class DeclarationViewModel(
    private val store: SecureDraftStore,
    private val handoff: ParishHandoff,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeclarationUiState())
    val uiState: StateFlow<DeclarationUiState> = _uiState.asStateFlow()
    private val _appliedParish = MutableStateFlow<AppliedParish?>(null)
    val appliedParish: StateFlow<AppliedParish?> = _appliedParish.asStateFlow()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val restored = store.load()
            _uiState.value = restored?.toUiState() ?: DeclarationUiState(isLoaded = true)
            // Only after the draft is restored, or the restored draft would overwrite the picked church.
            handoff.pending.filterNotNull().collect { pick ->
                if (handoff.take(pick)) applyParish(pick)
            }
        }
    }

    private fun applyParish(pick: ParishPick) {
        val previous = _uiState.value.parish(pick.field)
        mutate { withParish(pick.field, pick.text) }
        _appliedParish.value = AppliedParish(pick, previous)
    }

    fun undoParish(applied: AppliedParish) {
        // Only while the field still holds the picked church; a later edit by hand wins.
        if (_uiState.value.parish(applied.pick.field) == applied.pick.text) {
            mutate { withParish(applied.pick.field, applied.previous) }
        }
        parishMessageShown(applied)
    }

    fun parishMessageShown(applied: AppliedParish) {
        _appliedParish.compareAndSet(applied, null)
    }

    fun updateFullName(value: String) = mutate { copy(fullName = value.take(200)) }
    fun updateHomeAddress(value: String) = mutate { copy(homeAddress = value.take(300)) }
    fun updateBaptismDate(value: LocalDate) = mutate {
        copy(baptismDate = value.coerceAtMost(LocalDate.now()))
    }
    fun updateBaptismParish(value: String) = mutate { copy(baptismParish = value.take(300)) }
    fun updateResidenceParish(value: String) = mutate { copy(residenceParish = value.take(300)) }
    fun updateResidenceSameAsBaptism(value: Boolean) = mutate { copy(residenceSameAsBaptism = value) }
    fun updateMotivation(value: String) = mutate { copy(motivation = value.take(MOTIVATION_MAX_LENGTH)) }
    /** [preset] is the chosen ready-made reason, [presets] all of them, as the screen shows them. */
    fun applyMotivationPreset(preset: String, presets: Collection<String>) = mutate {
        copy(motivation = withMotivationPreset(motivation, preset, presets).take(MOTIVATION_MAX_LENGTH))
    }

    /** Sample data for testing the form and the PDF; offered only in test builds. */
    fun fillSample() = mutate {
        copy(
            fullName = "Jan Kowalski",
            homeAddress = "ul. Przykładowa 12/3, 00-001 Warszawa",
            baptismDate = LocalDate.of(1995, 5, 14),
            baptismParish = "Parafia Rzymskokatolicka pw. św. Anny w Warszawie",
            residenceParish = "Parafia Rzymskokatolicka pw. Najświętszego Zbawiciela w Warszawie",
            motivation = "Nie identyfikuję się z Kościołem katolickim i nie podzielam jego nauczania.",
        )
    }

    fun clear() {
        saveJob?.cancel()
        _uiState.value = DeclarationUiState(isLoaded = true)
        viewModelScope.launch { runCatching { store.clear() } }
    }

    fun flushDraft() {
        if (!_uiState.value.isLoaded) return
        saveJob?.cancel()
        val snapshot = _uiState.value.toDraft()
        viewModelScope.launch { runCatching { store.save(snapshot) } }
    }

    fun buildDeclaration(): ApostasyDeclaration? {
        val state = _uiState.value
        if (!state.canGenerate) return null
        return ApostasyDeclaration(
            fullName = state.fullName.trim(),
            homeAddress = state.homeAddress.trim(),
            baptismDate = state.baptismDate,
            baptismParish = state.baptismParish.trim(),
            residenceParish = state.effectiveResidenceParish.trim(),
            motivation = state.motivation.trim(),
            declarationDate = LocalDate.now(),
        )
    }

    private fun mutate(transform: DeclarationUiState.() -> DeclarationUiState) {
        if (!_uiState.value.isLoaded) return
        _uiState.update { it.transform() }
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        val snapshot = _uiState.value.toDraft()
        saveJob = viewModelScope.launch {
            delay(600)
            runCatching { store.save(snapshot) }
        }
    }

    companion object {
        fun factory(store: SecureDraftStore, handoff: ParishHandoff): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DeclarationViewModel(store, handoff) as T
            }
    }
}

private fun DeclarationUiState.parish(field: ParishField) = when (field) {
    ParishField.BAPTISM -> baptismParish
    ParishField.RESIDENCE -> residenceParish
}

private fun DeclarationUiState.withParish(field: ParishField, value: String) = when (field) {
    ParishField.BAPTISM -> copy(baptismParish = value.take(PARISH_MAX_LENGTH))
    // A church picked for the residence is a different one than the baptism parish.
    ParishField.RESIDENCE -> copy(residenceParish = value.take(PARISH_MAX_LENGTH), residenceSameAsBaptism = false)
}

private fun DeclarationDraft.toUiState() = DeclarationUiState(
    isLoaded = true,
    fullName = fullName,
    baptismDate = runCatching { LocalDate.ofEpochDay(baptismEpochDay) }
        .getOrDefault(DEFAULT_BAPTISM_DATE)
        .coerceAtMost(LocalDate.now()),
    baptismParish = baptismParish,
    residenceParish = residenceParish,
    residenceSameAsBaptism = residenceSameAsBaptism,
    homeAddress = homeAddress,
    motivation = motivation,
)

private fun DeclarationUiState.toDraft() = DeclarationDraft(
    fullName = fullName,
    baptismEpochDay = baptismDate.toEpochDay(),
    baptismParish = baptismParish,
    residenceParish = residenceParish,
    residenceSameAsBaptism = residenceSameAsBaptism,
    homeAddress = homeAddress,
    motivation = motivation,
)

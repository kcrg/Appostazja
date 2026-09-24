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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class DeclarationUiState(
    val isLoaded: Boolean = false,
    val fullName: String = "",
    val baptismDate: LocalDate = LocalDate.now(),
    val baptismParish: String = "",
    val residenceParish: String = "",
    val homeAddress: String = "",
    val motivation: String = "",
) {
    val canGenerate: Boolean
        get() = isLoaded &&
            fullName.isNotBlank() &&
            homeAddress.isNotBlank() &&
            baptismParish.isNotBlank() &&
            residenceParish.isNotBlank() &&
            motivation.isNotBlank() &&
            !baptismDate.isAfter(LocalDate.now())
}

internal class DeclarationViewModel(private val store: SecureDraftStore) : ViewModel() {
    private val _uiState = MutableStateFlow(DeclarationUiState())
    val uiState: StateFlow<DeclarationUiState> = _uiState.asStateFlow()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val restored = store.load()
            _uiState.value = restored?.toUiState() ?: DeclarationUiState(isLoaded = true)
        }
    }

    fun updateFullName(value: String) = mutate { copy(fullName = value.take(200)) }
    fun updateHomeAddress(value: String) = mutate { copy(homeAddress = value.take(300)) }
    fun updateBaptismDate(value: LocalDate) = mutate {
        copy(baptismDate = value.coerceAtMost(LocalDate.now()))
    }
    fun updateBaptismParish(value: String) = mutate { copy(baptismParish = value.take(300)) }
    fun updateResidenceParish(value: String) = mutate { copy(residenceParish = value.take(300)) }
    fun updateMotivation(value: String) = mutate { copy(motivation = value.take(2_000)) }

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
            residenceParish = state.residenceParish.trim(),
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
        fun factory(store: SecureDraftStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DeclarationViewModel(store) as T
            }
    }
}

private fun DeclarationDraft.toUiState() = DeclarationUiState(
    isLoaded = true,
    fullName = fullName,
    baptismDate = runCatching { LocalDate.ofEpochDay(baptismEpochDay) }
        .getOrDefault(LocalDate.now())
        .coerceAtMost(LocalDate.now()),
    baptismParish = baptismParish,
    residenceParish = residenceParish,
    homeAddress = homeAddress,
    motivation = motivation,
)

private fun DeclarationUiState.toDraft() = DeclarationDraft(
    fullName = fullName,
    baptismEpochDay = baptismDate.toEpochDay(),
    baptismParish = baptismParish,
    residenceParish = residenceParish,
    homeAddress = homeAddress,
    motivation = motivation,
)

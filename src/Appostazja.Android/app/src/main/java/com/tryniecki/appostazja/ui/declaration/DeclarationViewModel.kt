package com.tryniecki.appostazja.ui.declaration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tryniecki.appostazja.pdf.ApostasyDeclaration
import com.tryniecki.appostazja.pdf.BaptismCertificateRequest
import com.tryniecki.appostazja.pdf.CertificateDelivery
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

/** A church from the map written into the form, with what the field held before so it can be undone. */
internal data class AppliedParish(val pick: ParishPick, val previous: String)

internal class DeclarationViewModel(
    private val store: SecureDraftStore,
    private val handoff: ParishHandoff,
) : ViewModel(), DeclarationFormActions {
    private val _uiState = MutableStateFlow(DeclarationUiState())
    val uiState: StateFlow<DeclarationUiState> = _uiState.asStateFlow()
    private val _appliedParish = MutableStateFlow<AppliedParish?>(null)
    val appliedParish: StateFlow<AppliedParish?> = _appliedParish.asStateFlow()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val restored = store.load()?.toUiState() ?: DeclarationUiState(isLoaded = true)
            _uiState.value = restored.copy(step = restored.initialStep())
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

    // Moving through the wizard; not saved, as the draft restores to the right step by itself.
    override fun goTo(step: DeclarationStep) = navigate { copy(step = step, flow = DocumentFlow.DECLARATION) }
    override fun openCertificateRequest() = navigate { copy(flow = DocumentFlow.CERTIFICATE_REQUEST) }

    /** One step back, or out of the certificate request; false when already at the start. */
    fun back(): Boolean {
        val state = _uiState.value
        when {
            state.flow == DocumentFlow.CERTIFICATE_REQUEST -> navigate { copy(flow = DocumentFlow.DECLARATION) }
            state.step.ordinal > 0 -> navigate { copy(step = DeclarationStep.entries[step.ordinal - 1]) }
            else -> return false
        }
        return true
    }

    override fun answerLivesInBaptismParish(value: Boolean) = mutate { copy(livesInBaptismParish = value) }
    override fun answerHasBaptismCertificate(value: Boolean) = mutate { copy(hasBaptismCertificate = value) }
    override fun updateFullName(value: String) = mutate { copy(fullName = value.take(200)) }
    override fun updateHomeAddress(value: String) = mutate { copy(homeAddress = value.take(300)) }
    override fun updateBaptismDate(value: LocalDate) = mutate {
        copy(baptismDate = value.coerceAtMost(LocalDate.now()))
    }
    override fun updateBaptismDateUnknown(value: Boolean) = mutate { copy(baptismDateUnknown = value) }
    override fun updateBaptismYear(value: String) = mutate { copy(baptismYear = value.filter(Char::isDigit).take(4)) }
    override fun updateBaptismParish(value: String) = mutate { copy(baptismParish = value.take(PARISH_MAX_LENGTH)) }
    override fun updateResidenceParish(value: String) = mutate { copy(residenceParish = value.take(PARISH_MAX_LENGTH)) }
    override fun updateMotivation(value: String) = mutate { copy(motivation = value.take(MOTIVATION_MAX_LENGTH)) }
    /** [preset] is the chosen ready-made reason, [presets] all of them, as the screen shows them. */
    override fun applyMotivationPreset(preset: String, presets: Collection<String>) = mutate {
        copy(motivation = withMotivationPreset(motivation, preset, presets).take(MOTIVATION_MAX_LENGTH))
    }
    override fun updateRequestAnnotatedCertificate(value: Boolean) = mutate { copy(requestAnnotatedCertificate = value) }
    override fun updateBirthDate(value: LocalDate?) = mutate { copy(birthDate = value?.coerceAtMost(LocalDate.now())) }
    override fun updateBirthPlace(value: String) = mutate { copy(birthPlace = value.take(SHORT_FIELD_MAX_LENGTH)) }
    override fun updateFatherName(value: String) = mutate { copy(fatherName = value.take(SHORT_FIELD_MAX_LENGTH)) }
    override fun updateMotherName(value: String) = mutate { copy(motherName = value.take(SHORT_FIELD_MAX_LENGTH)) }
    override fun updateCertificateDelivery(value: CertificateDelivery) = mutate { copy(certificateDelivery = value) }
    override fun updateContact(value: String) = mutate { copy(contact = value.take(SHORT_FIELD_MAX_LENGTH)) }

    /** Sample data for testing the form and the PDF; offered only in test builds. Opens the last step. */
    fun fillSample() = mutate {
        copy(
            livesInBaptismParish = false,
            hasBaptismCertificate = true,
            fullName = "Jan Kowalski",
            homeAddress = "ul. Przykładowa 12/3, 00-001 Warszawa",
            baptismDate = LocalDate.of(1995, 5, 14),
            baptismDateUnknown = false,
            baptismParish = "Parafia Rzymskokatolicka pw. św. Anny w Warszawie",
            residenceParish = "Parafia Rzymskokatolicka pw. Najświętszego Zbawiciela w Warszawie",
            motivation = "Nie identyfikuję się z Kościołem katolickim i nie podzielam jego nauczania.",
            birthDate = LocalDate.of(1995, 4, 2),
            birthPlace = "Warszawa",
            fatherName = "Adam",
            motherName = "Anna Nowak",
            step = DeclarationStep.MOTIVATION,
            flow = DocumentFlow.DECLARATION,
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

    fun buildDeclaration(today: LocalDate = LocalDate.now()): ApostasyDeclaration? {
        val state = _uiState.value
        if (!state.isLoaded || state.missingFields(today).isNotEmpty()) return null
        return ApostasyDeclaration(
            fullName = state.fullName.trim(),
            homeAddress = state.homeAddress.trim(),
            baptismDate = state.baptismDateOrNull(today) ?: return null,
            baptismParish = state.baptismParish.trim(),
            residenceParish = state.effectiveResidenceParish.trim(),
            motivation = state.motivation.trim(),
            declarationDate = today,
            baptisedElsewhere = state.needsBaptismCertificate,
            requestAnnotatedCertificate = state.requestAnnotatedCertificate,
        )
    }

    fun buildCertificateRequest(today: LocalDate = LocalDate.now()): BaptismCertificateRequest? {
        val state = _uiState.value
        if (!state.isLoaded || state.requestMissingFields(today).isNotEmpty()) return null
        return BaptismCertificateRequest(
            fullName = state.fullName.trim(),
            homeAddress = state.homeAddress.trim(),
            baptismParish = state.baptismParish.trim(),
            baptismDate = state.baptismDateOrNull(today) ?: return null,
            birthDate = state.birthDate,
            birthPlace = state.birthPlace,
            fatherName = state.fatherName,
            motherName = state.motherName,
            delivery = state.certificateDelivery,
            contact = state.contact,
            requestDate = today,
        )
    }

    private fun navigate(transform: DeclarationUiState.() -> DeclarationUiState) {
        if (_uiState.value.isLoaded) _uiState.update { it.transform() }
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
        private const val SHORT_FIELD_MAX_LENGTH = 200

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
    ParishField.RESIDENCE -> copy(residenceParish = value.take(PARISH_MAX_LENGTH), livesInBaptismParish = false)
}

internal fun DeclarationDraft.toUiState() = DeclarationUiState(
    isLoaded = true,
    fullName = fullName,
    baptismDate = runCatching { LocalDate.ofEpochDay(baptismEpochDay) }
        .getOrDefault(DEFAULT_BAPTISM_DATE)
        .coerceAtMost(LocalDate.now()),
    baptismParish = baptismParish,
    residenceParish = residenceParish,
    // Drafts from before the wizard only knew the "same parish" checkbox.
    livesInBaptismParish = livesInBaptismParish ?: true.takeIf { residenceSameAsBaptism },
    hasBaptismCertificate = hasBaptismCertificate,
    homeAddress = homeAddress,
    motivation = motivation,
    baptismDateUnknown = baptismDateUnknown,
    baptismYear = baptismYear,
    requestAnnotatedCertificate = requestAnnotatedCertificate,
    birthDate = birthEpochDay?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() },
    birthPlace = birthPlace,
    fatherName = fatherName,
    motherName = motherName,
    certificateDelivery = runCatching { CertificateDelivery.valueOf(certificateDelivery) }
        .getOrDefault(CertificateDelivery.POST),
    contact = contact,
)

internal fun DeclarationUiState.toDraft() = DeclarationDraft(
    fullName = fullName,
    baptismEpochDay = baptismDate.toEpochDay(),
    baptismParish = baptismParish,
    residenceParish = residenceParish,
    residenceSameAsBaptism = livesInBaptismParish == true,
    livesInBaptismParish = livesInBaptismParish,
    hasBaptismCertificate = hasBaptismCertificate,
    homeAddress = homeAddress,
    motivation = motivation,
    baptismDateUnknown = baptismDateUnknown,
    baptismYear = baptismYear,
    requestAnnotatedCertificate = requestAnnotatedCertificate,
    birthEpochDay = birthDate?.toEpochDay(),
    birthPlace = birthPlace,
    fatherName = fatherName,
    motherName = motherName,
    certificateDelivery = certificateDelivery.name,
    contact = contact,
)

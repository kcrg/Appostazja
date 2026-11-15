package com.tryniecki.appostazja.ui.declaration

import com.tryniecki.appostazja.pdf.BaptismDate
import com.tryniecki.appostazja.pdf.CertificateDelivery
import com.tryniecki.appostazja.pdf.MIN_BAPTISM_YEAR
import java.time.LocalDate

/** Where the baptism date starts before anything is picked: a plausible date, so the picker opens near it. */
internal val DEFAULT_BAPTISM_DATE: LocalDate = LocalDate.of(2000, 1, 20)

/** The steps of the declaration wizard, in order. */
internal enum class DeclarationStep { SITUATION, PERSONAL, BAPTISM, MOTIVATION }

/** The two documents the wizard prepares; the certificate request is a side trip from the declaration. */
internal enum class DocumentFlow { DECLARATION, CERTIFICATE_REQUEST }

/** The parts of the form that can be left unanswered, with the step that asks for them. */
internal enum class FormField(val step: DeclarationStep) {
    SITUATION(DeclarationStep.SITUATION),
    FULL_NAME(DeclarationStep.PERSONAL),
    HOME_ADDRESS(DeclarationStep.PERSONAL),
    BAPTISM_DATE(DeclarationStep.BAPTISM),
    BAPTISM_PARISH(DeclarationStep.BAPTISM),
    RESIDENCE_PARISH(DeclarationStep.BAPTISM),
    MOTIVATION(DeclarationStep.MOTIVATION),
}

internal data class DeclarationUiState(
    val isLoaded: Boolean = false,
    val fullName: String = "",
    val baptismDate: LocalDate = DEFAULT_BAPTISM_DATE,
    val baptismParish: String = "",
    val residenceParish: String = "",
    /**
     * Lives in the parish of baptism, so the declaration is submitted there and needs no certificate.
     * Null until answered.
     */
    val livesInBaptismParish: Boolean? = null,
    /** Already holds a baptism certificate; asked only when baptised elsewhere. Null until answered. */
    val hasBaptismCertificate: Boolean? = null,
    val homeAddress: String = "",
    val motivation: String = "",
    /** The exact date is not known; [baptismYear] may still give the year. */
    val baptismDateUnknown: Boolean = false,
    /** Digits typed for the year when the exact date is not known; blank leaves the date to be written by hand. */
    val baptismYear: String = "",
    /** The declaration asks for a baptism certificate noting the departure. */
    val requestAnnotatedCertificate: Boolean = false,
    // Only for the certificate request: details that help the parish find the baptism record.
    val birthDate: LocalDate? = null,
    val birthPlace: String = "",
    val fatherName: String = "",
    val motherName: String = "",
    val certificateDelivery: CertificateDelivery = CertificateDelivery.POST,
    val contact: String = "",
    // Where the user is; not kept in the draft.
    val step: DeclarationStep = DeclarationStep.SITUATION,
    val flow: DocumentFlow = DocumentFlow.DECLARATION,
) {
    /** Where the declaration is submitted. */
    val effectiveResidenceParish: String
        get() = if (livesInBaptismParish == true) baptismParish else residenceParish

    /** Null while the year typed for an unknown date is not a plausible year. */
    fun baptismDateOrNull(today: LocalDate = LocalDate.now()): BaptismDate? = when {
        !baptismDateUnknown -> BaptismDate.Exact(baptismDate).takeIf { !baptismDate.isAfter(today) }
        baptismYear.isBlank() -> BaptismDate.Unknown
        else -> baptismYear.toIntOrNull()
            ?.takeIf { it in MIN_BAPTISM_YEAR..today.year }
            ?.let(BaptismDate::Year)
    }

    /** What the declaration still needs, in the order of the wizard. */
    fun missingFields(today: LocalDate = LocalDate.now()): List<FormField> = buildList {
        if (livesInBaptismParish == null || (livesInBaptismParish == false && hasBaptismCertificate == null)) {
            add(FormField.SITUATION)
        }
        if (fullName.isBlank()) add(FormField.FULL_NAME)
        if (homeAddress.isBlank()) add(FormField.HOME_ADDRESS)
        if (baptismDateOrNull(today) == null) add(FormField.BAPTISM_DATE)
        if (baptismParish.isBlank()) add(FormField.BAPTISM_PARISH)
        // Living in the parish of baptism, the residence field is not asked; the baptism parish covers it.
        if (livesInBaptismParish != true && residenceParish.isBlank()) add(FormField.RESIDENCE_PARISH)
        if (motivation.isBlank()) add(FormField.MOTIVATION)
    }

    /** What the certificate request still needs: who asks, where to, and where the answer goes. */
    fun requestMissingFields(today: LocalDate = LocalDate.now()): List<FormField> = buildList {
        if (baptismParish.isBlank()) add(FormField.BAPTISM_PARISH)
        if (fullName.isBlank()) add(FormField.FULL_NAME)
        if (homeAddress.isBlank()) add(FormField.HOME_ADDRESS)
        if (baptismDateOrNull(today) == null) add(FormField.BAPTISM_DATE)
    }

    val canGenerate: Boolean
        get() = isLoaded && missingFields().isEmpty()

    /** A certificate goes with the declaration unless it is submitted in the parish of baptism. */
    val needsBaptismCertificate: Boolean
        get() = livesInBaptismParish == false

    /**
     * Where a restored draft opens: the situation while it is unanswered, then the first step with something
     * missing, and the last step once everything is in, so a returning user lands on the summary.
     */
    fun initialStep(today: LocalDate = LocalDate.now()): DeclarationStep =
        missingFields(today).firstOrNull()?.step ?: DeclarationStep.MOTIVATION
}

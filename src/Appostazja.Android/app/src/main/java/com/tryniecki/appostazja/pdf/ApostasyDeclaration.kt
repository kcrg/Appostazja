package com.tryniecki.appostazja.pdf

import java.time.LocalDate

data class ApostasyDeclaration(
    val fullName: String,
    val homeAddress: String,
    val baptismDate: BaptismDate,
    val baptismParish: String,
    val residenceParish: String,
    val motivation: String,
    val declarationDate: LocalDate,
    /** Baptised in another parish than the one the declaration is submitted to: a certificate goes with it. */
    val baptisedElsewhere: Boolean = true,
    /** Asks for a baptism certificate carrying the note of leaving the Church (point 11 of the decree). */
    val requestAnnotatedCertificate: Boolean = false,
)

/** What the person knows about the date of their baptism. */
sealed interface BaptismDate {
    data class Exact(val date: LocalDate) : BaptismDate
    data class Year(val year: Int) : BaptismDate
    /** Left blank in the declaration, to be written in by hand. */
    data object Unknown : BaptismDate
}

/** A request to the parish of baptism for a baptism certificate, to attach to the declaration. */
data class BaptismCertificateRequest(
    val fullName: String,
    val homeAddress: String,
    val baptismParish: String,
    val baptismDate: BaptismDate,
    val birthDate: LocalDate?,
    val birthPlace: String,
    val fatherName: String,
    val motherName: String,
    val delivery: CertificateDelivery,
    val contact: String,
    val requestDate: LocalDate,
)

enum class CertificateDelivery { POST, IN_PERSON }

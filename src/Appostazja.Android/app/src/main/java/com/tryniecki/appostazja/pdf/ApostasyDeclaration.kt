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
)

/** What the person knows about the date of their baptism. */
sealed interface BaptismDate {
    data class Exact(val date: LocalDate) : BaptismDate
    data class Year(val year: Int) : BaptismDate
    /** Left blank in the declaration, to be written in by hand. */
    data object Unknown : BaptismDate
}

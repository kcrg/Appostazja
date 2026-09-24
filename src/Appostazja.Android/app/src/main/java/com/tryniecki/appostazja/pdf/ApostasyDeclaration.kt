package com.tryniecki.appostazja.pdf

import java.time.LocalDate

data class ApostasyDeclaration(
    val fullName: String,
    val homeAddress: String,
    val baptismDate: LocalDate,
    val baptismParish: String,
    val residenceParish: String,
    val motivation: String,
    val declarationDate: LocalDate,
)

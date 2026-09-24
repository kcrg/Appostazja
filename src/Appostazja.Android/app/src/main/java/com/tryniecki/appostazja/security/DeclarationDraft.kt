package com.tryniecki.appostazja.security

import kotlinx.serialization.Serializable

@Serializable
data class DeclarationDraft(
    val fullName: String = "",
    val baptismEpochDay: Long = java.time.LocalDate.now().toEpochDay(),
    val baptismParish: String = "",
    val residenceParish: String = "",
    val homeAddress: String = "",
    val motivation: String = "",
)

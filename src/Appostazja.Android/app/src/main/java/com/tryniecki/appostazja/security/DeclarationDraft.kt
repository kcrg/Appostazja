package com.tryniecki.appostazja.security

import kotlinx.serialization.Serializable

@Serializable
data class DeclarationDraft(
    val fullName: String = "",
    val baptismEpochDay: Long = java.time.LocalDate.now().toEpochDay(),
    val baptismParish: String = "",
    val residenceParish: String = "",
    /** Written for drafts read by an older version; [livesInBaptismParish] is what counts. */
    val residenceSameAsBaptism: Boolean = false,
    val livesInBaptismParish: Boolean? = null,
    val hasBaptismCertificate: Boolean? = null,
    val homeAddress: String = "",
    val motivation: String = "",
    val baptismDateUnknown: Boolean = false,
    val baptismYear: String = "",
    val requestAnnotatedCertificate: Boolean = false,
    val birthEpochDay: Long? = null,
    val birthPlace: String = "",
    val fatherName: String = "",
    val motherName: String = "",
    val certificateDelivery: String = "POST",
    val contact: String = "",
)

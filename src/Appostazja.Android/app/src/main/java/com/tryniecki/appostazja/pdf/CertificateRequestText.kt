package com.tryniecki.appostazja.pdf

/** The wording of a [BaptismCertificateRequest], shared by the PDF letter and the e-mail. */
internal data class CertificateRequestText(
    val subject: String,
    val request: String,
    /** "Label: value" lines that help the parish find the baptism record; only what the person gave. */
    val details: List<String>,
    val delivery: String,
    val contact: String?,
)

internal fun certificateRequestText(request: BaptismCertificateRequest): CertificateRequestText {
    val details = buildList {
        add("Imię i nazwisko: ${request.fullName.trim()}")
        request.birthDate?.let { add("Data urodzenia: ${it.format(DECLARATION_DATE)}") }
        request.birthPlace.trim().takeIf { it.isNotEmpty() }?.let { add("Miejsce urodzenia: $it") }
        request.fatherName.trim().takeIf { it.isNotEmpty() }?.let { add("Imię ojca: $it") }
        request.motherName.trim().takeIf { it.isNotEmpty() }?.let { add("Imię i nazwisko rodowe matki: $it") }
        // An unknown date is no help to the parish; the other details have to do.
        if (request.baptismDate != BaptismDate.Unknown) add(baptismDateLine(request.baptismDate))
    }
    val delivery = when (request.delivery) {
        CertificateDelivery.POST ->
            "Proszę o przesłanie świadectwa pocztą na adres: ${request.homeAddress.trim().replace('\n', ' ')}."
        CertificateDelivery.IN_PERSON ->
            "Świadectwo odbiorę osobiście w kancelarii parafialnej. Proszę o informację, kiedy będzie gotowe."
    }
    return CertificateRequestText(
        subject = "Prośba o wydanie świadectwa chrztu – ${request.fullName.trim()}",
        request = "Uprzejmie proszę o wydanie aktualnego świadectwa chrztu. " +
            "Poniżej podaję dane, które pomogą odnaleźć akt chrztu w księdze ochrzczonych.",
        details = details,
        delivery = delivery,
        contact = request.contact.trim().takeIf { it.isNotEmpty() }?.let { "Kontakt: $it" },
    )
}

/** The e-mail body: the same text as the letter, with a greeting and a signature. */
internal fun certificateRequestEmail(text: CertificateRequestText, fullName: String): String = buildString {
    appendLine("Dzień dobry,")
    appendLine()
    appendLine(text.request)
    appendLine()
    text.details.forEach(::appendLine)
    appendLine()
    appendLine(text.delivery)
    text.contact?.let(::appendLine)
    appendLine()
    appendLine("Z poważaniem")
    append(fullName.trim())
}

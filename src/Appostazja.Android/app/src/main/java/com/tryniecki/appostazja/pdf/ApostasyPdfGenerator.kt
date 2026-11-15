package com.tryniecki.appostazja.pdf

import android.graphics.pdf.PdfDocument
import java.io.OutputStream

class ApostasyPdfGenerator {
    fun generate(declaration: ApostasyDeclaration, destination: OutputStream) {
        validate(declaration)
        write(destination) { writer ->
            writer.dateLine(letterDateLine(declaration.homeAddress, declaration.declarationDate))
            writer.space(18f)
            writer.sender(declaration.fullName, listOf(declaration.homeAddress))
            writer.space(26f)
            writer.addressee("Proboszcz parafii miejsca zamieszkania", declaration.residenceParish)
            writer.space(44f)

            writer.title("OŚWIADCZENIE WOLI", "O WYSTĄPIENIU Z KOŚCIOŁA KATOLICKIEGO")
            writer.space(26f)

            writer.paragraph(
                "Ja, niżej podpisany/a, oświadczam, że w sposób świadomy, dobrowolny i wolny " +
                    "występuję ze wspólnoty Kościoła katolickiego. Proszę o przeprowadzenie czynności " +
                    "przewidzianych w Dekrecie Ogólnym Konferencji Episkopatu Polski w sprawie wystąpień " +
                    "z Kościoła oraz powrotu do wspólnoty Kościoła, obowiązującym od 19 lutego 2016 r., " +
                    "oraz o dokonanie stosownej adnotacji w księdze ochrzczonych.",
            )
            writer.space(14f)
            writer.fields(
                listOf(
                    labelAndValue(baptismDateLine(declaration.baptismDate)),
                    "Parafia chrztu:" to declaration.baptismParish,
                ),
            )
            writer.space(14f)

            writer.heading("Motywacja")
            writer.paragraph(declaration.motivation)
            writer.space(14f)
            writer.paragraph(
                "Decyzję podejmuję bez przymusu i ze świadomością konsekwencji, jakie pociąga za sobą ten akt. " +
                    "Moja wola zerwania wspólnoty z Kościołem jest jednoznaczna.",
            )
            if (declaration.requestAnnotatedCertificate) {
                writer.space(8f)
                writer.paragraph("Proszę o wydanie mi świadectwa chrztu z adnotacją o wystąpieniu z Kościoła.")
            }
            writer.signature("czytelny, własnoręczny podpis")
            if (declaration.baptisedElsewhere) {
                writer.space(10f)
                writer.note("Załącznik: świadectwo chrztu")
            }
        }
    }

    fun generate(request: BaptismCertificateRequest, destination: OutputStream) {
        require(request.fullName.isNotBlank() && request.homeAddress.isNotBlank() && request.baptismParish.isNotBlank())
        val text = certificateRequestText(request)
        write(destination) { writer ->
            writer.dateLine(letterDateLine(request.homeAddress, request.requestDate))
            writer.space(18f)
            writer.sender(request.fullName, listOfNotNull(request.homeAddress, text.contact?.let(::labelAndValue)?.second))
            writer.space(26f)
            writer.addressee("Kancelaria parafialna", request.baptismParish)
            writer.space(44f)

            writer.title("PROŚBA O WYDANIE ŚWIADECTWA CHRZTU")
            writer.space(26f)

            writer.paragraph(text.request)
            writer.space(14f)
            writer.fields(text.details.map(::labelAndValue))
            writer.space(14f)
            writer.paragraph(text.delivery)
            writer.space(8f)
            writer.paragraph("Z poważaniem")
            writer.signature("podpis")
        }
    }

    private fun write(destination: OutputStream, content: (PdfWriter) -> Unit) {
        val document = PdfDocument()
        try {
            val writer = PdfWriter(document)
            content(writer)
            writer.finish()
            document.writeTo(destination)
        } finally {
            document.close()
        }
    }

    private fun validate(value: ApostasyDeclaration) {
        require(value.fullName.isNotBlank() && value.fullName.length <= 200)
        require(value.homeAddress.isNotBlank() && value.homeAddress.length <= 300)
        require(value.baptismParish.isNotBlank() && value.baptismParish.length <= 300)
        require(value.residenceParish.isNotBlank() && value.residenceParish.length <= 300)
        require(value.motivation.isNotBlank() && value.motivation.length <= 2_000)
        when (val date = value.baptismDate) {
            is BaptismDate.Exact -> require(!date.date.isAfter(value.declarationDate))
            is BaptismDate.Year -> require(date.year in MIN_BAPTISM_YEAR..value.declarationDate.year)
            BaptismDate.Unknown -> Unit
        }
    }
}

/** "Data chrztu: 14.05.1995" as a label column and a value column. */
internal fun labelAndValue(line: String): Pair<String, String> {
    val split = line.indexOf(": ")
    return if (split < 0) "" to line else line.substring(0, split + 1) to line.substring(split + 2)
}

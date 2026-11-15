package com.tryniecki.appostazja.pdf

import android.graphics.pdf.PdfDocument
import java.io.OutputStream

class ApostasyPdfGenerator {
    fun generate(declaration: ApostasyDeclaration, destination: OutputStream) {
        validate(declaration)
        val document = PdfDocument()
        try {
            val writer = PdfWriter(document)
            writer.right(letterDateLine(declaration.homeAddress, declaration.declarationDate), 10f)
            writer.space(10f)
            writer.left(declaration.fullName, 11f)
            writer.left(declaration.homeAddress, 11f)
            writer.space(20f)

            writer.right("Proboszcz parafii miejsca zamieszkania", 10f)
            writer.right(declaration.residenceParish, 11f)
            writer.space(34f)

            writer.center("OŚWIADCZENIE WOLI", 15f, bold = true)
            writer.center("O WYSTĄPIENIU Z KOŚCIOŁA KATOLICKIEGO", 15f, bold = true)
            writer.space(24f)

            writer.paragraph(
                "Ja, niżej podpisany/a, oświadczam, że w sposób świadomy, dobrowolny i wolny " +
                    "występuję ze wspólnoty Kościoła katolickiego. Proszę o przeprowadzenie czynności " +
                    "przewidzianych w Dekrecie Ogólnym Konferencji Episkopatu Polski w sprawie wystąpień " +
                    "z Kościoła oraz powrotu do wspólnoty Kościoła, obowiązującym od 19 lutego 2016 r., " +
                    "oraz o dokonanie stosownej adnotacji w księdze ochrzczonych.",
                11f,
            )
            writer.space(12f)
            writer.left(baptismDateLine(declaration.baptismDate), 11f)
            writer.paragraph("Parafia chrztu: ${declaration.baptismParish}", 11f)
            writer.space(14f)

            writer.paragraph("Motywacja:", 11f, bold = true)
            writer.paragraph(declaration.motivation, 11f)
            writer.space(14f)
            writer.paragraph(
                "Decyzję podejmuję bez przymusu i ze świadomością konsekwencji, jakie pociąga za sobą ten akt. " +
                    "Moja wola zerwania wspólnoty z Kościołem jest jednoznaczna.",
                11f,
            )
            if (declaration.requestAnnotatedCertificate) {
                writer.space(10f)
                writer.paragraph(
                    "Proszę o wydanie mi świadectwa chrztu z adnotacją o wystąpieniu z Kościoła.",
                    11f,
                )
            }
            if (declaration.baptisedElsewhere) {
                writer.space(20f)
                writer.paragraph("Załącznik: świadectwo chrztu", 10f)
            }
            writer.signature("czytelny, własnoręczny podpis")
            writer.finish()
            document.writeTo(destination)
        } finally {
            document.close()
        }
    }

    fun generate(request: BaptismCertificateRequest, destination: OutputStream) {
        require(request.fullName.isNotBlank() && request.homeAddress.isNotBlank() && request.baptismParish.isNotBlank())
        val text = certificateRequestText(request)
        val document = PdfDocument()
        try {
            val writer = PdfWriter(document)
            writer.right(letterDateLine(request.homeAddress, request.requestDate), 10f)
            writer.space(10f)
            writer.left(request.fullName, 11f)
            writer.left(request.homeAddress, 11f)
            text.contact?.let { writer.left(it, 10f) }
            writer.space(20f)

            writer.right("Kancelaria parafialna", 10f)
            writer.right(request.baptismParish, 11f)
            writer.space(34f)

            writer.center("PROŚBA O WYDANIE ŚWIADECTWA CHRZTU", 15f, bold = true)
            writer.space(24f)

            writer.paragraph(text.request, 11f)
            writer.space(12f)
            text.details.forEach { writer.paragraph(it, 11f) }
            writer.space(14f)
            writer.paragraph(text.delivery, 11f)
            writer.signature("podpis")
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

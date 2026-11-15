package com.tryniecki.appostazja.pdf

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant

class DeclarationArchiveTest {
    private val root: File = Files.createTempDirectory("declarations").toFile()
    private val stored = File(root, "archive")
    private val plain = File(root, "plain")
    private var now = Instant.parse("2026-11-14T12:00:00Z")
    private val archive = DeclarationArchive(stored, plain, ReversingCipher, clock = { now })
    private val pdf = "%PDF-1.7 Jan Kowalski".toByteArray()

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `declarations are listed newest first with the time they were generated`() = runBlocking {
        val first = archive.save(pdf)
        now = now.plus(Duration.ofDays(2))
        val second = archive.save(pdf)

        assertEquals(listOf(second, first), archive.list())
        assertEquals(Instant.parse("2026-11-16T12:00:00Z"), archive.list().first().createdAt)
    }

    @Test
    fun `two declarations generated in the same millisecond both survive`() = runBlocking {
        val first = archive.save(pdf)
        val second = archive.save("%PDF other".toByteArray())

        assertNotEquals(first.id, second.id)
        assertEquals(2, archive.list().size)
    }

    @Test
    fun `the archive holds only ciphertext and hands back the original PDF`() = runBlocking {
        val saved = archive.save(pdf)

        val onDisk = stored.listFiles()!!.single().readBytes()
        assertFalse(onDisk.contentEquals(pdf))

        val exported = ByteArrayOutputStream().also { archive.export(saved.id, it) }.toByteArray()
        assertArrayEquals(pdf, exported)
        assertArrayEquals(pdf, archive.plainCopy(saved.id, "deklaracja.pdf").readBytes())
    }

    @Test
    fun `deleting removes the declaration and its plain copy`() = runBlocking {
        val saved = archive.save(pdf)
        val copy = archive.plainCopy(saved.id, "deklaracja.pdf")

        archive.delete(saved.id)

        assertTrue(archive.list().isEmpty())
        assertFalse(copy.exists())
    }

    @Test
    fun `plain copies outlive the screen but are pruned after an hour`() = runBlocking {
        val saved = archive.save(pdf)
        val copy = archive.plainCopy(saved.id, "deklaracja.pdf")
        copy.setLastModified(now.toEpochMilli())
        copy.parentFile!!.setLastModified(now.toEpochMilli())

        now = now.plus(Duration.ofMinutes(30))
        archive.list()
        assertTrue("a share target may still be reading it", copy.exists())

        now = now.plus(Duration.ofMinutes(31))
        archive.list()
        assertFalse(copy.exists())
    }

    @Test
    fun `a certificate request is kept apart from declarations and opens like one`() = runBlocking {
        val declaration = archive.save(pdf)
        val request = archive.save("%PDF request".toByteArray(), DocumentKind.CERTIFICATE_REQUEST)

        assertEquals(
            listOf(DocumentKind.CERTIFICATE_REQUEST, DocumentKind.DECLARATION),
            archive.list().map { it.kind },
        )
        assertEquals(DocumentKind.CERTIFICATE_REQUEST, archive.find(request.id)?.kind)
        assertArrayEquals("%PDF request".toByteArray(), archive.plainCopy(request.id, "prosba.pdf").readBytes())

        archive.delete(request.id)
        assertEquals(listOf(declaration), archive.list())
    }

    @Test
    fun `unrelated files in the archive directory are ignored`() = runBlocking {
        archive.save(pdf)
        File(stored, "notes.txt").writeText("x")
        File(stored, "123.pdf.enc.partial").writeText("x")

        assertEquals(1, archive.list().size)
    }

    /** Not encryption, just a reversible transform so the test can tell stored bytes from the PDF. */
    private object ReversingCipher : BytesCipher {
        override fun encrypt(plaintext: ByteArray) = plaintext.reversedArray()
        override fun decrypt(payload: ByteArray) = payload.reversedArray()
    }
}

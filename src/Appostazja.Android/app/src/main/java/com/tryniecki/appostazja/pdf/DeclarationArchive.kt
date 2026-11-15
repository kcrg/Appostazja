package com.tryniecki.appostazja.pdf

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The kinds of documents the app generates. */
enum class DocumentKind(internal val suffix: String) {
    DECLARATION(".pdf.enc"),
    CERTIFICATE_REQUEST(".request.pdf.enc"),
}

/** A PDF generated earlier, identified by when it was created. */
data class ArchivedDeclaration(
    val id: String,
    val createdAt: Instant,
    val kind: DocumentKind = DocumentKind.DECLARATION,
)

/** Encrypts archived PDFs at rest; the Keystore implementation is swapped for a fake in JVM tests. */
interface BytesCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(payload: ByteArray): ByteArray
}

/**
 * Generated declarations, kept so they can be previewed or shared again. They hold the same personal data as
 * the draft, so like the draft they are encrypted with a Keystore key in a directory excluded from backups.
 * Plain copies are written to the cache only to hand them to the viewer or to another app. An app receiving a
 * share reads the file whenever it likes, so copies are not deleted on leaving the screen; copies older than
 * [PLAIN_COPY_LIFETIME] are pruned whenever the archive is used.
 */
class DeclarationArchive(
    private val directory: File,
    private val plainDirectory: File,
    private val cipher: BytesCipher,
    private val clock: () -> Instant = Instant::now,
) {
    constructor(context: Context) : this(
        directory = File(context.noBackupFilesDir, "declarations"),
        plainDirectory = File(context.cacheDir, "declarations"),
        cipher = KeystoreCipher(),
    )

    suspend fun save(
        pdf: ByteArray,
        kind: DocumentKind = DocumentKind.DECLARATION,
    ): ArchivedDeclaration = withContext(Dispatchers.IO) {
        directory.mkdirs()
        // Millisecond ids; two saves within the same millisecond take the next free one.
        var millis = clock().toEpochMilli()
        while (existingFile(millis.toString()) != null) millis++
        val id = millis.toString()
        val target = File(directory, "$id${kind.suffix}")
        val partial = File(directory, "$id${kind.suffix}.partial")
        partial.writeBytes(cipher.encrypt(pdf))
        check(partial.renameTo(target)) { "Cannot store declaration $id." }
        ArchivedDeclaration(id, Instant.ofEpochMilli(millis), kind)
    }

    /** Newest first. Files that are not archived declarations are ignored. */
    suspend fun list(): List<ArchivedDeclaration> = withContext(Dispatchers.IO) {
        pruneStaleCopies()
        directory.listFiles().orEmpty()
            .mapNotNull { file -> parse(file.name) }
            .sortedByDescending { it.createdAt }
    }

    suspend fun find(id: String): ArchivedDeclaration? = list().firstOrNull { it.id == id }

    /** Writes the decrypted PDF, e.g. to a document the user picked. */
    suspend fun export(id: String, destination: OutputStream) = withContext(Dispatchers.IO) {
        destination.write(read(id))
    }

    /** A decrypted copy in the cache, for the viewer or for sharing through the FileProvider. */
    suspend fun plainCopy(id: String, fileName: String): File = withContext(Dispatchers.IO) {
        pruneStaleCopies()
        val folder = File(plainDirectory, id).apply { mkdirs() }
        File(folder, fileName).apply { writeBytes(read(id)) }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        existingFile(id)?.delete()
        File(plainDirectory, id).deleteRecursively()
    }

    private fun pruneStaleCopies() {
        val cutoff = clock().minus(PLAIN_COPY_LIFETIME).toEpochMilli()
        plainDirectory.listFiles().orEmpty()
            .filter { folder -> folder.walkBottomUp().all { it.lastModified() < cutoff } }
            .forEach { it.deleteRecursively() }
    }

    private fun read(id: String): ByteArray = cipher.decrypt(fileFor(id).readBytes())

    private fun fileFor(id: String): File =
        existingFile(id) ?: throw java.io.FileNotFoundException("No archived document $id.")

    private fun existingFile(id: String): File? {
        require(id.all(Char::isDigit) && id.isNotEmpty()) { "Invalid declaration id." }
        return DocumentKind.entries.map { File(directory, "$id${it.suffix}") }.firstOrNull(File::exists)
    }

    private fun parse(fileName: String): ArchivedDeclaration? {
        // The longest suffix first: ".request.pdf.enc" also ends with ".pdf.enc".
        val kind = DocumentKind.entries.sortedByDescending { it.suffix.length }
            .firstOrNull { fileName.endsWith(it.suffix) } ?: return null
        val millis = fileName.removeSuffix(kind.suffix).toLongOrNull() ?: return null
        return ArchivedDeclaration(millis.toString(), Instant.ofEpochMilli(millis), kind)
    }

    companion object {
        val PLAIN_COPY_LIFETIME: java.time.Duration = java.time.Duration.ofHours(1)
    }
}

/** AES-GCM with a non-exportable Keystore key, the same scheme as the declaration draft. */
private class KeystoreCipher : BytesCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(2 + cipher.iv.size + encrypted.size)
            .putShort(cipher.iv.size.toShort())
            .put(cipher.iv)
            .put(encrypted)
            .array()
    }

    override fun decrypt(payload: ByteArray): ByteArray {
        require(payload.size > 2) { "Encrypted declaration is truncated." }
        val buffer = ByteBuffer.wrap(payload)
        val ivSize = buffer.short.toInt() and 0xffff
        require(ivSize in 12..32 && buffer.remaining() > ivSize) { "Encrypted declaration has an invalid IV." }
        val iv = ByteArray(ivSize).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        return cipher.doFinal(ciphertext)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "appostazja_declaration_archive_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val AAD = "appostazja:declaration-archive:v1".toByteArray(Charsets.UTF_8)
    }
}

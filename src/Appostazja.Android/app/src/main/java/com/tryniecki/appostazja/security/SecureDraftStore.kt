package com.tryniecki.appostazja.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureDraftStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): DeclarationDraft? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!file.baseFile.isFile) return@withContext null
            runCatching { decrypt(file.openRead().use { it.readBytes() }) }
                .getOrElse {
                    file.delete()
                    null
                }
        }
    }

    suspend fun save(draft: DeclarationDraft) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val payload = encrypt(json.encodeToString(DeclarationDraft.serializer(), draft))
            val output = file.startWrite()
            try {
                output.write(payload)
                output.fd.sync()
                file.finishWrite(output)
            } catch (throwable: Throwable) {
                file.failWrite(output)
                throw throwable
            }
        }
    }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { file.delete() }
    }

    private fun encrypt(value: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return ByteBuffer.allocate(2 + cipher.iv.size + encrypted.size)
            .putShort(cipher.iv.size.toShort())
            .put(cipher.iv)
            .put(encrypted)
            .array()
    }

    private fun decrypt(payload: ByteArray): DeclarationDraft {
        require(payload.size > 2) { "Encrypted draft is truncated." }
        val buffer = ByteBuffer.wrap(payload)
        val ivSize = buffer.short.toInt() and 0xffff
        require(ivSize in 12..32 && buffer.remaining() > ivSize) { "Encrypted draft has an invalid IV." }
        val iv = ByteArray(ivSize).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        val plaintext = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        return json.decodeFromString(DeclarationDraft.serializer(), plaintext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val FILE_NAME = "apostasy-declaration-draft-v1.bin"
        const val KEY_ALIAS = "appostazja_declaration_draft_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val AAD = "appostazja:declaration-draft:v1".toByteArray(Charsets.UTF_8)
    }
}

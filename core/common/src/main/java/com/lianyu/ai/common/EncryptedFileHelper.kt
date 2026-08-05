package com.lianyu.ai.common

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.InputStream

/**
 * Encrypts/decrypts locally stored chat media (images, videos) using
 * androidx.security.crypto.EncryptedFile with an Android Keystore master
 * key (AES-256-GCM). Ciphertext is what lives on disk permanently;
 * plaintext is only ever regenerated transiently for display.
 */
object EncryptedFileHelper {

    private const val PLAIN_CACHE_DIR = "media_plain"

    private fun masterKey(context: Context): MasterKey =
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    /** Encrypts [input] and writes the ciphertext to [destFile]. */
    fun encrypt(context: Context, input: InputStream, destFile: File): Boolean {
        return try {
            if (destFile.exists()) destFile.delete()
            destFile.parentFile?.mkdirs()
            val encryptedFile = EncryptedFile.Builder(
                context.applicationContext,
                destFile,
                masterKey(context),
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            encryptedFile.openFileOutput().use { output ->
                input.use { it.copyTo(output) }
            }
            true
        } catch (e: Exception) {
            destFile.delete()
            false
        }
    }

    /**
     * Decrypts [encryptedFile] into a throwaway plaintext file under
     * cacheDir/media_plain for the UI (Coil) to load. Falls back to
     * returning [encryptedFile] itself when it isn't ciphertext — this
     * keeps media sent *before* encryption was introduced working.
     */
    fun decryptToPlainCache(context: Context, encryptedFile: File): File? {
        if (!encryptedFile.exists()) return null
        val plainDir = File(context.applicationContext.cacheDir, PLAIN_CACHE_DIR).apply { mkdirs() }
        val plainFile = File(plainDir, encryptedFile.name)
        return try {
            val enc = EncryptedFile.Builder(
                context.applicationContext,
                encryptedFile,
                masterKey(context),
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            enc.openFileInput().use { input ->
                plainFile.outputStream().use { output -> input.copyTo(output) }
            }
            plainFile
        } catch (e: Exception) {
            encryptedFile
        }
    }
}

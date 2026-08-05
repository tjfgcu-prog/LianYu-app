package com.lianyu.ai.database.repository

import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM secret codec for ApiConfig.apiKey, backed by Android Keystore
 * (hardware-backed on supported devices). The key never leaves the device
 * and is not included in backups.
 */
object S0 : ApiConfigRepository.SecretCodec {
    private const val KEYSTORE_ALIAS = "lianyu_api_key_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val PREFIX = "enc:v1:"

    private val keyStoreKey: SecretKey? by lazy {
        runCatching { getOrCreateAndroidKeyStoreKey() }.getOrNull()
    }

    override fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return plaintext
        val key = keyStoreKey ?: return plaintext
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteBuffer.allocate(iv.size + ciphertext.size).put(iv).put(ciphertext).array()
            PREFIX + Base64.getEncoder().encodeToString(combined)
        } catch (_: Exception) {
            plaintext
        }
    }

    override fun decrypt(value: String): String? {
        if (!isEncrypted(value)) return value
        val key = keyStoreKey ?: return null
        return try {
            val combined = Base64.getDecoder().decode(value.removePrefix(PREFIX))
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    override fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    private fun getOrCreateAndroidKeyStoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }
        val generator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        generator.generateKey()
        val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }
}

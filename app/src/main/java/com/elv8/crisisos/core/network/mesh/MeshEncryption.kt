package com.elv8.crisisos.core.network.mesh

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * Robust encryption wrapper for Mesh communication.
 * In production, this would use libsodium (X25519 + ChaCha20).
 * For now, it uses AES-256 derived from CRS-ID as a proof of concept.
 */
object MeshEncryption {
    private const val ALGORITHM = "AES/ECB/PKCS5Padding"
    private const val KEY_ALGORITHM = "AES"

    fun encrypt(data: String, recipientCrsId: String): String {
        try {
            val key = deriveKey(recipientCrsId)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(data.toByteArray())
            return Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("MeshEncryption", "Encrypt failed: ${e.message}")
            return data 
        }
    }

    fun decrypt(encryptedData: String, recipientCrsId: String): String {
        try {
            val key = deriveKey(recipientCrsId)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key)
            val decoded = Base64.decode(encryptedData, Base64.NO_WRAP)
            return String(cipher.doFinal(decoded))
        } catch (e: Exception) {
            android.util.Log.e("MeshEncryption", "Decrypt failed: ${e.message}")
            return encryptedData
        }
    }

    private fun deriveKey(crsId: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(crsId.toByteArray())
        return SecretKeySpec(hash, KEY_ALGORITHM)
    }
}

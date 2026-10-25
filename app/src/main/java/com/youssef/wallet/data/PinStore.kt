package com.youssef.wallet.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** تخزين آمن للـ PIN: لا يُحفظ الرقم نفسه بل بصمته (PBKDF2 مع salt عشوائي). */
object PinStore {
    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 60_000, 256)).encoded

    /** يعيد "salt:hash" بصيغة Base64. */
    fun create(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val enc = Base64.getEncoder()
        return enc.encodeToString(salt) + ":" + enc.encodeToString(hash(pin, salt))
    }

    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        return try {
            val dec = Base64.getDecoder()
            MessageDigest.isEqual(hash(pin, dec.decode(parts[0])), dec.decode(parts[1]))
        } catch (_: Exception) {
            false
        }
    }
}

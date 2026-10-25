package com.auditcafe.wallet.io

import android.content.Context
import com.auditcafe.wallet.data.AppDatabase
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * نسخ احتياطي لملف قاعدة البيانات كاملاً. اختيارياً مشفّر بكلمة مرور (AES-256-GCM + PBKDF2).
 * يُحفظ عبر Storage Access Framework، فيمكن اختيار Google Drive كوجهة مباشرة.
 */
object BackupIO {
    private val PLAIN = "MWB0".toByteArray()
    private val ENC = "MWB1".toByteArray()

    private fun key(pass: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pass.toCharArray(), salt, 120_000, 256)
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(k, "AES")
    }

    fun backup(ctx: Context, out: OutputStream, password: String?) {
        val db = AppDatabase.get(ctx)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val file = AppDatabase.dbFile(ctx)
        if (password.isNullOrEmpty()) {
            out.write(PLAIN)
            file.inputStream().use { it.copyTo(out) }
            out.flush()
        } else {
            val rnd = SecureRandom()
            val salt = ByteArray(16).also { rnd.nextBytes(it) }
            val iv = ByteArray(12).also { rnd.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            out.write(ENC); out.write(salt); out.write(iv)
            CipherOutputStream(out, cipher).use { co -> file.inputStream().use { it.copyTo(co) } }
        }
    }

    private fun readFully(i: InputStream, n: Int): ByteArray {
        val b = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = i.read(b, off, n - off)
            if (r < 0) throw java.io.IOException("short file")
            off += r
        }
        return b
    }

    /** يستبدل قاعدة البيانات بالنسخة. بعدها يجب إعادة تشغيل التطبيق. */
    fun restore(ctx: Context, input: InputStream, password: String?) {
        val bin = BufferedInputStream(input)
        val magic = readFully(bin, 4)
        val src: InputStream = when {
            magic.contentEquals(PLAIN) -> bin
            magic.contentEquals(ENC) -> {
                require(!password.isNullOrEmpty()) { "password required" }
                val salt = readFully(bin, 16)
                val iv = readFully(bin, 12)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
                CipherInputStream(bin, cipher)
            }
            else -> throw IllegalArgumentException("not a backup file")
        }
        val tmp = File(ctx.cacheDir, "restore.db")
        tmp.outputStream().use { src.copyTo(it) }
        val head = ByteArray(15)
        tmp.inputStream().use { it.read(head) }
        if (String(head) != "SQLite format 3") {
            tmp.delete()
            throw IllegalArgumentException("invalid database")
        }
        AppDatabase.closeAndReset()
        val f = AppDatabase.dbFile(ctx)
        File(f.path + "-wal").delete()
        File(f.path + "-shm").delete()
        tmp.copyTo(f, overwrite = true)
        tmp.delete()
    }
}

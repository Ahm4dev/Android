package com.auditcafe.wallet.io

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.auditcafe.wallet.WalletApp
import com.auditcafe.wallet.data.KeystoreBox
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** نسخ احتياطي تلقائي إلى مجلد يختاره المستخدم (يمكن أن يكون مجلد Google Drive). يحتفظ بآخر 4 نسخ. */
object AutoBackup {
    private const val KEEP = 4
    private const val WEEK = 7L * 86_400_000L

    fun run(ctx: Context): Boolean {
        val p = WalletApp.prefs
        if (p.autoBackupUri.isEmpty()) return false
        val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(p.autoBackupUri)) ?: return false
        if (!dir.canWrite()) return false
        // إذا كانت هناك كلمة مرور ولم يمكن قراءتها لا نكتب نسخة غير مشفرة
        val pass: String? = if (p.autoBackupPassEnc.isEmpty()) null else (KeystoreBox.decrypt(p.autoBackupPassEnc) ?: return false)
        val name = "MyWallet-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".mwb"
        val f = dir.createFile("application/octet-stream", name) ?: return false
        ctx.contentResolver.openOutputStream(f.uri, "wt")!!.use { BackupIO.backup(ctx, it, pass) }
        dir.listFiles()
            .filter { it.name?.startsWith("MyWallet-") == true }
            .sortedByDescending { it.name }
            .drop(KEEP)
            .forEach { it.delete() }
        p.setLastAutoBackup(System.currentTimeMillis())
        return true
    }

    fun runIfDue(ctx: Context) {
        val p = WalletApp.prefs
        if (!p.autoBackup || p.autoBackupUri.isEmpty()) return
        if (System.currentTimeMillis() - p.lastAutoBackup < WEEK) return
        run(ctx)
    }
}

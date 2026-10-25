package com.youssef.wallet.sms

import android.content.Context
import android.provider.Telephony
import com.youssef.wallet.data.AppDatabase

/** يمسح صندوق الوارد القديم كاملاً (أي عدد من الرسائل وأي طول) ويحوّل الحركات إلى "مقترحة". */
object SmsImporter {
    /** يعيد (عدد الرسائل المفحوصة، عدد الحركات المقترحة/المسجلة). */
    suspend fun scan(ctx: Context, onProgress: (done: Int, total: Int, added: Int) -> Unit): Pair<Int, Int> {
        val db = AppDatabase.get(ctx)
        val banks = BankRegistry.merge(ctx, db.bankRules().list())
        var done = 0
        var added = 0
        val cursor = ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null, null, "date DESC"
        ) ?: return 0 to 0
        cursor.use { c ->
            val total = c.count
            val iA = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iB = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iD = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val body = c.getString(iB).orEmpty()
                if (body.length >= 10) {
                    val r = runCatching {
                        SmsProcessor.process(ctx, c.getString(iA).orEmpty(), body, c.getLong(iD), "SMS", banks, notify = false)
                    }.getOrDefault(SmsProcessor.IGNORED)
                    if (r != SmsProcessor.IGNORED) added++
                }
                done++
                if (done % 50 == 0) onProgress(done, total, added)
            }
            onProgress(done, total, added)
        }
        return done to added
    }
}

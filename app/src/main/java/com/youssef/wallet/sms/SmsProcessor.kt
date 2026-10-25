package com.youssef.wallet.sms

import android.content.Context
import com.youssef.wallet.WalletApp
import com.youssef.wallet.ai.SmartEngine
import com.youssef.wallet.data.AppDatabase
import com.youssef.wallet.data.Suggested
import com.youssef.wallet.data.Txn
import java.security.MessageDigest

/** يستقبل نص (SMS أو إشعار) ويقرر: تجاهل / حركة مقترحة / تسجيل تلقائي. */
object SmsProcessor {
    const val IGNORED = 0
    const val SUGGESTED = 1
    const val AUTO = 2

    private const val WINDOW = 5 * 60_000L

    fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    suspend fun process(
        ctx: Context,
        sender: String,
        body: String,
        date: Long,
        source: String = "SMS",
        banks: List<BankInfo>? = null,
        notify: Boolean = true
    ): Int {
        if (body.isBlank()) return IGNORED
        val db = AppDatabase.get(ctx)
        val hash = sha(sender.uppercase() + "|" + body.trim() + "|" + (date / 86_400_000L))
        if (db.suggested().hashCount(hash) > 0 || db.txns().hashCount(hash) > 0) return IGNORED

        val bankList = banks ?: BankRegistry.merge(ctx, db.bankRules().list())
        val bank = BankRegistry.match(bankList, sender)
        val p = SmsParser.parse(body, date, bank) ?: return IGNORED

        val alphaSender = sender.any { it.isLetter() }
        if (bank == null && !alphaSender && p.last4 == null && p.balance == null) return IGNORED
        if (source == "NOTIF" && bank == null && p.last4 == null && p.balance == null) return IGNORED

        // منع التكرار بين SMS والإشعار لنفس الحركة (مصدران مختلفان فقط)
        val other = if (source == "NOTIF") "SMS%" else "NOTIF%"
        if (db.suggested().similarOther(p.amount, p.kind, p.date - WINDOW, p.date + WINDOW, source) > 0) return IGNORED
        if (db.txns().similarOther(p.amount, p.date - WINDOW, p.date + WINDOW, other) > 0) return IGNORED

        val account = p.last4?.let { db.accounts().byLast4(it) }
        val catId = SmartEngine.suggestCategory(db, p.merchant, p.kind, body)
        val bankName = bank?.name ?: sender
        val prefs = WalletApp.prefs

        if (bank?.trusted == true && prefs.autoRegister && account != null) {
            db.txns().insert(
                Txn(
                    type = if (p.kind == "CREDIT") "INCOME" else "EXPENSE",
                    amount = p.amount,
                    accountId = account.id,
                    categoryId = catId,
                    merchant = p.merchant.ifBlank { bankName },
                    date = p.date,
                    hash = hash,
                    source = source + "_AUTO"
                )
            )
            return AUTO
        }

        val id = db.suggested().insert(
            Suggested(
                sender = sender, bank = bankName, body = body.take(2000), amount = p.amount,
                currency = p.currency ?: account?.currency ?: prefs.baseCurrency, kind = p.kind, last4 = p.last4,
                merchant = p.merchant, date = p.date, balanceAfter = p.balance, hash = hash,
                accountId = account?.id, categoryId = catId, source = source
            )
        )
        if (id > 0 && notify) NotificationHelper.suggested(ctx, db.suggested().pendingCountNow())
        return if (id > 0) SUGGESTED else IGNORED
    }
}

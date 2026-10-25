package com.auditcafe.wallet.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/** يولّد الحركات المستحقة من المعاملات المتكررة (بما فيها ما فات أثناء إغلاق التطبيق). */
object RecurringProcessor {
    private val lock = Mutex()

    fun advance(from: Long, period: String, anchorDay: Int): Long {
        val c = Calendar.getInstance().apply { timeInMillis = from }
        when (period) {
            "DAILY" -> c.add(Calendar.DAY_OF_YEAR, 1)
            "WEEKLY" -> c.add(Calendar.DAY_OF_YEAR, 7)
            "YEARLY" -> {
                c.set(Calendar.DAY_OF_MONTH, 1)
                c.add(Calendar.YEAR, 1)
                c.set(Calendar.DAY_OF_MONTH, minOf(anchorDay, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
            else -> {
                c.set(Calendar.DAY_OF_MONTH, 1)
                c.add(Calendar.MONTH, 1)
                c.set(Calendar.DAY_OF_MONTH, minOf(anchorDay, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
        }
        return c.timeInMillis
    }

    /** أول موعد قادم بعد الآن (يُستعمل عند إعادة تفعيل معاملة موقوفة حتى لا تُضاف حركات قديمة). */
    fun nextAfterNow(r: Recurring): Long {
        val now = System.currentTimeMillis()
        var next = r.nextDate
        var guard = 0
        while (next <= now && guard < 5000) { next = advance(next, r.period, r.anchorDay); guard++ }
        return next
    }

    /** يعيد عدد الحركات المضافة. */
    suspend fun runDue(ctx: Context): Int = lock.withLock {
        val db = AppDatabase.get(ctx)
        val now = System.currentTimeMillis()
        var added = 0
        for (id in db.recurring().dueIds(now)) {
            val r = db.recurring().get(id) ?: continue
            if (db.accounts().get(r.accountId) == null) {
                db.recurring().update(r.copy(active = false))
                continue
            }
            var next = r.nextDate
            var guard = 0
            val list = ArrayList<Txn>()
            while (next <= now && guard < 400) {
                list.add(
                    Txn(
                        type = r.type, amount = r.amount, accountId = r.accountId, categoryId = r.categoryId,
                        merchant = r.merchant, note = r.note, date = next, hash = "rec-${r.id}-$next", source = "RECURRING"
                    )
                )
                next = advance(next, r.period, r.anchorDay)
                guard++
            }
            db.withTransaction {
                db.txns().insertAll(list)
                db.recurring().update(r.copy(nextDate = next))
            }
            added += list.size
        }
        added
    }
}

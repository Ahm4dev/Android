package com.youssef.wallet.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.youssef.wallet.WalletApp
import com.youssef.wallet.data.AppDatabase
import com.youssef.wallet.data.RecurringProcessor
import com.youssef.wallet.io.AutoBackup
import com.youssef.wallet.sms.NotificationHelper

object Reminders {
    suspend fun check(ctx: Context) {
        val p = WalletApp.prefs
        if (!p.reminders) return
        val today = System.currentTimeMillis() / 86_400_000L
        if (p.lastReminderDay == today) return
        val db = AppDatabase.get(ctx)
        val now = System.currentTimeMillis()
        val until = now + 36L * 3_600_000L
        val rec = db.recurring().dueSoonCount(now, until)
        val debts = db.debts().dueSoonCount(until)
        if (rec + debts > 0) {
            NotificationHelper.reminder(ctx, rec, debts)
            p.setLastReminderDay(today)
        }
    }
}

/** كل 12 ساعة في الخلفية: المعاملات المتكررة المستحقة، التذكيرات، النسخ الأسبوعي. */
class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        runCatching { RecurringProcessor.runDue(c) }
        runCatching { Reminders.check(c) }
        runCatching { AutoBackup.runIfDue(c) }
        return Result.success()
    }
}

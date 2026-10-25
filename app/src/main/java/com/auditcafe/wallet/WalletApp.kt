package com.auditcafe.wallet

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.auditcafe.wallet.data.Prefs
import com.auditcafe.wallet.work.NotificationHelper
import com.auditcafe.wallet.work.DailyWorker
import java.util.concurrent.TimeUnit

class WalletApp : Application() {
    companion object {
        lateinit var prefs: Prefs
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        NotificationHelper.createChannel(this)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyWorker>(12, TimeUnit.HOURS).build()
        )
    }
}

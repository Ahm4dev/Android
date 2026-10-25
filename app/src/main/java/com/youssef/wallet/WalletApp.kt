package com.youssef.wallet

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.youssef.wallet.data.Prefs
import com.youssef.wallet.sms.NotificationHelper
import com.youssef.wallet.work.DailyWorker
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

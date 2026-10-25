package com.youssef.wallet.sms

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.youssef.wallet.WalletApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** بديل اختياري: قراءة إشعارات تطبيقات البنوك (يُفعَّل من الإعدادات). */
class BankNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ignoredPkgs = setOf(
        "com.whatsapp", "org.telegram.messenger", "com.facebook.orca", "com.google.android.gm"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!WalletApp.prefs.notifCapture) return
        if (sbn.packageName == packageName || sbn.packageName in ignoredPkgs) return
        val ex = sbn.notification.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (text.length < 10) return
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        scope.launch {
            runCatching {
                SmsProcessor.process(applicationContext, label, "$title $text", sbn.postTime, source = "NOTIF")
            }
        }
    }
}

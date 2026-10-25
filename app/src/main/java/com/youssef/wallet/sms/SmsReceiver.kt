package com.youssef.wallet.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** يستقبل الرسائل الجديدة. الرسائل الطويلة (متعددة الأجزاء) تُدمج قبل التحليل. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val grouped = msgs.groupBy { it.originatingAddress ?: "" }
        val pending = goAsync()
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for ((addr, parts) in grouped) {
                    val body = parts.joinToString("") { it.messageBody ?: "" }
                    SmsProcessor.process(app, addr, body, parts.first().timestampMillis)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

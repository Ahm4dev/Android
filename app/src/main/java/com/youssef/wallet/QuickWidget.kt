package com.youssef.wallet

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** ودجت للشاشة الرئيسية: إضافة حركة سريعة + فتح الحركات المقترحة. لا يعرض أي أرصدة (خصوصية). */
class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_quick)
            v.setOnClickPendingIntent(R.id.w_add, pending(ctx, 10) { putExtra("quick", true) })
            v.setOnClickPendingIntent(R.id.w_inbox, pending(ctx, 11) { putExtra("tab", "INBOX") })
            mgr.updateAppWidget(id, v)
        }
    }

    private fun pending(ctx: Context, code: Int, extras: Intent.() -> Unit): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extras()
        }
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

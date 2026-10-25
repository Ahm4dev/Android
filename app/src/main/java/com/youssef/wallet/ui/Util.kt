package com.youssef.wallet.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

val CURRENCIES = listOf("EGP", "USD", "EUR", "GBP", "SAR", "AED", "KWD", "QAR", "JOD", "TRY")
val EMOJIS = listOf(
    "🍽️", "☕", "🛒", "🚗", "🏠", "💡", "📱", "🎬", "🎮", "📚", "🎓", "💊", "🏥", "👕", "💄", "🎁", "✈️", "🏖️",
    "👨‍👩‍👧", "🐾", "🔧", "🧾", "💼", "💻", "📈", "💰", "💸", "🏦", "🔁", "🤲", "📦", "🍔", "🚌", "🎂", "🏧", "💳", "🎯"
)

fun money(v: Double): String = String.format(Locale.US, "%,.2f", v)

fun trimNum(v: Double): String =
    if (v == Math.floor(v) && v < 1e12) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

fun fmtDate(ms: Long): String = SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US).format(Date(ms))
fun fmtIso(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))

/** أسماء التصنيفات/الحسابات الافتراضية مخزنة كمفاتيح (cat_xxx / acc_xxx) وتُترجم هنا. */
fun label(ctx: Context, name: String): String {
    if (name.startsWith("cat_") || name.startsWith("acc_")) {
        val id = ctx.resources.getIdentifier(name, "string", ctx.packageName)
        if (id != 0) return ctx.getString(id)
    }
    return name
}

@Composable
fun lbl(name: String): String = label(LocalContext.current, name)

fun normDigits(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) {
        sb.append(
            when (c) {
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                '\u066B', '\u060C' -> '.'
                '\u066C' -> ','
                else -> c
            }
        )
    }
    return sb.toString()
}

fun parseAmount(s: String?): Double? {
    if (s == null) return null
    val t = normDigits(s.trim()).replace(",", "").replace(" ", "")
    return t.toDoubleOrNull()
}

fun monthRange(offset: Int): Pair<Long, Long> {
    val c = Calendar.getInstance()
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    c.add(Calendar.MONTH, offset)
    val start = c.timeInMillis
    c.add(Calendar.MONTH, 1)
    return start to (c.timeInMillis - 1)
}

fun yearRange(): Pair<Long, Long> {
    val c = Calendar.getInstance()
    c.set(Calendar.MONTH, 0); c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    val start = c.timeInMillis
    c.add(Calendar.YEAR, 1)
    return start to (c.timeInMillis - 1)
}

fun dayRange(): Pair<Long, Long> {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    val start = c.timeInMillis
    c.add(Calendar.DAY_OF_MONTH, 1)
    return start to (c.timeInMillis - 1)
}

fun weekRange(): Pair<Long, Long> {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    c.add(Calendar.DAY_OF_MONTH, -6)
    val start = c.timeInMillis
    return start to Long.MAX_VALUE
}

fun periodRange(p: String): Pair<Long, Long> = when (p) {
    "TODAY" -> dayRange()
    "WEEK" -> weekRange()
    "MONTH" -> monthRange(0)
    "LAST" -> monthRange(-1)
    "3M" -> monthRange(-2).first to monthRange(0).second
    "YEAR" -> yearRange()
    else -> 0L to Long.MAX_VALUE
}

fun conv(amount: Double, cur: String, rates: Map<String, Double>, base: String): Double =
    if (cur == base) amount else amount * (rates[cur] ?: 1.0)

fun parseDate(s: String?): Long? {
    val t = normDigits(s.orEmpty().trim())
    if (t.isEmpty()) return null
    t.toDoubleOrNull()?.let {
        if (it in 20000.0..80000.0) {
            val utc = ((it - 25569.0) * 86_400_000.0).toLong()
            return utc - TimeZone.getDefault().getOffset(utc)
        }
    }
    for (p in listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd", "dd/MM/yyyy HH:mm", "dd/MM/yyyy", "dd-MM-yyyy", "MM/dd/yyyy")) {
        try {
            val f = SimpleDateFormat(p, Locale.US)
            f.isLenient = false
            return f.parse(t)?.time ?: continue
        } catch (_: Exception) {
        }
    }
    return null
}

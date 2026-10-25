package com.auditcafe.wallet.sms

import java.util.Calendar

/**
 * محلل رسائل البنوك (عربي + إنجليزي) مبني على قواعد regex.
 * يعمل محلياً بالكامل ولا يرسل أي نص خارج الجهاز.
 */
object SmsParser {

    data class Parsed(
        val amount: Double,
        val currency: String?,
        val kind: String,          // DEBIT, CREDIT, TRANSFER, ATM
        val last4: String?,
        val merchant: String,
        val balance: Double?,
        val date: Long
    )

    // ---------- تطبيع النص ----------
    private fun normDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                    in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                    '\u066B' -> '.'
                    '\u066C' -> ','
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    private fun nrm(s: String): String =
        s.lowercase().replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي')

    // ---------- مجموعات الكلمات ----------
    private class KwSet(words: List<String>) {
        private val rx: List<Regex> = words.map { k ->
            if (k.all { it.code < 128 })
                Regex("(?<![a-z])" + Regex.escape(k) + (if (k.length <= 3) "(?![a-z])" else ""))
            else Regex(Regex.escape(k))
        }

        fun first(t: String): Int {
            var best = -1
            for (r in rx) {
                val i = r.find(t)?.range?.first ?: continue
                if (best < 0 || i < best) best = i
            }
            return best
        }
    }

    private val atmStrong = KwSet(listOf("atm", "صراف", "سحب نقدي", "cash withdrawal", "cash wdl"))
    private val credit = KwSet(
        listOf(
            "credited", "credit of", "received", "deposit", "salary", "refund", "reversal", "cashback",
            "ايداع", "اضافه", "اضافة", "استلام", "تم استلام", "راتب", "استرداد", "واردة", "وارد", "دائن", "اضيف"
        )
    )
    private val transfer = KwSet(
        listOf("transfer", "instapay", "ipn", "تحويل", "حواله", "حوالة", "انستاباي")
    )
    private val debit = KwSet(
        listOf(
            "debited", "debit", "purchase", "spent", "paid", "payment", "pos", "withdrawn", "withdrawal", "charged",
            "تم خصم", "خصم", "شراء", "دفع", "سداد", "مدين", "عمليه", "عملية", "مشتريات", "تم سحب", "سحب"
        )
    )
    private val strong = KwSet(
        listOf(
            "debited", "credited", "purchase", "spent", "withdrawn", "transferred",
            "تم خصم", "تم ايداع", "تم استلام", "شراء", "تم سحب", "تم تحويل", "عمليه شراء", "عملية شراء"
        )
    )

    private val otpRx = Regex(
        "(?i)(\\botp\\b|one[- ]?time|verification code|security code|passcode|رمز التحقق|رمز التفعيل|كلمه المرور|" +
            "الرمز السري|كود التحقق|كود التفعيل|رمز الدخول|do not share|لا تشارك)"
    )
    private val promoRx = Regex(
        "(?i)(promo|offer|% off|discount up|congratulations|apply now|installment plan|عرض|عروض|كوبون|مبروك|" +
            "قسط|تقدم|للتقديم|احصل علي|استمتع)"
    )

    // ---------- المبالغ ----------
    private const val NUM = "(?:\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)"
    private const val CUR = "(?<![A-Za-z])(?:EGP|USD|EUR|GBP|SAR|AED|KWD|QAR|LE|L\\.E\\.?|E\\u00A3|\\u00A3|\\u0024|\\u20AC|" +
        "جنيه|ج\\.م|ج\\.\\s?م|دولار|ريال|درهم)(?![A-Za-z])"
    private val moneyRx = Regex("(?i)(?:($CUR)\\s*($NUM)|($NUM)\\s*($CUR))")
    private val labelAmountRx = Regex("(?i)(?:amount|مبلغ|بمبلغ|قيمه|value)[^\\d\\n]{0,15}($NUM)")
    private val balRx = Regex("(?i)(?:balance|bal\\.?|الرصيد|رصيدك|رصيد)[^\\d\\n]{0,30}?($NUM)")

    private fun toNum(s: String): Double? = s.replace(",", "").toDoubleOrNull()

    private fun normCur(c: String?): String? {
        if (c.isNullOrBlank()) return null
        val u = c.uppercase().replace(" ", "")
        return when {
            u in setOf("EGP", "LE", "L.E", "L.E.", "E\u00A3", "جنيه", "ج.م") -> "EGP"
            u == "\$" || u == "USD" || u == "دولار" -> "USD"
            u == "\u20AC" || u == "EUR" -> "EUR"
            u == "\u00A3" || u == "GBP" -> "GBP"
            u == "SAR" || u == "ريال" -> "SAR"
            u == "AED" || u == "درهم" -> "AED"
            else -> u
        }
    }

    // ---------- آخر 4 أرقام / التاجر / التاريخ ----------
    private val last4A = Regex("(?i)(?:\\*+|x+|\\.{2,}|#)\\s?(\\d{4})(?!\\d)(?![.,]\\d)")
    private val last4B = Regex(
        "(?i)(?:card|a/c|acct|account|ending|بطاقه|بطاقة|بطاقتك|حساب|حسابك|كارت)[^\\d\\n]{0,15}?(\\d{4})(?!\\d)(?![.,]\\d)"
    )

    private val merchLabel = Regex("(?i)(?:merchant|المتجر|التاجر)\\s*[:\\-]?\\s*([^\\n.,،;:]{2,40})")
    private val merchEn = Regex(
        "(?i)\\b(?:at|to|from|@)\\s+([A-Za-z][A-Za-z0-9&'\\-. *]{1,38}?)" +
            "(?=\\s+(?:on|at|using|with|via|ref|dated|card|a/c|acct|avail|bal|balance|\\d)|[.,;\\n]|$)"
    )
    private val merchAr = Regex(
        "(?:لدى|لدي|عند|الى|إلى|التاجر)\\s*[:\\-]?\\s*([\\u0600-\\u06FFA-Za-z0-9&'\\-. ]{2,40}?)" +
            "(?=\\s+(?:بتاريخ|يوم|في|علي|على|بطاقه|بطاقة|بطاقتك|رصيد|الرصيد|مبلغ|بمبلغ|\\d)|[.,،;\\n]|$)"
    )
    private val merchArFrom = Regex(
        "(?:من)\\s*[:\\-]?\\s*([\\u0600-\\u06FFA-Za-z0-9&'\\-. ]{2,40}?)" +
            "(?=\\s+(?:بتاريخ|يوم|في|علي|على|بطاقه|بطاقة|بطاقتك|رصيد|الرصيد|مبلغ|بمبلغ|\\d)|[.,،;\\n]|$)"
    )
    private val stopWords = setOf(
        "account", "card", "your", "a/c", "acct", "atm", "merchant", "you", "the", "balance", "bal", "date",
        "حساب", "بطاقه", "بطاقة", "بطاقتك", "حسابك", "الحساب", "رصيد", "رصيدك", "الرصيد", "ماكينه", "ماكينة", "ماكينات",
        "بطاقات", "المحفظه", "المحفظة", "محفظتك"
    )

    private val ymdRx = Regex("(\\d{4})[/\\-.](\\d{1,2})[/\\-.](\\d{1,2})(?:[ T,]+(\\d{1,2}):(\\d{2}))?")
    private val dmyRx = Regex("(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2,4})(?:[ T,]+(\\d{1,2}):(\\d{2}))?")

    private fun extractDate(body: String, fallback: Long): Long {
        fun build(y: Int, m: Int, d: Int, h: Int, mi: Int): Long? {
            if (m !in 1..12 || d !in 1..31 || h !in 0..23 || mi !in 0..59) return null
            val c = Calendar.getInstance()
            c.clear()
            c.set(y, m - 1, d, h, mi, 0)
            val t = c.timeInMillis
            return if (t in (fallback - 400L * 86_400_000L)..(fallback + 86_400_000L)) t else null
        }
        ymdRx.find(body)?.let { m ->
            val g = m.groupValues
            build(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toIntOrNull() ?: 0, g[5].toIntOrNull() ?: 0)?.let { return it }
        }
        dmyRx.find(body)?.let { m ->
            val g = m.groupValues
            var y = g[3].toInt()
            if (y < 100) y += 2000
            build(y, g[2].toInt(), g[1].toInt(), g[4].toIntOrNull() ?: 0, g[5].toIntOrNull() ?: 0)?.let { return it }
        }
        return fallback
    }

    private val cutWords = setOf(
        "بالبطاقه", "بالبطاقة", "المنتهيه", "المنتهية", "بطاقه", "بطاقة", "بطاقتك", "بتاريخ", "رقم", "عبر", "بواسطه", "بواسطة",
        "using", "via", "with", "card", "ref", "dated"
    )

    private fun cleanMerchant(s: String): String {
        val kept = ArrayList<String>()
        for (w in s.replace(Regex("\\s+"), " ").trim().split(" ")) {
            if (nrm(w) in cutWords) break
            kept.add(w)
        }
        return kept.joinToString(" ").trim().trimEnd('.', ',', '-', ':', ';').take(40)
    }

    private fun extractMerchant(body: String, bank: BankInfo?): String {
        bank?.merchantRegex?.let { rx ->
            runCatching { Regex(rx).find(body)?.groupValues?.getOrNull(1) }.getOrNull()
                ?.let { cleanMerchant(it) }?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        merchLabel.find(body)?.groupValues?.get(1)?.let { cleanMerchant(it) }?.takeIf { it.isNotEmpty() }?.let { return it }
        for (rx in listOf(merchAr, merchArFrom)) {
            for (m in rx.findAll(body)) {
                val v = cleanMerchant(m.groupValues[1])
                if (v.length >= 2 && nrm(v.split(" ").first()) !in stopWords) return v
            }
        }
        for (m in merchEn.findAll(body)) {
            val v = cleanMerchant(m.groupValues[1])
            if (v.length >= 2 && v.split(" ").first().lowercase() !in stopWords) return v
        }
        return ""
    }

    private fun minPos(a: Int, b: Int): Int = when {
        a < 0 -> b
        b < 0 -> a
        else -> minOf(a, b)
    }

    /** يعيد null إذا لم تكن الرسالة حركة مالية (OTP، دعاية، غير ذلك). */
    fun parse(rawBody: String, smsDate: Long, bank: BankInfo?): Parsed? {
        val body = normDigits(rawBody)
        val t = nrm(body)
        val hasStrong = strong.first(t) >= 0
        if (otpRx.containsMatchIn(t) && !hasStrong) return null
        if (promoRx.containsMatchIn(t) && !hasStrong) return null

        // الرصيد بعد العملية
        var balance: Double? = null
        var balRange: IntRange? = null
        val bm = balRx.find(body)
        if (bm != null) {
            balance = toNum(bm.groupValues[1])
            balRange = bm.range
        }

        // المبلغ: أول مبلغ مع عملة لا يقع داخل نص الرصيد
        var amount: Double? = null
        var currency: String? = null
        for (m in moneyRx.findAll(body)) {
            val br = balRange
            if (br != null && m.range.first <= br.last && m.range.last >= br.first) continue
            val g = m.groupValues
            val numStr = g[2].ifEmpty { g[3] }
            val curStr = g[1].ifEmpty { g[4] }
            amount = toNum(numStr)
            currency = normCur(curStr)
            if (amount != null && amount > 0) break
        }
        if (amount == null || amount <= 0) {
            val lm = labelAmountRx.find(body)
            if (lm != null) amount = toNum(lm.groupValues[1])
        }
        val amt = amount ?: return null
        if (amt <= 0) return null

        // نوع العملية
        val iAtm = atmStrong.first(t)
        var iCr = credit.first(t)
        var iTr = transfer.first(t)
        var iDb = debit.first(t)
        bank?.let {
            if (it.creditWords.isNotEmpty()) iCr = minPos(iCr, KwSet(it.creditWords.map(::nrm)).first(t))
            if (it.debitWords.isNotEmpty()) iDb = minPos(iDb, KwSet(it.debitWords.map(::nrm)).first(t))
        }
        val kind: String = when {
            iAtm >= 0 && iCr < 0 -> "ATM"
            else -> {
                val c = listOf("CREDIT" to iCr, "TRANSFER" to iTr, "DEBIT" to iDb).filter { it.second >= 0 }
                if (c.isEmpty()) return null
                var k = c.minByOrNull { it.second }!!.first
                if (k == "TRANSFER" && iCr >= 0) k = "CREDIT"
                k
            }
        }

        val last4 = last4A.find(body)?.groupValues?.get(1) ?: last4B.find(body)?.groupValues?.get(1)
        var merchant = extractMerchant(body, bank)
        if (kind == "ATM" && merchant.isEmpty()) merchant = "ATM"

        return Parsed(amt, currency, kind, last4, merchant, balance, extractDate(body, smsDate))
    }
}

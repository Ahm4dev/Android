package com.auditcafe.wallet.ai

data class CatInfo(val id: Long, val key: String, val label: String, val isIncome: Boolean)
data class NlResult(val type: String, val period: String, val categoryId: Long, val text: String)

/** فهم أسئلة مثل "كم صرفت على المطاعم الشهر الماضي" بقواعد بسيطة وبدون إنترنت. */
object NlQuery {
    private fun norm(s: String) = s.lowercase().replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه').replace('ـ', ' ')

    private fun stripAl(w: String) = if (w.startsWith("ال") && w.length > 4) w.substring(2) else w

    private val periods = listOf(
        "LAST" to listOf("الشهر الماضي", "الشهر الفايت", "الشهر الفائت", "الشهر اللي فات", "شهر فات", "last month", "previous month"),
        "3M" to listOf("3 شهور", "ثلاث شهور", "ثلاثه شهور", "3 اشهر", "3 months", "three months"),
        "TODAY" to listOf("النهارده", "اليوم", "today"),
        "WEEK" to listOf("هذا الاسبوع", "الاسبوع", "اسبوع", "this week", "week"),
        "YEAR" to listOf("هذه السنه", "هذا العام", "السنه", "العام", "this year", "year"),
        "MONTH" to listOf("هذا الشهر", "الشهر ده", "الشهر دا", "الشهر الحالي", "this month", "الشهر", "month")
    )
    private val incomeStarts = listOf("دخل", "ايراد", "كسب", "استلم", "income", "earn", "receiv", "salary")

    private val synonyms = mapOf(
        "cat_food" to listOf("مطاعم", "مطعم", "اكل", "طعام", "اكلات", "food", "restaurants", "restaurant", "groceries", "dining"),
        "cat_transport" to listOf("مواصلات", "بنزين", "اوبر", "كريم", "uber", "careem", "transport", "taxi", "fuel"),
        "cat_bills" to listOf("فواتير", "فاتوره", "bills", "bill"),
        "cat_telecom" to listOf("اتصالات", "موبايل", "telecom", "mobile"),
        "cat_shopping" to listOf("تسوق", "shopping"),
        "cat_health" to listOf("صحه", "علاج", "دواء", "ادويه", "health", "medicine"),
        "cat_fun" to listOf("ترفيه", "entertainment", "fun"),
        "cat_edu" to listOf("تعليم", "دراسه", "education"),
        "cat_rent" to listOf("ايجار", "rent"),
        "cat_travel" to listOf("سفر", "travel", "trips"),
        "cat_clothes" to listOf("ملابس", "هدوم", "clothes", "clothing"),
        "cat_gifts" to listOf("هدايا", "gifts"),
        "cat_family" to listOf("عايله", "family"),
        "cat_cash" to listOf("سحب", "atm", "cash"),
        "cat_salary" to listOf("راتب", "مرتب", "salary"),
        "cat_bonus" to listOf("مكافاه", "بونص", "bonus")
    )

    private val stop = setOf(
        "كم", "كام", "ما", "هو", "هي", "مجموع", "اجمالي", "علي", "على", "في", "من", "الي", "الى", "عن", "هل", "كل",
        "صرفت", "انفقت", "دفعت", "دخلي", "دخل", "ايراد", "ايرادات", "كسبت", "استلمت", "الشهر", "الماضي", "الفايت", "الفائت", "هذا", "هذه",
        "ده", "دا", "الحالي", "اليوم", "النهارده", "الاسبوع", "السنه", "العام", "شهور", "اشهر",
        "spent", "spend", "how", "much", "did", "i", "on", "in", "for", "the", "my", "this", "last", "month", "week", "year",
        "today", "total", "what", "was", "is", "income", "earned", "received", "expenses", "expense", "months", "three",
        "previous", "ago", "show", "me", "to", "at", "a", "of"
    )

    fun parse(text: String, cats: List<CatInfo>): NlResult? {
        val q = norm(text)
        var period: String? = null
        for ((k, phrases) in periods) {
            if (phrases.any { q.contains(norm(it)) }) { period = k; break }
        }
        val tokens = q.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        var income = tokens.any { t -> incomeStarts.any { t.startsWith(it) } }

        var cat: CatInfo? = null
        val used = HashSet<String>()
        outer@ for (c in cats) {
            val syn = (synonyms[c.key] ?: emptyList()).map { stripAl(norm(it)) }
            for (t in tokens) {
                val st = stripAl(t)
                if (st in syn) { cat = c; used += t; break@outer }
            }
            val lab = norm(c.label)
            if (lab.length >= 3 && q.contains(lab)) { cat = c; used += lab.split(" "); break }
        }
        if (cat != null && cat.isIncome) income = true

        var merchant = ""
        if (cat == null) {
            merchant = tokens.filter { it.length >= 3 && it !in stop && it.toDoubleOrNull() == null && it !in used }.joinToString(" ")
        }
        val hasTypeWord = tokens.any { it in setOf("صرفت", "انفقت", "دفعت", "spent", "spend", "expense", "expenses") } || income
        if (cat == null && merchant.isEmpty() && period == null && !hasTypeWord) return null
        return NlResult(if (income) "INCOME" else "EXPENSE", period ?: "MONTH", cat?.id ?: 0L, merchant)
    }
}

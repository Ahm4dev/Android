package com.auditcafe.wallet.ai

import com.auditcafe.wallet.data.AppDatabase
import com.auditcafe.wallet.data.MerchantMap
import java.util.Calendar
import java.util.Locale
import kotlin.math.sqrt

data class Insight(val kind: String, val num1: Double = 0.0, val num2: Double = 0.0, val label: String = "")

/**
 * ذكاء اصطناعي محلي بدون إنترنت:
 *  1) تصنيف تلقائي: يتعلم من اختيارات المستخدم (merchant_map) ثم كلمات مفتاحية عربي/إنجليزي.
 *  2) توقع مصروف نهاية الشهر + تنبيهات الميزانية + اكتشاف المصروفات الشاذة (z-score).
 */
object SmartEngine {

    private val rules: List<Pair<String, List<String>>> = listOf(
        "cat_food" to listOf(
            "kfc", "mcdonald", "mcdonalds", "burger", "pizza", "talabat", "elmenus", "restaurant", "cafe", "coffee",
            "starbucks", "costa", "supermarket", "carrefour", "spinneys", "hyper", "kazyon", "metro market", "bakery",
            "مطعم", "كافيه", "قهوة", "بيتزا", "طلبات", "كنتاكي", "ماكدونالدز", "سوبر ماركت", "كارفور", "سبينس",
            "خير زمان", "بقالة", "هايبر", "مخبز", "حلواني"
        ),
        "cat_transport" to listOf(
            "uber", "careem", "indrive", "didi", "swvl", "petrol", "gasoline", "taxi", "metro", "chillout", "wataniya",
            "mobil", "بنزينة", "بنزين", "وطنية", "تاكسي", "مترو", "اوبر", "أوبر", "كريم", "قطار", "جراج", "موقف"
        ),
        "cat_bills" to listOf(
            "electric", "electricity", "water", "gas bill", "bill", "fawry", "internet", "كهرباء", "مياه", "غاز",
            "فاتورة", "فوري", "ايجار شقة", "ضرائب"
        ),
        "cat_telecom" to listOf(
            "vodafone", "orange", "etisalat", "we", "telecom", "فودافون", "اورنج", "أورنج", "اتصالات", "وي", "رصيد", "شحن"
        ),
        "cat_shopping" to listOf(
            "amazon", "noon", "jumia", "aliexpress", "shein", "ikea", "mall", "souq", "temu", "سيتي سنتر", "امازون",
            "نون", "جوميا", "مول", "سوق"
        ),
        "cat_health" to listOf(
            "pharmacy", "hospital", "clinic", "lab", "dental", "doctor", "seif", "ezaby", "صيدلية", "مستشفى", "عيادة",
            "معمل", "دكتور", "العزبي", "سيف", "أسنان", "اشعة"
        ),
        "cat_fun" to listOf(
            "netflix", "spotify", "cinema", "vox", "shahid", "playstation", "steam", "youtube", "anghami", "game",
            "سينما", "شاهد", "العاب", "ألعاب", "انغامي"
        ),
        "cat_edu" to listOf(
            "school", "university", "udemy", "course", "coursera", "academy", "مدرسة", "جامعة", "كورس", "اكاديمية",
            "أكاديمية", "تعليم", "مصاريف دراسية"
        ),
        "cat_rent" to listOf("rent", "ايجار", "إيجار"),
        "cat_travel" to listOf(
            "airline", "egyptair", "booking", "airbnb", "hotel", "airport", "فندق", "مصر للطيران", "مطار", "طيران", "سفر"
        ),
        "cat_clothes" to listOf(
            "zara", "h&m", "defacto", "lcw", "max", "adidas", "nike", "ملابس", "ديفاكتو", "حذاء"
        ),
        "cat_gifts" to listOf("gift", "هدية", "هدايا", "زهور", "flowers")
    )

    fun merchantKey(m: String): String = TextKey.merchantKey(m)

    fun keywordCategory(merchant: String, kind: String, body: String): String {
        when (kind) {
            "ATM" -> return "cat_cash"
            "TRANSFER" -> return "cat_transfer"
            "CREDIT" -> {
                val t = (merchant + " " + body).lowercase()
                return when {
                    listOf("salary", "payroll", "راتب", "مرتب").any { t.contains(it) } -> "cat_salary"
                    listOf("refund", "reversal", "cashback", "استرداد", "ارجاع", "إرجاع").any { t.contains(it) } -> "cat_refund"
                    else -> "cat_other_inc"
                }
            }
        }
        val t = merchant.lowercase()
        if (t.isBlank()) return "cat_other_exp"
        val tokens = t.split(Regex("[^\\p{L}0-9&]+")).filter { it.isNotEmpty() }.toSet()
        for ((cat, words) in rules) {
            for (w in words) {
                val wl = w.lowercase()
                if (wl in tokens || (wl.length >= 4 && t.contains(wl))) return cat
            }
        }
        return "cat_other_exp"
    }

    suspend fun suggestCategory(db: AppDatabase, merchant: String, kind: String, body: String): Long? {
        val key = merchantKey(merchant)
        if (key.isNotEmpty() && kind != "ATM" && kind != "TRANSFER") {
            db.merchants().get(key)?.let { return it.categoryId }
        }
        return db.categories().byName(keywordCategory(merchant, kind, body))?.id
    }

    /** يتعلم من اختيار المستخدم: التاجر → التصنيف. */
    suspend fun learn(db: AppDatabase, merchant: String, categoryId: Long) {
        val key = merchantKey(merchant)
        if (key.isEmpty()) return
        val old = db.merchants().get(key)
        db.merchants().put(
            MerchantMap(key, categoryId, if (old != null && old.categoryId == categoryId) old.hits + 1 else 1)
        )
    }

    fun buildInsights(
        spent: Double,
        lastMonthSpent: Double,
        topCategory: Pair<String, Double>?,
        budgetUsage: List<Triple<String, Double, Double>>   // اسم، مصروف، ميزانية
    ): List<Insight> {
        val out = ArrayList<Insight>()
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (day >= 3 && spent > 0) {
            val forecast = spent / day * days
            out += Insight("forecast", forecast)
            if (lastMonthSpent > 0) {
                out += Insight("mom", (forecast - lastMonthSpent) / lastMonthSpent * 100.0)
            }
        }
        if (topCategory != null && spent > 0) {
            out += Insight("top", topCategory.second / spent * 100.0, 0.0, topCategory.first)
        }
        for ((name, s, b) in budgetUsage) {
            if (b > 0 && s / b >= 0.8) out += Insight("budget", s / b * 100.0, 0.0, name)
        }
        return out
    }

    /** مصروفات الأسبوع الأخير التي تزيد عن متوسط تصنيفها بأكثر من 3 انحرافات معيارية. */
    suspend fun anomalies(db: AppDatabase): List<Insight> {
        val now = System.currentTimeMillis()
        val rows = db.txns().expenseRows(now - 90L * 86_400_000L)
        if (rows.size < 10) return emptyList()
        val stats = rows.groupBy { it.categoryId }.mapValues { (_, l) ->
            val n = l.size
            val mean = l.sumOf { it.amount } / n
            val sd = sqrt(l.sumOf { (it.amount - mean) * (it.amount - mean) } / n)
            Triple(n, mean, sd)
        }
        val out = ArrayList<Insight>()
        for (t in db.txns().recentExpenses(now - 7L * 86_400_000L)) {
            val s = stats[t.categoryId] ?: continue
            if (s.first >= 8 && s.third > 0 && t.amount > s.second + 3 * s.third) {
                out += Insight("anomaly", t.amount, s.second, t.merchant)
            }
        }
        return out.take(3)
    }
}

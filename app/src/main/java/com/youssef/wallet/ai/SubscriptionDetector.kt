package com.youssef.wallet.ai

data class TxRec(val merchant: String, val amount: Double, val date: Long, val accountId: Long, val categoryId: Long?)
data class SubHint(
    val merchant: String, val amount: Double, val period: String, val lastDate: Long,
    val accountId: Long, val categoryId: Long?, val count: Int
)

/** يكتشف المصروفات المتكررة (اشتراكات) من تكرار نفس التاجر بنفس المبلغ تقريباً وفواصل زمنية منتظمة. */
object SubscriptionDetector {
    private const val DAY = 86_400_000.0

    fun detect(list: List<TxRec>, existingKeys: Set<String>, now: Long): List<SubHint> {
        val out = ArrayList<SubHint>()
        for ((key, group) in list.groupBy { TextKey.merchantKey(it.merchant) }) {
            if (key.isBlank() || key in existingKeys || group.size < 3) continue
            val g = group.sortedBy { it.date }
            val amounts = g.map { it.amount }.sorted()
            val median = amounts[amounts.size / 2]
            if (median <= 0 || g.any { kotlin.math.abs(it.amount - median) > median * 0.10 }) continue
            val gaps = g.zipWithNext { a, b -> (b.date - a.date) / DAY }
            val avg = gaps.average()
            val period = when {
                avg in 25.0..35.0 && gaps.all { it in 20.0..40.0 } -> "MONTHLY"
                avg in 6.0..8.0 && gaps.all { it in 5.0..10.0 } -> "WEEKLY"
                else -> continue
            }
            val last = g.last()
            if ((now - last.date) / DAY > avg * 1.6) continue
            out += SubHint(last.merchant, median, period, last.date, last.accountId, last.categoryId, g.size)
        }
        return out.sortedByDescending { it.amount }
    }
}

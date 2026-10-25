package com.youssef.wallet.ai

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** دوال نقية (بدون أندرويد) قابلة للاختبار. */
object TextKey {
    fun merchantKey(m: String): String =
        m.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}]+"), " ").trim().take(30)

    /** تقريب الميزانية المقترحة لرقم "لطيف". */
    fun niceRound(v: Double): Double = when {
        v <= 0 -> 0.0
        v < 200 -> ceil(v / 10) * 10
        v < 2000 -> ceil(v / 50) * 50
        else -> ceil(v / 100) * 100
    }
}

/** تقسيم فاتورة: يعيد (نصيبي، نصيب كل شخص آخر). الباقي من الفكة عليّ. */
object SplitMath {
    private fun r2(v: Double) = round(v * 100) / 100

    fun split(total: Double, tipPercent: Double, parts: Int): Pair<Double, Double> {
        require(parts >= 1)
        val t = r2(total * (1 + tipPercent / 100.0))
        val each = floor(t / parts * 100) / 100
        val mine = r2(t - each * (parts - 1))
        return mine to each
    }
}

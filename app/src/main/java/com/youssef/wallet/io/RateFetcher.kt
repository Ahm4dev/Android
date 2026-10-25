package com.youssef.wallet.io

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** يجلب أسعار الصرف (مقابل الدولار) من واجهة مجانية، مع مصدر احتياطي. */
object RateFetcher {
    private val urls = listOf(
        "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json",
        "https://latest.currency-api.pages.dev/v1/currencies/usd.json"
    )

    /** يعيد خريطة (كود العملة بحروف كبيرة -> عدد وحداتها لكل 1 دولار) أو null عند الفشل. */
    fun fetch(): Map<String, Double>? {
        for (u in urls) {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                val text = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                val o = JSONObject(text).getJSONObject("usd")
                val m = HashMap<String, Double>()
                for (k in o.keys()) m[k.uppercase()] = o.getDouble(k)
                if (m.isNotEmpty()) return m
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** سعر أونصة الذهب بالدولار، أو null عند الفشل. */
    fun fetchGold(): Double? {
        try {
            val c = URL("https://api.gold-api.com/price/XAU").openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            val text = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            return JSONObject(text).getDouble("price").takeIf { it > 0 }
        } catch (_: Exception) {
            return null
        }
    }
}

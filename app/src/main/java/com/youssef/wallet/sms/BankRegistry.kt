package com.youssef.wallet.sms

import android.content.Context
import com.youssef.wallet.data.BankRuleRow
import org.json.JSONObject

data class BankInfo(
    val name: String,
    val senders: List<String>,
    val merchantRegex: String? = null,
    val debitWords: List<String> = emptyList(),
    val creditWords: List<String> = emptyList(),
    val trusted: Boolean = false,
    val custom: Boolean = false
)

/** سجل البنوك: قواعد مدمجة (assets/bank_rules.json) + بنوك يضيفها المستخدم من الإعدادات. */
object BankRegistry {
    @Volatile private var builtin: List<BankInfo>? = null

    private fun loadBuiltin(ctx: Context): List<BankInfo> =
        builtin ?: synchronized(this) {
            builtin ?: runCatching {
                val text = ctx.assets.open("bank_rules.json").bufferedReader().use { it.readText() }
                val arr = JSONObject(text).getJSONArray("banks")
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    fun list(k: String): List<String> =
                        o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                    BankInfo(
                        name = o.getString("name"),
                        senders = list("senders"),
                        merchantRegex = o.optString("merchantRegex").takeIf { it.isNotBlank() },
                        debitWords = list("debitWords"),
                        creditWords = list("creditWords")
                    )
                }
            }.getOrDefault(emptyList()).also { builtin = it }
        }

    fun merge(ctx: Context, rows: List<BankRuleRow>): List<BankInfo> {
        val byName = rows.associateBy { it.name }
        val base = loadBuiltin(ctx).map { b -> byName[b.name]?.let { b.copy(trusted = it.trusted) } ?: b }
        val custom = rows.filter { it.custom }.map {
            BankInfo(
                name = it.name,
                senders = listOf(it.sender),
                debitWords = split(it.debitWords),
                creditWords = split(it.creditWords),
                trusted = it.trusted,
                custom = true
            )
        }
        return base + custom
    }

    private fun split(s: String) = s.split(',', '،', ';').map { it.trim() }.filter { it.isNotEmpty() }

    fun norm(s: String): String = s.uppercase().filter { it.isLetterOrDigit() }

    fun match(banks: List<BankInfo>, sender: String): BankInfo? {
        val s = norm(sender)
        if (s.isEmpty()) return null
        return banks.firstOrNull { b ->
            b.senders.any { p ->
                val n = norm(p)
                n.isNotEmpty() && (s == n || s.contains(n))
            }
        }
    }
}

package com.youssef.wallet.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class SavedFilter(
    val name: String, val q: String, val type: String, val accountId: Long, val categoryId: Long, val period: String
)

/** إعدادات التطبيق (SharedPreferences) مع حالة Compose قابلة للمراقبة. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE)

    var biometric by mutableStateOf(sp.getBoolean("biometric", false))
        private set
    var lockTimeout by mutableStateOf(sp.getInt("lock_timeout", 30))
        private set
    var secureScreen by mutableStateOf(sp.getBoolean("secure_screen", true))
        private set
    var autoRegister by mutableStateOf(sp.getBoolean("auto_register", false))
        private set
    var notifCapture by mutableStateOf(sp.getBoolean("notif_capture", false))
        private set
    var baseCurrency by mutableStateOf(sp.getString("base", "EGP") ?: "EGP")
        private set
    var ratesUpdated by mutableStateOf(sp.getLong("rates_updated", 0L))
        private set
    var goldUsdOz by mutableStateOf(sp.getString("gold_oz", "0")?.toDoubleOrNull() ?: 0.0)
        private set
    var reminders by mutableStateOf(sp.getBoolean("reminders", true))
        private set
    var lastReminderDay by mutableStateOf(sp.getLong("last_reminder_day", 0L))
        private set
    var pinHash by mutableStateOf(sp.getString("pin_hash", "") ?: "")
        private set
    var autoBackup by mutableStateOf(sp.getBoolean("auto_backup", false))
        private set
    var autoBackupUri by mutableStateOf(sp.getString("auto_backup_uri", "") ?: "")
        private set
    var autoBackupPassEnc by mutableStateOf(sp.getString("auto_backup_pass", "") ?: "")
        private set
    var lastAutoBackup by mutableStateOf(sp.getLong("last_auto_backup", 0L))
        private set

    val hasPin: Boolean get() = pinHash.isNotEmpty()
    val lockEnabled: Boolean get() = biometric || hasPin

    fun setBiometric(v: Boolean) { biometric = v; sp.edit().putBoolean("biometric", v).apply() }
    fun setLockTimeout(v: Int) { lockTimeout = v; sp.edit().putInt("lock_timeout", v).apply() }
    fun setSecureScreen(v: Boolean) { secureScreen = v; sp.edit().putBoolean("secure_screen", v).apply() }
    fun setAutoRegister(v: Boolean) { autoRegister = v; sp.edit().putBoolean("auto_register", v).apply() }
    fun setNotifCapture(v: Boolean) { notifCapture = v; sp.edit().putBoolean("notif_capture", v).apply() }
    fun setBaseCurrency(v: String) { baseCurrency = v; sp.edit().putString("base", v).apply() }
    fun setRatesUpdated(v: Long) { ratesUpdated = v; sp.edit().putLong("rates_updated", v).apply() }
    fun setGold(v: Double) { goldUsdOz = v; sp.edit().putString("gold_oz", v.toString()).apply() }
    fun setReminders(v: Boolean) { reminders = v; sp.edit().putBoolean("reminders", v).apply() }
    fun setLastReminderDay(v: Long) { lastReminderDay = v; sp.edit().putLong("last_reminder_day", v).apply() }
    fun setPin(hash: String) { pinHash = hash; sp.edit().putString("pin_hash", hash).apply() }
    fun setAutoBackup(v: Boolean) { autoBackup = v; sp.edit().putBoolean("auto_backup", v).apply() }
    fun setAutoBackupUri(v: String) { autoBackupUri = v; sp.edit().putString("auto_backup_uri", v).apply() }
    fun setAutoBackupPassEnc(v: String) { autoBackupPassEnc = v; sp.edit().putString("auto_backup_pass", v).apply() }
    fun setLastAutoBackup(v: Long) { lastAutoBackup = v; sp.edit().putLong("last_auto_backup", v).apply() }

    // ---------- الفلاتر المحفوظة ----------
    val savedFilters = mutableStateListOf<SavedFilter>().also { it.addAll(loadFilters()) }

    private fun loadFilters(): List<SavedFilter> = try {
        val a = JSONArray(sp.getString("saved_filters", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            SavedFilter(o.getString("n"), o.getString("q"), o.getString("t"), o.getLong("a"), o.getLong("c"), o.getString("p"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun persistFilters() {
        val a = JSONArray()
        savedFilters.forEach {
            a.put(JSONObject().put("n", it.name).put("q", it.q).put("t", it.type).put("a", it.accountId).put("c", it.categoryId).put("p", it.period))
        }
        sp.edit().putString("saved_filters", a.toString()).apply()
    }

    fun addFilter(f: SavedFilter) {
        val i = savedFilters.indexOfFirst { it.name == f.name }
        if (i >= 0) savedFilters[i] = f else savedFilters.add(f)
        persistFilters()
    }

    fun removeFilter(name: String) {
        savedFilters.removeAll { it.name == name }
        persistFilters()
    }
}

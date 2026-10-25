package com.youssef.wallet.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.room.withTransaction
import com.youssef.wallet.R
import com.youssef.wallet.WalletApp
import com.youssef.wallet.ai.Insight
import com.youssef.wallet.ai.SmartEngine
import com.youssef.wallet.ai.CatInfo
import com.youssef.wallet.ai.NlQuery
import com.youssef.wallet.ai.SplitMath
import com.youssef.wallet.ai.SubHint
import com.youssef.wallet.ai.SubscriptionDetector
import com.youssef.wallet.ai.TextKey
import com.youssef.wallet.ai.TxRec
import com.youssef.wallet.data.Debt
import com.youssef.wallet.io.AutoBackup
import com.youssef.wallet.io.ReceiptStore
import kotlinx.coroutines.flow.first
import com.youssef.wallet.data.Account
import com.youssef.wallet.data.AppDatabase
import com.youssef.wallet.data.BankRuleRow
import com.youssef.wallet.data.CatTotal
import com.youssef.wallet.data.Category
import com.youssef.wallet.data.Goal
import com.youssef.wallet.data.MonthTotal
import com.youssef.wallet.data.Rate
import com.youssef.wallet.data.Recurring
import com.youssef.wallet.data.RecurringProcessor
import com.youssef.wallet.data.Suggested
import com.youssef.wallet.data.Txn
import com.youssef.wallet.data.TypeTotal
import com.youssef.wallet.io.BackupIO
import com.youssef.wallet.io.RateFetcher
import com.youssef.wallet.io.XlsxIO
import com.youssef.wallet.sms.BankInfo
import com.youssef.wallet.sms.BankRegistry
import com.youssef.wallet.sms.SmsImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar

data class Filter(
    val q: String = "",
    val type: String = "",
    val accountId: Long = 0,
    val categoryId: Long = 0,
    val period: String = "ALL"
)

data class Slice(val categoryId: Long?, val total: Double)
data class MonthBar(val ym: String, val income: Double, val expense: Double)
data class ReportData(
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val slices: List<Slice> = emptyList(),
    val months: List<MonthBar> = emptyList()
)

data class ScanState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val added: Int = 0,
    val finished: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    val db: AppDatabase = AppDatabase.get(ctx)
    val prefs = WalletApp.prefs

    private fun <T> Flow<T>.state(init: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.Eagerly, init)

    // ---------- بيانات أساسية ----------
    val accounts = db.accounts().all().state(emptyList())
    val categories = db.categories().all().state(emptyList())
    val balances: StateFlow<Map<Long, Double>> =
        db.txns().balances().map { l -> l.associate { it.accountId to it.balance } }.state(emptyMap())
    val rates: StateFlow<Map<String, Double>> =
        db.rates().all().map { l -> l.associate { it.currency to it.perUnit } }.state(emptyMap())
    val goals = db.goals().all().state(emptyList())
    val recurring = db.recurring().all().state(emptyList())
    val pending = db.suggested().pending().state(emptyList())
    val bankRows = db.bankRules().all().state(emptyList())
    val banks: StateFlow<List<BankInfo>> = bankRows.map { BankRegistry.merge(ctx, it) }.state(emptyList())
    val base = MutableStateFlow(prefs.baseCurrency)
    val debts = db.debts().all().state(emptyList())
    val txCount = db.txns().count().state(0)
    val recent = db.txns().recent(5).state(emptyList())

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val restartRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun msg(@StringRes id: Int, vararg a: Any) {
        messages.tryEmit(ctx.getString(id, *a))
    }

    // ---------- الصفحة الرئيسية ----------
    private val homeRange = MutableStateFlow(monthRange(0))

    fun refreshRange() {
        runRecurring()
        homeRange.value = monthRange(0)
        refreshAnomalies()
        refreshSubHints()
    }

    val totalBalance: StateFlow<Double> = combine(accounts, balances, rates, base) { acc, bal, r, b ->
        acc.sumOf { conv(bal[it.id] ?: it.initialBalance, it.currency, r, b) }
    }.state(0.0)

    val homeTotals: StateFlow<Pair<Double, Double>> = combine(
        homeRange.flatMapLatest { db.txns().typeTotals(it.first, it.second) }, rates, base
    ) { l, r, b -> sumTypes(l, r, b) }.state(0.0 to 0.0)

    val lastMonthExpense: StateFlow<Double> = combine(
        homeRange.flatMapLatest { flowOfLast() }, rates, base
    ) { l, r, b -> sumTypes(l, r, b).second }.state(0.0)

    private fun flowOfLast(): Flow<List<TypeTotal>> {
        val rg = monthRange(-1)
        return db.txns().typeTotals(rg.first, rg.second)
    }

    private fun sumTypes(l: List<TypeTotal>, r: Map<String, Double>, b: String): Pair<Double, Double> {
        var inc = 0.0
        var exp = 0.0
        l.forEach {
            val v = conv(it.total, it.currency, r, b)
            if (it.type == "INCOME") inc += v else exp += v
        }
        return inc to exp
    }

    private fun sumCats(l: List<CatTotal>, r: Map<String, Double>, b: String): Map<Long, Double> {
        val out = HashMap<Long, Double>()
        l.forEach { out[it.categoryId ?: -1L] = (out[it.categoryId ?: -1L] ?: 0.0) + conv(it.total, it.currency, r, b) }
        return out
    }

    val homeCats: StateFlow<Map<Long, Double>> = combine(
        homeRange.flatMapLatest { db.txns().catTotals("EXPENSE", it.first, it.second) }, rates, base
    ) { l, r, b -> sumCats(l, r, b) }.state(emptyMap())

    private val anomalies = MutableStateFlow<List<Insight>>(emptyList())

    private fun refreshAnomalies() {
        viewModelScope.launch(Dispatchers.IO) { anomalies.value = runCatching { SmartEngine.anomalies(db) }.getOrDefault(emptyList()) }
    }

    val insights: StateFlow<List<Insight>> = combine(
        homeTotals, lastMonthExpense, homeCats, categories, anomalies
    ) { totals, last, cats, catList, anom ->
        val spent = totals.second
        val byId = catList.associateBy { it.id }
        val top = cats.filterKeys { it > 0 }.maxByOrNull { it.value }
            ?.let { (id, v) -> (byId[id]?.name ?: "") to v }
        val budgets = catList.filter { it.monthlyBudget > 0 && !it.isIncome }
            .map { Triple(it.name, cats[it.id] ?: 0.0, it.monthlyBudget) }
        SmartEngine.buildInsights(spent, last, top?.takeIf { it.first.isNotEmpty() }, budgets) + anom
    }.state(emptyList())

    init {
        refreshAnomalies()
        runRecurring()
        if (System.currentTimeMillis() - prefs.ratesUpdated > 12L * 3600_000L) refreshRates()
    }

    // ---------- الحركات: بحث وفلاتر + Paging ----------
    val filter = MutableStateFlow(Filter())

    val txPaging: Flow<PagingData<Txn>> = filter.debounce(200).flatMapLatest { f ->
        val (from, to) = periodRange(f.period)
        Pager(PagingConfig(pageSize = 50, enablePlaceholders = false)) {
            db.txns().paged(f.q.trim(), f.type, f.accountId, f.categoryId, from, to)
        }.flow
    }.cachedIn(viewModelScope)

    fun setFilter(f: Filter) { filter.value = f }

    // ---------- التقارير ----------
    val reportPeriod = MutableStateFlow("MONTH")
    val reportType = MutableStateFlow("EXPENSE")

    val report: StateFlow<ReportData> = combine(reportPeriod, reportType) { p, t -> periodRange(p) to t }
        .flatMapLatest { (rg, t) ->
            combine(
                db.txns().typeTotals(rg.first, rg.second),
                db.txns().catTotals(t, rg.first, rg.second),
                db.txns().monthTotals(monthRange(-11).first),
                rates, base
            ) { tt, ct, mt, r, b -> buildReport(tt, ct, mt, r, b) }
        }.state(ReportData())

    private fun buildReport(
        tt: List<TypeTotal>, ct: List<CatTotal>, mt: List<MonthTotal>, r: Map<String, Double>, b: String
    ): ReportData {
        val (inc, exp) = sumTypes(tt, r, b)
        val slices = ct.groupBy { it.categoryId }
            .map { (k, l) -> Slice(k, l.sumOf { conv(it.total, it.currency, r, b) }) }
            .sortedByDescending { it.total }
        val keys = (11 downTo 0).map { i ->
            val c = Calendar.getInstance()
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.MONTH, -i)
            String.format("%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
        }
        val byMonth = mt.groupBy { it.ym }
        val months = keys.map { k ->
            val l = byMonth[k].orEmpty()
            MonthBar(
                k,
                l.filter { it.type == "INCOME" }.sumOf { conv(it.total, it.currency, r, b) },
                l.filter { it.type == "EXPENSE" }.sumOf { conv(it.total, it.currency, r, b) }
            )
        }
        return ReportData(inc, exp, slices, months)
    }

    // ---------- عمليات الحركات ----------
    fun saveTxn(t: Txn) {
        viewModelScope.launch(Dispatchers.IO) {
            if (t.id == 0L) db.txns().insert(t) else db.txns().update(t)
            if (t.categoryId != null && t.merchant.isNotBlank() && t.type != "TRANSFER") {
                SmartEngine.learn(db, t.merchant, t.categoryId)
            }
            refreshAnomalies()
        }
    }

    fun deleteTxn(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val att = db.txns().get(id)?.attachment
            db.txns().delete(id)
            ReceiptStore.delete(ctx, att)
        }
    }

    // ---------- الحسابات / التصنيفات / الأهداف / الأسعار ----------
    fun saveAccount(a: Account) {
        viewModelScope.launch(Dispatchers.IO) { if (a.id == 0L) db.accounts().insert(a) else db.accounts().update(a) }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val atts = db.txns().attachmentsOfAccount(id)
            db.withTransaction {
                db.txns().deleteByAccount(id)
                db.recurring().deleteByAccount(id)
                db.accounts().delete(id)
            }
            atts.forEach { ReceiptStore.delete(ctx, it) }
        }
    }

    fun saveCategory(c: Category) {
        viewModelScope.launch(Dispatchers.IO) { if (c.id == 0L) db.categories().insert(c) else db.categories().update(c) }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                db.txns().clearCategory(id)
                db.recurring().clearCategory(id)
                db.categories().delete(id)
            }
        }
    }

    fun saveGoal(g: Goal) {
        viewModelScope.launch(Dispatchers.IO) { if (g.id == 0L) db.goals().insert(g) else db.goals().update(g) }
    }

    fun deleteGoal(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.goals().delete(id) }
    }

    fun setRate(cur: String, v: Double) {
        viewModelScope.launch(Dispatchers.IO) { db.rates().put(Rate(cur, v)) }
    }

    fun setBase(cur: String) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) { db.rates().clear() }
            prefs.setBaseCurrency(cur)
            base.value = cur
            refreshRates()
        }
    }

    /** يحدّث أسعار الصرف من الإنترنت. الأسعار المحفوظة تبقى عند الفشل (يعمل بدون إنترنت). */
    fun refreshRates(manual: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val usd = RateFetcher.fetch()
            val b = base.value
            val baseUsd = usd?.get(b)
            if (usd == null || baseUsd == null || baseUsd <= 0) {
                if (manual) msg(R.string.rates_failed)
                return@launch
            }
            val list = (accounts.value.map { it.currency } + goals.value.map { it.currency } + CURRENCIES)
                .distinct().filter { it != b }
            for (c in list) {
                val perUsd = usd[c]
                if (perUsd != null && perUsd > 0) db.rates().put(Rate(c, baseUsd / perUsd))
            }
            val oz = RateFetcher.fetchGold() ?: usd["XAU"]?.takeIf { it > 0 }?.let { 1.0 / it }
            if (oz != null && oz > 0) prefs.setGold(oz)
            prefs.setRatesUpdated(System.currentTimeMillis())
            if (manual) msg(R.string.rates_updated)
        }
    }

    // ---------- المعاملات المتكررة ----------
    fun runRecurring() {
        viewModelScope.launch(Dispatchers.IO) {
            val n = runCatching { RecurringProcessor.runDue(ctx) }.getOrDefault(0)
            if (n > 0) {
                msg(R.string.rec_added, n)
                refreshAnomalies()
            }
        }
    }

    fun saveRecurring(r: Recurring) {
        viewModelScope.launch(Dispatchers.IO) {
            if (r.id == 0L) db.recurring().insert(r) else db.recurring().update(r)
            val n = runCatching { RecurringProcessor.runDue(ctx) }.getOrDefault(0)
            if (n > 0) { msg(R.string.rec_added, n); refreshAnomalies() }
        }
    }

    fun toggleRecurring(r: Recurring, on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.recurring().update(if (on) r.copy(active = true, nextDate = RecurringProcessor.nextAfterNow(r)) else r.copy(active = false))
        }
    }

    fun deleteRecurring(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.recurring().delete(id) }
    }

    // ---------- الحركات المقترحة ----------
    fun accept(s: Suggested, t: Txn) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                db.txns().insert(t.copy(id = 0, hash = s.hash, source = s.source + "_OK"))
                db.suggested().setStatus(s.id, "ACCEPTED")
            }
            if (t.categoryId != null && t.merchant.isNotBlank()) SmartEngine.learn(db, t.merchant, t.categoryId)
            val l4 = s.last4
            if (l4 != null) {
                val a = db.accounts().get(t.accountId)
                if (a != null && a.last4.isBlank()) db.accounts().update(a.copy(last4 = l4))
            }
            refreshAnomalies()
        }
    }

    fun ignore(s: Suggested) {
        viewModelScope.launch(Dispatchers.IO) { db.suggested().setStatus(s.id, "IGNORED") }
    }

    fun ignoreAll() {
        viewModelScope.launch(Dispatchers.IO) { db.suggested().ignoreAll() }
    }

    // ---------- البنوك ----------
    fun setTrusted(b: BankInfo, trusted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.bankRules().upsert(
                BankRuleRow(
                    name = b.name, sender = b.senders.firstOrNull().orEmpty(), custom = b.custom, trusted = trusted,
                    debitWords = b.debitWords.joinToString(","), creditWords = b.creditWords.joinToString(",")
                )
            )
        }
    }

    fun addBank(name: String, sender: String, debitWords: String, creditWords: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.bankRules().upsert(BankRuleRow(name.trim(), sender.trim(), true, false, debitWords, creditWords))
        }
    }

    fun deleteBank(name: String) {
        viewModelScope.launch(Dispatchers.IO) { db.bankRules().delete(name) }
    }

    // ---------- مسح صندوق الرسائل ----------
    val scan = MutableStateFlow(ScanState())

    fun scanInbox() {
        if (scan.value.running) return
        viewModelScope.launch(Dispatchers.IO) {
            scan.value = ScanState(running = true)
            try {
                val r = SmsImporter.scan(ctx) { d, t, a -> scan.value = ScanState(true, d, t, a) }
                scan.value = ScanState(false, r.first, r.first, r.second, true)
            } catch (e: Exception) {
                scan.value = ScanState()
                msg(R.string.error_generic, e.message ?: "")
            }
        }
    }

    // ---------- Excel ----------
    fun exportExcel(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val accs = db.accounts().list().associateBy { it.id }
                val cats = db.categories().list().associateBy { it.id }
                var count = 0
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
                    XlsxIO.export(out, "Transactions", listOf("Date", "Type", "Amount", "Account", "Category", "Merchant", "Note")) { emit ->
                        var after = 0L
                        while (true) {
                            val chunk = runBlocking { db.txns().chunk(after, 5000) }
                            if (chunk.isEmpty()) break
                            for (t in chunk) {
                                val accName = accs[t.accountId]?.name?.let { label(ctx, it) }.orEmpty()
                                val third = if (t.type == "TRANSFER") {
                                    t.toAccountId?.let { accs[it] }?.name?.let { label(ctx, it) }.orEmpty()
                                } else {
                                    t.categoryId?.let { cats[it] }?.name?.let { label(ctx, it) }.orEmpty()
                                }
                                emit(listOf(fmtIso(t.date), t.type, t.amount, accName, third, t.merchant, t.note))
                                count++
                            }
                            after = chunk.last().id
                        }
                    }
                }
                msg(R.string.export_done, count)
            } catch (e: Exception) {
                msg(R.string.error_generic, e.message ?: "")
            }
        }
    }

    private fun parseType(x: String?): String {
        val s = normDigits(x.orEmpty().trim().lowercase())
        return when {
            s.startsWith("inc") || s == "in" || s.contains("دخل") || s.contains("ايراد") || s.contains("إيراد") -> "INCOME"
            s.startsWith("tran") || s.contains("تحويل") -> "TRANSFER"
            else -> "EXPENSE"
        }
    }

    fun importExcel(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rows = ctx.contentResolver.openInputStream(uri)!!.use { XlsxIO.read(it) }
                var data = rows.filter { r -> r.any { it.isNotBlank() } }
                if (data.isNotEmpty() && parseAmount(data[0].getOrNull(2)) == null) data = data.drop(1)

                val accMap = HashMap<String, Account>()
                db.accounts().list().forEach { accMap[it.name.lowercase()] = it; accMap[label(ctx, it.name).lowercase()] = it }
                val catMap = HashMap<String, Category>()
                db.categories().list().forEach {
                    catMap[it.name.lowercase() + "|" + it.isIncome] = it
                    catMap[label(ctx, it.name).lowercase() + "|" + it.isIncome] = it
                }

                suspend fun accountFor(name: String): Account {
                    val n = name.trim().ifBlank { "acc_cash" }
                    accMap[n.lowercase()]?.let { return it }
                    val a = Account(name = n, currency = base.value)
                    val created = a.copy(id = db.accounts().insert(a))
                    accMap[n.lowercase()] = created
                    return created
                }

                val out = ArrayList<Txn>(data.size)
                var skipped = 0
                for (r in data) {
                    val date = parseDate(r.getOrNull(0))
                    val amt = parseAmount(r.getOrNull(2))
                    if (date == null || amt == null || amt <= 0) { skipped++; continue }
                    var type = parseType(r.getOrNull(1))
                    val acc = accountFor(r.getOrNull(3).orEmpty())
                    val third = r.getOrNull(4).orEmpty().trim()
                    var toId: Long? = null
                    var catId: Long? = null
                    if (type == "TRANSFER") {
                        if (third.isEmpty()) type = "EXPENSE" else toId = accountFor(third).id
                    }
                    if (type != "TRANSFER" && third.isNotEmpty()) {
                        val key = third.lowercase() + "|" + (type == "INCOME")
                        val c = catMap[key] ?: Category(name = third, isIncome = type == "INCOME").let {
                            val created = it.copy(id = db.categories().insert(it))
                            catMap[key] = created
                            created
                        }
                        catId = c.id
                    }
                    out.add(
                        Txn(
                            type = type, amount = amt, accountId = acc.id, toAccountId = toId, categoryId = catId,
                            merchant = r.getOrNull(5).orEmpty(), note = r.getOrNull(6).orEmpty(),
                            date = date, source = "IMPORT"
                        )
                    )
                }
                db.withTransaction { out.chunked(1000).forEach { db.txns().insertAll(it) } }
                msg(R.string.import_done, out.size, skipped)
            } catch (e: Exception) {
                msg(R.string.error_generic, e.message ?: "")
            }
        }
    }

    // ---------- النسخ الاحتياطي ----------
    fun backup(uri: Uri, pass: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use { BackupIO.backup(ctx, it, pass) }
                msg(R.string.backup_done)
            } catch (e: Exception) {
                msg(R.string.error_generic, e.message ?: "")
            }
        }
    }

    fun restore(uri: Uri, pass: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ctx.contentResolver.openInputStream(uri)!!.use { BackupIO.restore(ctx, it, pass) }
                restartRequest.emit(Unit)
            } catch (e: Exception) {
                msg(R.string.restore_failed)
            }
        }
    }

    // ================= الديون والقروض =================
    fun saveDebt(d: Debt) {
        viewModelScope.launch(Dispatchers.IO) { if (d.id == 0L) db.debts().insert(d) else db.debts().update(d) }
    }

    fun deleteDebt(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { db.debts().delete(id) }
    }

    /** تسجيل دفعة على دين. إن اختير حساب بنفس العملة تُسجَّل أيضاً حركة (دخل إن كنت مقرضاً، مصروف إن كنت مقترضاً). */
    fun payDebt(d: Debt, amount: Double, accountId: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            val pay = minOf(amount, d.amount - d.paid)
            if (pay <= 0) return@launch
            db.withTransaction {
                db.debts().update(d.copy(paid = d.paid + pay))
                val acc = accountId?.let { db.accounts().get(it) }
                if (acc != null && acc.currency == d.currency) {
                    db.txns().insert(
                        Txn(
                            type = if (d.lent) "INCOME" else "EXPENSE", amount = pay, accountId = acc.id,
                            merchant = d.person, note = ctx.getString(R.string.debt_payment_note), date = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    // ================= تقسيم الفاتورة =================
    fun saveSplit(total: Double, tip: Double, parts: Int, accountId: Long, categoryId: Long?, merchant: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val (mine, _) = SplitMath.split(total, tip, parts)
            db.txns().insert(
                Txn(
                    type = "EXPENSE", amount = mine, accountId = accountId, categoryId = categoryId, merchant = merchant,
                    note = ctx.getString(R.string.split_note, parts), date = System.currentTimeMillis(), source = "MANUAL"
                )
            )
            if (categoryId != null && merchant.isNotBlank()) SmartEngine.learn(db, merchant, categoryId)
            msg(R.string.split_saved)
        }
    }

    // ================= اكتشاف الاشتراكات =================
    val subHints = MutableStateFlow<List<SubHint>>(emptyList())
    private val dismissedSubs = HashSet<String>()

    fun refreshSubHints() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val list = db.txns().expensesWithMerchant(now - 200L * 86_400_000L)
                    .map { TxRec(it.merchant, it.amount, it.date, it.accountId, it.categoryId) }
                val existing = db.recurring().all().first().map { TextKey.merchantKey(it.merchant) }.toSet()
                subHints.value = SubscriptionDetector.detect(list, existing, now)
                    .filter { TextKey.merchantKey(it.merchant) !in dismissedSubs }
            }
        }
    }

    fun acceptSub(h: SubHint) {
        val cal = Calendar.getInstance().apply { timeInMillis = h.lastDate }
        val base = Recurring(
            type = "EXPENSE", amount = h.amount, accountId = h.accountId, categoryId = h.categoryId,
            merchant = h.merchant, period = h.period, nextDate = h.lastDate, anchorDay = cal.get(Calendar.DAY_OF_MONTH)
        )
        // الحركات السابقة مسجلة فعلاً؛ نبدأ من أول موعد قادم حتى لا تتكرر
        viewModelScope.launch(Dispatchers.IO) {
            db.recurring().insert(base.copy(nextDate = RecurringProcessor.nextAfterNow(base)))
            refreshSubHints()
        }
    }

    fun dismissSub(h: SubHint) {
        dismissedSubs += TextKey.merchantKey(h.merchant)
        subHints.value = subHints.value.filter { it.merchant != h.merchant }
    }

    // ================= ميزانيات مقترحة =================
    val budgetSuggestions = MutableStateFlow<List<Pair<Category, Double>>>(emptyList())

    /** متوسط آخر 3 أشهر كاملة لكل تصنيف لا توجد له ميزانية، مضروباً في 1.1 ومقرّباً. */
    fun computeBudgetSuggestions() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val r = rates.value
                val b = base.value
                val sums = HashMap<Long, Double>()
                for (off in -3..-1) {
                    val (f, t) = monthRange(off)
                    db.txns().catTotals("EXPENSE", f, t).first().forEach {
                        val id = it.categoryId ?: return@forEach
                        sums[id] = (sums[id] ?: 0.0) + conv(it.total, it.currency, r, b)
                    }
                }
                budgetSuggestions.value = categories.value
                    .filter { !it.isIncome && it.monthlyBudget <= 0 }
                    .mapNotNull { c ->
                        val avg = (sums[c.id] ?: 0.0) / 3.0
                        val sug = TextKey.niceRound(avg * 1.1)
                        if (sug > 0) c to sug else null
                    }
                    .sortedByDescending { it.second }
            }
        }
    }

    fun applyBudget(c: Category, v: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            db.categories().update(c.copy(monthlyBudget = v))
            budgetSuggestions.value = budgetSuggestions.value.filter { it.first.id != c.id }
        }
    }

    fun applyAllBudgets() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = budgetSuggestions.value
            db.withTransaction { list.forEach { (c, v) -> db.categories().update(c.copy(monthlyBudget = v)) } }
            budgetSuggestions.value = emptyList()
            msg(R.string.budgets_applied, list.size)
        }
    }

    // ================= بحث بلغة طبيعية =================
    data class NlAnswer(val total: Double, val count: Int, val result: com.youssef.wallet.ai.NlResult, val filter: Filter)

    val nlAnswer = MutableStateFlow<NlAnswer?>(null)
    val nlFailed = MutableStateFlow(false)

    fun askNl(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val infos = categories.value.map { CatInfo(it.id, it.name, label(ctx, it.name), it.isIncome) }
            val res = NlQuery.parse(text, infos)
            if (res == null) {
                nlAnswer.value = null; nlFailed.value = true; return@launch
            }
            nlFailed.value = false
            val (f, t) = periodRange(res.period)
            val rows = db.txns().sumWhere(res.type, f, t, res.categoryId, res.text)
            val total = rows.sumOf { conv(it.total, it.currency, rates.value, base.value) }
            nlAnswer.value = NlAnswer(
                total, rows.sumOf { it.n }, res,
                Filter(res.text, res.type, 0, res.categoryId, res.period)
            )
        }
    }

    fun clearNl() { nlAnswer.value = null; nlFailed.value = false }

    // ================= تقرير Excel =================
    /** تقرير الفترة المختارة في ملف Excel واحد: ملخص، توزيع التصنيفات، الأشهر، أكبر المصروفات. */
    fun exportReportExcel(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rep = report.value
                val (f, t) = periodRange(reportPeriod.value)
                val catMap = categories.value.associateBy { it.id }
                val accs = accounts.value.associateBy { it.id }
                val unc = ctx.getString(R.string.uncategorized)
                val sSum = ctx.getString(R.string.rep_summary)
                val sCat = ctx.getString(R.string.by_category)
                val sMon = ctx.getString(R.string.monthly_trend)
                val sTop = ctx.getString(R.string.rep_top)
                val top = db.txns().topExpenses(f, t, 20)
                val total = rep.slices.sumOf { it.total }
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
                    XlsxIO.export(
                        out, ctx.getString(R.string.tab_reports),
                        listOf(ctx.getString(R.string.rep_section), ctx.getString(R.string.rep_item), ctx.getString(R.string.amount) + " (" + base.value + ")", "%")
                    ) { emit ->
                        emit(listOf(sSum, ctx.getString(R.string.income), rep.income, ""))
                        emit(listOf(sSum, ctx.getString(R.string.expense), rep.expense, ""))
                        emit(listOf(sSum, ctx.getString(R.string.net), rep.income - rep.expense, ""))
                        rep.slices.forEach { sl ->
                            val n = sl.categoryId?.let { catMap[it] }?.let { label(ctx, it.name) } ?: unc
                            emit(listOf(sCat, n, sl.total, if (total > 0) Math.round(sl.total / total * 1000) / 10.0 else 0.0))
                        }
                        rep.months.forEach { m ->
                            emit(listOf(sMon, m.ym + " " + ctx.getString(R.string.income), m.income, ""))
                            emit(listOf(sMon, m.ym + " " + ctx.getString(R.string.expense), m.expense, ""))
                        }
                        top.forEach { x ->
                            val n = x.merchant.ifBlank { catMap[x.categoryId]?.let { label(ctx, it.name) } ?: unc }
                            emit(listOf(sTop, fmtDate(x.date).take(10) + "  " + n + " (" + (accs[x.accountId]?.currency ?: "") + ")", x.amount, ""))
                        }
                    }
                }
                msg(R.string.rep_done)
            } catch (e: Exception) {
                msg(R.string.error_generic, e.message ?: "")
            }
        }
    }

    // ================= نسخ احتياطي تلقائي (تشغيل يدوي) =================
    fun runAutoBackupNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching { AutoBackup.run(ctx) }.getOrDefault(false)
            msg(if (ok) R.string.backup_done else R.string.auto_backup_failed)
        }
    }
}

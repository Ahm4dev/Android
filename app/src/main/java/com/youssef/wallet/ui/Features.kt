package com.youssef.wallet.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.youssef.wallet.R
import com.youssef.wallet.ai.SplitMath
import com.youssef.wallet.data.Account
import com.youssef.wallet.data.Debt
import java.util.Calendar

// ======================= الديون والقروض =======================
@Composable
fun DebtsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val list by vm.debts.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var editing by remember { mutableStateOf<Debt?>(null) }
    var adding by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<Debt?>(null) }
    val now = System.currentTimeMillis()

    Column(Modifier.fillMaxSize()) {
        SubHeader(stringResource(R.string.debts), onBack)
        val owedToMe = list.filter { it.lent }.groupBy { it.currency }.mapValues { e -> e.value.sumOf { it.amount - it.paid } }
        val iOwe = list.filter { !it.lent }.groupBy { it.currency }.mapValues { e -> e.value.sumOf { it.amount - it.paid } }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.debts_owed_to_me) + ": " + (owedToMe.entries.filter { it.value > 0 }.joinToString(" • ") { money(it.value) + " " + it.key }.ifEmpty { "0" }), color = Green, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.debts_i_owe) + ": " + (iOwe.entries.filter { it.value > 0 }.joinToString(" • ") { money(it.value) + " " + it.key }.ifEmpty { "0" }), color = Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ " + stringResource(R.string.add_debt)) } }
            if (list.isEmpty()) item { Text(stringResource(R.string.debts_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list, key = { it.id }) { d ->
                val rest = d.amount - d.paid
                val done = rest <= 0.005
                val overdue = !done && d.dueDate != null && d.dueDate < now
                Card(Modifier.fillMaxWidth().clickable { editing = d }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text((if (d.lent) "⬆️ " else "⬇️ ") + d.person, fontWeight = FontWeight.Medium)
                            Text(
                                if (done) "✅" else money(rest) + " " + d.currency,
                                fontWeight = FontWeight.Bold, color = if (d.lent) Green else Red
                            )
                        }
                        Text(
                            stringResource(if (d.lent) R.string.debt_lent else R.string.debt_borrowed) + " • " + money(d.amount) + " " + d.currency,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (d.dueDate != null) {
                            Text(
                                (if (overdue) "⚠️ " else "📅 ") + stringResource(R.string.debt_due, fmtDate(d.dueDate).take(10)),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (overdue) Red else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (d.amount > 0) LinearProgressIndicator(progress = { (d.paid / d.amount).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        if (!done) OutlinedButton(onClick = { paying = d }) { Text(stringResource(if (d.lent) R.string.debt_receive else R.string.debt_pay)) }
                    }
                }
            }
        }
    }
    if (adding) DebtDialog(null, vm.base.value, { vm.saveDebt(it); adding = false }, null) { adding = false }
    editing?.let { d -> DebtDialog(d, d.currency, { vm.saveDebt(it); editing = null }, { vm.deleteDebt(d.id); editing = null }) { editing = null } }
    paying?.let { d -> PayDialog(d, accounts, { amt, acc -> vm.payDebt(d, amt, acc); paying = null }) { paying = null } }
}

@Composable
private fun DebtDialog(initial: Debt?, defCur: String, onSave: (Debt) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var person by remember { mutableStateOf(initial?.person ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.let { trimNum(it) } ?: "") }
    var paid by remember { mutableStateOf(initial?.paid?.let { trimNum(it) } ?: "0") }
    var cur by remember { mutableStateOf(initial?.currency ?: defCur) }
    var lent by remember { mutableStateOf(initial?.lent ?: true) }
    var due by remember { mutableStateOf(initial?.dueDate) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val a = parseAmount(amount)
    val p = parseAmount(paid) ?: 0.0
    AppDialog(
        title = stringResource(if (initial == null) R.string.add_debt else R.string.edit_debt),
        onDismiss = onDismiss,
        onConfirm = if (person.isNotBlank() && a != null && a > 0 && p >= 0) ({
            onSave(
                Debt(
                    id = initial?.id ?: 0, person = person.trim(), amount = a!!, paid = minOf(p, a), currency = cur,
                    lent = lent, date = initial?.date ?: System.currentTimeMillis(), dueDate = due, note = note.trim()
                )
            )
        }) else null,
        deleteAction = onDelete
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = lent, onClick = { lent = true }, label = { Text(stringResource(R.string.debt_lent)) })
            FilterChip(selected = !lent, onClick = { lent = false }, label = { Text(stringResource(R.string.debt_borrowed)) })
        }
        TextFieldS(person, stringResource(R.string.person)) { person = it }
        NumField(amount, stringResource(R.string.amount), { amount = it })
        NumField(paid, stringResource(R.string.debt_paid), { paid = it })
        Picker(stringResource(R.string.currency), cur, CURRENCIES, { it }) { cur = it }
        OutlinedButton(onClick = {
            val c = Calendar.getInstance().apply { timeInMillis = due ?: System.currentTimeMillis() }
            DatePickerDialog(ctx, { _, y, m, d ->
                val c2 = Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }
                due = c2.timeInMillis
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }, modifier = Modifier.fillMaxWidth()) {
            Text("📅 " + (due?.let { fmtDate(it).take(10) } ?: stringResource(R.string.debt_no_due)))
        }
        if (due != null) OutlinedButton(onClick = { due = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.debt_no_due)) }
        TextFieldS(note, stringResource(R.string.note)) { note = it }
    }
}

@Composable
private fun PayDialog(d: Debt, accounts: List<Account>, onPay: (Double, Long?) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val rest = d.amount - d.paid
    var amount by remember { mutableStateOf(trimNum(rest)) }
    var accId by remember { mutableStateOf<Long?>(null) }
    val same = accounts.filter { it.currency == d.currency }
    val a = parseAmount(amount)
    AppDialog(
        title = stringResource(if (d.lent) R.string.debt_receive else R.string.debt_pay),
        onDismiss = onDismiss,
        onConfirm = if (a != null && a > 0) ({ onPay(a!!, accId) }) else null
    ) {
        NumField(amount, stringResource(R.string.amount) + " (${d.currency})", { amount = it })
        Picker(
            stringResource(R.string.debt_record_in),
            same.firstOrNull { it.id == accId }?.let { label(ctx, it.name) } ?: stringResource(R.string.debt_dont_record),
            listOf<Account?>(null) + same, { x -> x?.let { label(ctx, it.name) } ?: ctx.getString(R.string.debt_dont_record) }
        ) { accId = it?.id }
    }
}

// ======================= تقسيم الفاتورة =======================
@Composable
fun SplitScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val accounts by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    var total by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("0") }
    var parts by remember { mutableStateOf(2) }
    var accId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }
    var catId by remember { mutableStateOf<Long?>(null) }
    var merchant by remember { mutableStateOf("") }
    val t = parseAmount(total)
    val tp = parseAmount(tip) ?: 0.0
    val acc = accounts.firstOrNull { it.id == accId } ?: accounts.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        SubHeader(stringResource(R.string.split_bill), onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField(total, stringResource(R.string.split_total) + (acc?.let { " (${it.currency})" } ?: ""), { total = it })
            NumField(tip, stringResource(R.string.split_tip), { tip = it })
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.split_people))
                OutlinedButton(onClick = { if (parts > 1) parts-- }) { Text("−") }
                Text("$parts", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { if (parts < 30) parts++ }) { Text("+") }
            }
            if (t != null && t > 0) {
                val (mine, each) = SplitMath.split(t, tp, parts)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.split_each) + ": " + money(each), fontWeight = FontWeight.Bold)
                        if (mine != each) Text(stringResource(R.string.split_mine) + ": " + money(mine))
                        Text(stringResource(R.string.split_note_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Picker(stringResource(R.string.account), acc?.let { label(ctx, it.name) } ?: "—", accounts, { label(ctx, it.name) }) { accId = it.id }
            val list = cats.filter { !it.isIncome }
            Picker(
                stringResource(R.string.category),
                list.firstOrNull { it.id == catId }?.let { it.icon + " " + label(ctx, it.name) } ?: stringResource(R.string.uncategorized),
                list, { it.icon + " " + label(ctx, it.name) }
            ) { catId = it.id }
            TextFieldS(merchant, stringResource(R.string.merchant)) { merchant = it }
            Button(
                onClick = {
                    vm.saveSplit(t!!, tp, parts, acc!!.id, catId, merchant.trim())
                    total = ""; merchant = ""
                },
                enabled = t != null && t > 0 && acc != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.split_save)) }
        }
    }
}

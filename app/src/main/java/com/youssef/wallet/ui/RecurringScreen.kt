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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.youssef.wallet.R
import com.youssef.wallet.data.Account
import com.youssef.wallet.data.Category
import com.youssef.wallet.data.Recurring
import java.util.Calendar

private val PERIODS = listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")

@Composable
private fun periodLabel(p: String): String = stringResource(
    when (p) {
        "DAILY" -> R.string.per_daily
        "WEEKLY" -> R.string.per_weekly
        "YEARLY" -> R.string.per_yearly
        else -> R.string.per_monthly
    }
)

@Composable
fun RecurringScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val list by vm.recurring.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    val hints by vm.subHints.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshSubHints() }
    var editing by remember { mutableStateOf<Recurring?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SubHeader(stringResource(R.string.recurring), onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(stringResource(R.string.recurring_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Button(onClick = { adding = true }, enabled = accounts.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text("＋ " + stringResource(R.string.add_recurring))
                }
            }
            if (hints.isNotEmpty()) {
                item { Text("🔎 " + stringResource(R.string.subs_found), fontWeight = FontWeight.Bold) }
                items(hints, key = { "h" + it.merchant }) { h ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(h.merchant, fontWeight = FontWeight.Medium)
                            Text(
                                money(h.amount) + " • " + periodLabel(h.period) + " • " + stringResource(R.string.subs_times, h.count),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.acceptSub(h) }) { Text(stringResource(R.string.subs_add)) }
                                OutlinedButton(onClick = { vm.dismissSub(h) }) { Text(stringResource(R.string.ignore)) }
                            }
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                item { Text(stringResource(R.string.recurring_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(list, key = { it.id }) { r ->
                val cat = cats.firstOrNull { it.id == r.categoryId }
                val acc = accounts.firstOrNull { it.id == r.accountId }
                Card(Modifier.fillMaxWidth().clickable { editing = r }) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (cat?.icon ?: "🔁") + " " + r.merchant.ifBlank { cat?.let { label(ctx, it.name) } ?: stringResource(R.string.uncategorized) },
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                periodLabel(r.period) + " • " + stringResource(R.string.next_run, fmtDate(r.nextDate)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                (if (r.type == "INCOME") "+" else "−") + money(r.amount) + " " + (acc?.currency ?: ""),
                                fontWeight = FontWeight.Bold, color = if (r.type == "INCOME") Green else Red
                            )
                        }
                        Switch(checked = r.active, onCheckedChange = { vm.toggleRecurring(r, it) })
                    }
                }
            }
        }
    }
    if (adding) RecurringDialog(null, accounts, cats, { vm.saveRecurring(it); adding = false }, null) { adding = false }
    editing?.let { r ->
        RecurringDialog(r, accounts, cats, { vm.saveRecurring(it); editing = null }, { vm.deleteRecurring(r.id); editing = null }) { editing = null }
    }
}

@Composable
private fun RecurringDialog(
    initial: Recurring?, accounts: List<Account>, categories: List<Category>,
    onSave: (Recurring) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var type by remember { mutableStateOf(initial?.type ?: "EXPENSE") }
    var amount by remember { mutableStateOf(initial?.amount?.let { trimNum(it) } ?: "") }
    var accId by remember { mutableStateOf(initial?.accountId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id ?: 0L) }
    var catId by remember { mutableStateOf(initial?.categoryId) }
    var merchant by remember { mutableStateOf(initial?.merchant ?: "") }
    var period by remember { mutableStateOf(initial?.period ?: "MONTHLY") }
    var date by remember { mutableStateOf(initial?.nextDate ?: System.currentTimeMillis()) }
    val acc = accounts.firstOrNull { it.id == accId }
    val amountVal = parseAmount(amount)
    val valid = amountVal != null && amountVal > 0 && acc != null

    AppDialog(
        title = stringResource(if (initial == null) R.string.add_recurring else R.string.edit_recurring),
        onDismiss = onDismiss,
        onConfirm = if (valid) ({
            val c = Calendar.getInstance().apply { timeInMillis = date }
            onSave(
                Recurring(
                    id = initial?.id ?: 0, type = type, amount = amountVal!!, accountId = accId,
                    categoryId = catId, merchant = merchant.trim(), note = initial?.note ?: "",
                    period = period, nextDate = date, anchorDay = c.get(Calendar.DAY_OF_MONTH),
                    active = initial?.active ?: true
                )
            )
        }) else null,
        deleteAction = onDelete
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("EXPENSE" to R.string.expense, "INCOME" to R.string.income).forEach { (k, r) ->
                FilterChip(selected = type == k, onClick = {
                    type = k
                    if (catId != null && categories.firstOrNull { it.id == catId }?.isIncome != (k == "INCOME")) catId = null
                }, label = { Text(stringResource(r)) })
            }
        }
        NumField(amount, stringResource(R.string.amount) + (acc?.let { " (${it.currency})" } ?: ""), { amount = it })
        Picker(stringResource(R.string.account), acc?.let { label(ctx, it.name) } ?: "—", accounts, { label(ctx, it.name) }) { accId = it.id }
        val list = categories.filter { it.isIncome == (type == "INCOME") }
        val sel = categories.firstOrNull { it.id == catId }
        Picker(
            stringResource(R.string.category),
            sel?.let { it.icon + " " + label(ctx, it.name) } ?: stringResource(R.string.uncategorized),
            list, { it.icon + " " + label(ctx, it.name) }
        ) { catId = it.id }
        TextFieldS(merchant, stringResource(R.string.merchant)) { merchant = it }
        Picker(stringResource(R.string.repeat), periodLabel(period), PERIODS, { it }, Modifier.fillMaxWidth()) { period = it }
        OutlinedButton(onClick = {
            val c = Calendar.getInstance().apply { timeInMillis = date }
            DatePickerDialog(ctx, { _, y, m, d ->
                date = Calendar.getInstance().apply { timeInMillis = date; set(y, m, d) }.timeInMillis
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("📅 " + stringResource(R.string.start_date) + ": " + fmtDate(date)) }
    }
}

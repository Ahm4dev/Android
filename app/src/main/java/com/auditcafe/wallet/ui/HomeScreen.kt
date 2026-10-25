package com.auditcafe.wallet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auditcafe.wallet.R
import com.auditcafe.wallet.data.Txn

@Composable
fun HomeScreen(vm: MainViewModel, onEdit: (Txn) -> Unit) {
    val ctx = LocalContext.current
    val base by vm.base.collectAsState()
    val total by vm.totalBalance.collectAsState()
    val totals by vm.homeTotals.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val balances by vm.balances.collectAsState()
    val cats by vm.categories.collectAsState()
    val catSpent by vm.homeCats.collectAsState()
    val insights by vm.insights.collectAsState()
    val recent by vm.recent.collectAsState()
    val accMap = remember(accounts) { accounts.associateBy { it.id } }
    val catMap = remember(cats) { cats.associateBy { it.id } }
    val budgeted = cats.filter { it.monthlyBudget > 0 && !it.isIncome }
    val rates by vm.rates.collectAsState()
    val debts by vm.debts.collectAsState()
    val dueDebts = remember(debts) {
        val limit = System.currentTimeMillis() + 7L * 86_400_000L
        debts.count { it.paid < it.amount && it.dueDate != null && it.dueDate <= limit }
    }
    val gold21 = goldGram24(vm.prefs.goldUsdOz, base, rates)?.let { it * 21 / 24 }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(stringResource(R.string.total_balance), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    Text(
                        "${money(total)} $base", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(stringResource(R.string.income_month), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                            Text("+${money(totals.first)}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text(stringResource(R.string.expense_month), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                            Text("−${money(totals.second)}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (gold21 != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text("🪙 " + stringResource(R.string.home_gold, money(gold21), base), modifier = Modifier.padding(14.dp))
                }
            }
        }

        if (dueDebts > 0) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Red.copy(alpha = 0.10f))
                ) {
                    Text("🤝 " + stringResource(R.string.home_debts_due, dueDebts), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (insights.isNotEmpty()) {
            item { SectionTitle("🤖 " + stringResource(R.string.smart_insights)) }
            items(insights) { i ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (i.kind == "budget" || i.kind == "anomaly") Red.copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(insightText(i, base), modifier = Modifier.padding(14.dp))
                }
            }
        }

        if (budgeted.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.budgets)) }
            items(budgeted) { c ->
                val spent = catSpent[c.id] ?: 0.0
                val ratio = (spent / c.monthlyBudget).toFloat()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(c.icon + " " + label(ctx, c.name), fontWeight = FontWeight.Medium)
                            Text("${money(spent)} / ${money(c.monthlyBudget)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(),
                            color = if (ratio >= 1f) Red else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.accounts)) }
        items(accounts) { a ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(accountIcon(a.type) + " " + label(ctx, a.name), fontWeight = FontWeight.Medium)
                    val bal = balances[a.id] ?: a.initialBalance
                    Text("${money(bal)} ${a.currency}", fontWeight = FontWeight.Bold, color = if (bal < 0) Red else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.recent)) }
            items(recent) { t -> TxnRow(t, accMap, catMap) { onEdit(t) } }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

fun accountIcon(type: String): String = when (type) {
    "CASH" -> "💵"
    "SAVING" -> "🐖"
    "GOLD" -> "🪙"
    else -> "🏦"
}

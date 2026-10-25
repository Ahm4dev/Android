package com.youssef.wallet.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.youssef.wallet.R
import com.youssef.wallet.data.Suggested

@Composable
fun InboxScreen(vm: MainViewModel, onEdit: (Suggested) -> Unit) {
    val ctx = LocalContext.current
    val pending by vm.pending.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    val scan by vm.scan.collectAsState()
    val requestScan = rememberSmsPermission { vm.scanInbox() }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.inbox_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.inbox_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = requestScan, enabled = !scan.running, modifier = Modifier.fillMaxWidth()) {
                        Text("📩 " + stringResource(R.string.scan_sms))
                    }
                    if (scan.running) {
                        LinearProgressIndicator(
                            progress = { if (scan.total > 0) scan.done.toFloat() / scan.total else 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(stringResource(R.string.scan_progress, scan.done, scan.total, scan.added), style = MaterialTheme.typography.bodySmall)
                    } else if (scan.finished) {
                        Text(stringResource(R.string.scan_done, scan.done, scan.added), style = MaterialTheme.typography.bodySmall)
                    }
                    if (pending.size > 1) {
                        OutlinedButton(onClick = { vm.ignoreAll() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.ignore_all))
                        }
                    }
                }
            }
        }
        if (pending.isEmpty() && !scan.running) {
            item {
                Text(
                    stringResource(R.string.inbox_empty), modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(pending, key = { it.id }) { s ->
            val catName = s.categoryId?.let { id -> cats.firstOrNull { it.id == id } }
            val accName = s.accountId?.let { id -> accounts.firstOrNull { it.id == id } }
            val icon = when (s.kind) { "CREDIT" -> "🟢"; "TRANSFER" -> "🔁"; "ATM" -> "🏧"; else -> "🔴" }
            val kindLabel = stringResource(
                when (s.kind) { "CREDIT" -> R.string.kind_credit; "TRANSFER" -> R.string.kind_transfer; "ATM" -> R.string.kind_atm; else -> R.string.kind_debit }
            )
            val balLabel = stringResource(R.string.balance_after)
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$icon ${s.bank} • $kindLabel", fontWeight = FontWeight.SemiBold)
                        Text(
                            (if (s.kind == "CREDIT") "+" else "−") + money(s.amount) + " " + s.currency,
                            fontWeight = FontWeight.Bold, color = if (s.kind == "CREDIT") Green else Red
                        )
                    }
                    if (s.merchant.isNotBlank()) Text(s.merchant)
                    val meta = buildString {
                        append(fmtDate(s.date))
                        if (!s.last4.isNullOrBlank()) append(" • ••••${s.last4}")
                        if (s.balanceAfter != null) append(" • $balLabel ${money(s.balanceAfter)}")
                        if (accName != null) append(" • ${label(ctx, accName.name)}")
                        if (catName != null) append(" • ${catName.icon} ${label(ctx, catName.name)}")
                    }
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = { if (accounts.isNotEmpty()) vm.accept(s, suggestionToTxn(s, accounts)) }
                        ) { Text(stringResource(R.string.accept)) }
                        TextButton(onClick = { onEdit(s) }) { Text(stringResource(R.string.edit)) }
                        TextButton(onClick = { vm.ignore(s) }) { Text(stringResource(R.string.ignore)) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

package com.youssef.wallet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.youssef.wallet.R
import com.youssef.wallet.data.Account
import com.youssef.wallet.data.Category
import com.youssef.wallet.data.SavedFilter
import com.youssef.wallet.data.Txn

@Composable
fun TxnScreen(vm: MainViewModel, onEdit: (Txn) -> Unit) {
    val ctx = LocalContext.current
    val lazyItems = vm.txPaging.collectAsLazyPagingItems()
    val f by vm.filter.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    val accMap = remember(accounts) { accounts.associateBy { it.id } }
    val catMap = remember(cats) { cats.associateBy { it.id } }
    var q by remember(f.q) { mutableStateOf(f.q) }
    var saveDlg by remember { mutableStateOf(false) }
    var fname by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it; vm.setFilter(vm.filter.value.copy(q = it)) },
                placeholder = { Text("🔍 " + stringResource(R.string.search_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "" to R.string.all, "EXPENSE" to R.string.expense, "INCOME" to R.string.income, "TRANSFER" to R.string.transfer
                ).forEach { (k, r) ->
                    FilterChip(selected = f.type == k, onClick = { vm.setFilter(vm.filter.value.copy(type = k)) }, label = { Text(stringResource(r)) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "ALL" to R.string.p_all, "TODAY" to R.string.p_today, "WEEK" to R.string.p_week, "MONTH" to R.string.p_month, "LAST" to R.string.p_last, "YEAR" to R.string.p_year
                ).forEach { (k, r) ->
                    FilterChip(selected = f.period == k, onClick = { vm.setFilter(vm.filter.value.copy(period = k)) }, label = { Text(stringResource(r)) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val selAcc = accMap[f.accountId]
                Picker(
                    stringResource(R.string.account), selAcc?.let { label(ctx, it.name) } ?: stringResource(R.string.all),
                    listOf<Account?>(null) + accounts, { a -> a?.let { label(ctx, it.name) } ?: ctx.getString(R.string.all) },
                    Modifier.weight(1f)
                ) { a -> vm.setFilter(vm.filter.value.copy(accountId = a?.id ?: 0)) }
                val selCat = catMap[f.categoryId]
                Picker(
                    stringResource(R.string.category), selCat?.let { label(ctx, it.name) } ?: stringResource(R.string.all),
                    listOf<Category?>(null) + cats, { c -> c?.let { label(ctx, it.name) } ?: ctx.getString(R.string.all) },
                    Modifier.weight(1f)
                ) { c -> vm.setFilter(vm.filter.value.copy(categoryId = c?.id ?: 0)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.AssistChip(
                    onClick = { fname = ""; saveDlg = true },
                    label = { Text("💾 " + stringResource(R.string.save_filter)) }
                )
                vm.prefs.savedFilters.forEach { sf ->
                    val on = f.q == sf.q && f.type == sf.type && f.accountId == sf.accountId && f.categoryId == sf.categoryId && f.period == sf.period
                    FilterChip(
                        selected = on,
                        onClick = { vm.setFilter(Filter(sf.q, sf.type, sf.accountId, sf.categoryId, sf.period)) },
                        label = { Text("⭐ " + sf.name) },
                        trailingIcon = { Text("✕", modifier = Modifier.clickable { vm.prefs.removeFilter(sf.name) }) }
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(count = lazyItems.itemCount, key = lazyItems.itemKey { it.id }) { i ->
                    val t = lazyItems[i]
                    if (t != null) TxnRow(t, accMap, catMap) { onEdit(t) }
                }
                item { androidx.compose.foundation.layout.Spacer(Modifier.height(80.dp)) }
            }
            if (lazyItems.itemCount == 0 && lazyItems.loadState.refresh is LoadState.NotLoading) {
                Text(
                    stringResource(R.string.no_txns), modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (saveDlg) {
        AppDialog(
            title = stringResource(R.string.save_filter),
            onDismiss = { saveDlg = false },
            onConfirm = {
                if (fname.isNotBlank()) {
                    vm.prefs.addFilter(SavedFilter(fname.trim().take(24), f.q, f.type, f.accountId, f.categoryId, f.period))
                    saveDlg = false
                }
            }
        ) {
            OutlinedTextField(value = fname, onValueChange = { fname = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

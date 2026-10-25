package com.auditcafe.wallet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.auditcafe.wallet.R
import com.auditcafe.wallet.data.Account
import com.auditcafe.wallet.data.Category
import com.auditcafe.wallet.data.Goal

@Composable
fun SubHeader(title: String, onBack: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text(if (rtl) "→" else "←", style = MaterialTheme.typography.titleLarge) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AccountsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val accounts by vm.accounts.collectAsState()
    val balances by vm.balances.collectAsState()
    var editing by remember { mutableStateOf<Account?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SubHeader(stringResource(R.string.accounts), onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ " + stringResource(R.string.add_account)) } }
            items(accounts, key = { it.id }) { a ->
                Card(Modifier.fillMaxWidth().clickable { editing = a }) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(accountIcon(a.type) + " " + label(ctx, a.name), fontWeight = FontWeight.Medium)
                            if (a.last4.isNotBlank()) Text("•••• ${a.last4}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${money(balances[a.id] ?: a.initialBalance)} ${a.currency}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (adding) AccountDialog(null, vm.base.value, { vm.saveAccount(it); adding = false }, null) { adding = false }
    editing?.let { a ->
        AccountDialog(a, a.currency, { vm.saveAccount(it); editing = null }, { vm.deleteAccount(a.id); editing = null }) { editing = null }
    }
}

@Composable
private fun AccountDialog(initial: Account?, defCur: String, onSave: (Account) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial?.let { label(ctx, it.name) } ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "BANK") }
    var cur by remember { mutableStateOf(initial?.currency ?: defCur) }
    var init by remember { mutableStateOf(initial?.initialBalance?.let { trimNum(it) } ?: "0") }
    var last4 by remember { mutableStateOf(initial?.last4 ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val typeNames = mapOf(
        "BANK" to stringResource(R.string.type_bank), "CASH" to stringResource(R.string.type_cash),
        "SAVING" to stringResource(R.string.type_saving), "GOLD" to stringResource(R.string.type_gold)
    )
    AppDialog(
        title = stringResource(if (initial == null) R.string.add_account else R.string.edit_account),
        onDismiss = onDismiss,
        onConfirm = if (name.isNotBlank() && parseAmount(init) != null) ({
            val keepKey = initial != null && name == label(ctx, initial.name)
            onSave(
                Account(
                    id = initial?.id ?: 0, name = if (keepKey) initial!!.name else name.trim(), type = type, currency = cur,
                    initialBalance = parseAmount(init) ?: 0.0, last4 = last4.filter { it.isDigit() }.take(4)
                )
            )
        }) else null,
        deleteAction = if (onDelete != null) ({ confirmDelete = true }) else null
    ) {
        TextFieldS(name, stringResource(R.string.name)) { name = it }
        Picker(stringResource(R.string.type), typeNames[type] ?: type, listOf("BANK", "CASH", "SAVING", "GOLD"), { typeNames[it] ?: it }) { type = it }
        Picker(stringResource(R.string.currency), cur, CURRENCIES, { it }) { cur = it }
        NumField(init, stringResource(R.string.initial_balance), { init = it })
        TextFieldS(last4, stringResource(R.string.last4)) { last4 = it.filter { c -> c.isDigit() }.take(4) }
    }
    if (confirmDelete) {
        AppDialog(
            title = stringResource(R.string.confirm_delete), onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; onDelete?.invoke() }, confirmText = stringResource(R.string.delete)
        ) { Text(stringResource(R.string.delete_account_warning)) }
    }
}

@Composable
fun CategoriesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cats by vm.categories.collectAsState()
    val base by vm.base.collectAsState()
    var income by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    val sugg by vm.budgetSuggestions.collectAsState()
    androidx.compose.runtime.LaunchedEffect(cats.size) { vm.computeBudgetSuggestions() }

    Column(Modifier.fillMaxSize()) {
        SubHeader(stringResource(R.string.categories), onBack)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !income, onClick = { income = false }, label = { Text(stringResource(R.string.expense)) })
            FilterChip(selected = income, onClick = { income = true }, label = { Text(stringResource(R.string.income)) })
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ " + stringResource(R.string.add_category)) } }
            if (!income && sugg.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("💡 " + stringResource(R.string.budget_suggest), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.budget_suggest_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            sugg.take(8).forEach { (c, v) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.icon + " " + label(ctx, c.name) + " — " + money(v) + " $base")
                                    TextButton(onClick = { vm.applyBudget(c, v) }) { Text(stringResource(R.string.apply)) }
                                }
                            }
                            Button(onClick = { vm.applyAllBudgets() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.apply_all)) }
                        }
                    }
                }
            }
            items(cats.filter { it.isIncome == income }, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { editing = c }) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c.icon + " " + label(ctx, c.name), fontWeight = FontWeight.Medium)
                        if (c.monthlyBudget > 0) Text("${money(c.monthlyBudget)} $base", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (adding) CategoryDialog(null, income, { vm.saveCategory(it); adding = false }, null) { adding = false }
    editing?.let { c ->
        CategoryDialog(c, c.isIncome, { vm.saveCategory(it); editing = null }, { vm.deleteCategory(c.id); editing = null }) { editing = null }
    }
}

@Composable
private fun CategoryDialog(initial: Category?, isIncome: Boolean, onSave: (Category) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial?.let { label(ctx, it.name) } ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "📦") }
    var budget by remember { mutableStateOf(initial?.monthlyBudget?.takeIf { it > 0 }?.let { trimNum(it) } ?: "") }
    AppDialog(
        title = stringResource(if (initial == null) R.string.add_category else R.string.edit_category),
        onDismiss = onDismiss,
        onConfirm = if (name.isNotBlank()) ({
            val keepKey = initial != null && name == label(ctx, initial.name)
            onSave(
                Category(
                    id = initial?.id ?: 0, name = if (keepKey) initial!!.name else name.trim(), isIncome = isIncome,
                    icon = icon, monthlyBudget = if (isIncome) 0.0 else (parseAmount(budget) ?: 0.0)
                )
            )
        }) else null,
        deleteAction = onDelete
    ) {
        TextFieldS(name, stringResource(R.string.name)) { name = it }
        EmojiPicker(icon) { icon = it }
        if (!isIncome) NumField(budget, stringResource(R.string.monthly_budget), { budget = it })
    }
}

@Composable
fun GoalsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val goals by vm.goals.collectAsState()
    var editing by remember { mutableStateOf<Goal?>(null) }
    var adding by remember { mutableStateOf(false) }
    var topUp by remember { mutableStateOf<Goal?>(null) }

    Column(Modifier.fillMaxSize()) {
        SubHeader(stringResource(R.string.goals), onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ " + stringResource(R.string.add_goal)) } }
            items(goals, key = { it.id }) { g ->
                val ratio = if (g.target > 0) (g.saved / g.target).toFloat() else 0f
                Card(Modifier.fillMaxWidth().clickable { editing = g }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(g.icon + " " + g.name, fontWeight = FontWeight.Medium)
                            Text("${money(g.saved)} / ${money(g.target)} ${g.currency}", style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = if (ratio >= 1f) Green else MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { topUp = g }) { Text("＋ " + stringResource(R.string.add_savings)) }
                        }
                    }
                }
            }
        }
    }
    if (adding) GoalDialog(null, vm.base.value, { vm.saveGoal(it); adding = false }, null) { adding = false }
    editing?.let { g -> GoalDialog(g, g.currency, { vm.saveGoal(it); editing = null }, { vm.deleteGoal(g.id); editing = null }) { editing = null } }
    topUp?.let { g ->
        var v by remember { mutableStateOf("") }
        AppDialog(
            title = stringResource(R.string.add_savings), onDismiss = { topUp = null },
            onConfirm = if ((parseAmount(v) ?: 0.0) > 0) ({ vm.saveGoal(g.copy(saved = g.saved + parseAmount(v)!!)); topUp = null }) else null
        ) { NumField(v, stringResource(R.string.amount) + " (${g.currency})", { v = it }) }
    }
}

@Composable
private fun GoalDialog(initial: Goal?, defCur: String, onSave: (Goal) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var target by remember { mutableStateOf(initial?.target?.let { trimNum(it) } ?: "") }
    var saved by remember { mutableStateOf(initial?.saved?.let { trimNum(it) } ?: "0") }
    var cur by remember { mutableStateOf(initial?.currency ?: defCur) }
    var icon by remember { mutableStateOf(initial?.icon ?: "🎯") }
    AppDialog(
        title = stringResource(if (initial == null) R.string.add_goal else R.string.edit_goal),
        onDismiss = onDismiss,
        onConfirm = if (name.isNotBlank() && (parseAmount(target) ?: 0.0) > 0) ({
            onSave(Goal(initial?.id ?: 0, name.trim(), parseAmount(target)!!, parseAmount(saved) ?: 0.0, cur, icon))
        }) else null,
        deleteAction = onDelete
    ) {
        TextFieldS(name, stringResource(R.string.name)) { name = it }
        EmojiPicker(icon) { icon = it }
        NumField(target, stringResource(R.string.target), { target = it })
        NumField(saved, stringResource(R.string.saved), { saved = it })
        Picker(stringResource(R.string.currency), cur, CURRENCIES, { it }) { cur = it }
    }
}

@Composable
fun RatesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val base by vm.base.collectAsState()
    val rates by vm.rates.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val goals by vm.goals.collectAsState()
    val used = (accounts.map { it.currency } + goals.map { it.currency } + CURRENCIES.take(3)).distinct().filter { it != base }

    Column(Modifier.fillMaxSize()) {
        SubHeader(stringResource(R.string.currency_rates), onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Picker(stringResource(R.string.base_currency), base, CURRENCIES, { it }) { vm.setBase(it) }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.rates_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.refreshRates(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text("🔄 " + stringResource(R.string.refresh_rates))
                }
                if (vm.prefs.ratesUpdated > 0) {
                    Text(
                        stringResource(R.string.rates_last, fmtDate(vm.prefs.ratesUpdated)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { GoldSection(vm) }
            items(used, key = { it }) { cur ->
                var v by remember(cur, base, vm.prefs.ratesUpdated) { mutableStateOf(rates[cur]?.let { trimNum(it) } ?: "1") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("1 $cur =", modifier = Modifier.width(88.dp), fontWeight = FontWeight.Medium)
                    NumField(v, base, {
                        v = it
                        parseAmount(it)?.takeIf { x -> x > 0 }?.let { x -> vm.setRate(cur, x) }
                    }, Modifier.weight(1f))
                }
            }
        }
    }
}

package com.youssef.wallet.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.youssef.wallet.R
import com.youssef.wallet.data.Suggested
import com.youssef.wallet.data.Txn

enum class Tab(val emoji: String, val title: Int) {
    HOME("🏠", R.string.tab_home),
    TXNS("🧾", R.string.tab_txns),
    INBOX("📥", R.string.tab_inbox),
    REPORTS("📊", R.string.tab_reports),
    MORE("⚙️", R.string.tab_more)
}

@Composable
fun AppRoot(vm: MainViewModel, startTab: String?, startQuick: Boolean = false) {
    var tab by rememberSaveable { mutableStateOf(runCatching { Tab.valueOf(startTab ?: "HOME") }.getOrDefault(Tab.HOME)) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var creating by rememberSaveable { mutableStateOf(startQuick) }
    var editingSuggested by remember { mutableStateOf<Suggested?>(null) }
    val accounts by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    val pendingCount = vm.pending.collectAsState().value.size

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            BadgedBox(badge = { if (t == Tab.INBOX && pendingCount > 0) Badge { Text("$pendingCount") } }) {
                                Text(t.emoji, fontSize = 20.sp)
                            }
                        },
                        label = { Text(stringResource(t.title), maxLines = 1, fontSize = 11.sp) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.HOME || tab == Tab.TXNS) FloatingActionButton(onClick = { creating = true }) { Text("+", fontSize = 28.sp) }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.HOME -> HomeScreen(vm, onOpenInbox = { tab = Tab.INBOX }, onEdit = { editing = it })
                Tab.TXNS -> TxnScreen(vm, onEdit = { editing = it })
                Tab.INBOX -> InboxScreen(vm, onEdit = { editingSuggested = it })
                Tab.REPORTS -> ReportsScreen(vm, onShowTxns = { tab = Tab.TXNS })
                Tab.MORE -> SettingsScreen(vm)
            }
        }
    }

    if (creating) {
        TxnEditorDialog(null, accounts, cats, onSave = { vm.saveTxn(it); creating = false }, onDelete = null, onDismiss = { creating = false })
    }
    editing?.let { t ->
        TxnEditorDialog(
            t, accounts, cats,
            onSave = { vm.saveTxn(it); editing = null },
            onDelete = { vm.deleteTxn(t.id); editing = null },
            onDismiss = { editing = null }
        )
    }
    editingSuggested?.let { s ->
        TxnEditorDialog(
            suggestionToTxn(s, accounts), accounts, cats,
            onSave = { vm.accept(s, it); editingSuggested = null },
            onDelete = null,
            onDismiss = { editingSuggested = null }
        )
    }
}

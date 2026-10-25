package com.auditcafe.wallet.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.auditcafe.wallet.R
import com.auditcafe.wallet.io.ReceiptStore
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.auditcafe.wallet.ai.Insight
import com.auditcafe.wallet.data.Account
import com.auditcafe.wallet.data.Category
import com.auditcafe.wallet.data.Suggested
import com.auditcafe.wallet.data.Txn
import java.util.Calendar
import kotlin.math.abs

@Composable
fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
fun <T> Picker(
    label: String,
    selectedText: String,
    items: List<T>,
    itemText: (T) -> String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $selectedText", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach {
                DropdownMenuItem(text = { Text(itemText(it)) }, onClick = { onSelect(it); open = false })
            }
        }
    }
}

@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)?,
    confirmText: String = stringResource(R.string.save),
    deleteAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        },
        confirmButton = { if (onConfirm != null) TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = {
            Row {
                if (deleteAction != null) TextButton(onClick = deleteAction) { Text(stringResource(R.string.delete), color = Red) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

@Composable
fun NumField(value: String, label: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier
    )
}

@Composable
fun TextFieldS(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun EmojiPicker(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EMOJIS.forEach { e ->
            FilterChip(selected = e == selected, onClick = { onSelect(e) }, label = { Text(e, fontSize = 18.sp) })
        }
    }
}

@Composable
fun rememberSmsPermission(onGranted: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val cb by rememberUpdatedState(onGranted)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.READ_SMS] == true) cb()
    }
    return {
        val have = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        if (have) cb() else {
            val p = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
            launcher.launch(p.toTypedArray())
        }
    }
}

@Composable
fun insightText(i: Insight, base: String): String {
    val ctx = LocalContext.current
    return when (i.kind) {
        "forecast" -> stringResource(R.string.ins_forecast, money(i.num1), base)
        "mom" ->
            if (i.num1 >= 0) stringResource(R.string.ins_mom_up, abs(i.num1).toInt())
            else stringResource(R.string.ins_mom_down, abs(i.num1).toInt())
        "top" -> stringResource(R.string.ins_top, label(ctx, i.label), i.num1.toInt())
        "budget" -> stringResource(R.string.ins_budget, label(ctx, i.label), i.num1.toInt())
        "anomaly" -> stringResource(R.string.ins_anomaly, i.label.ifBlank { "—" }, money(i.num1), money(i.num2))
        else -> ""
    }
}

fun suggestionToTxn(s: Suggested, accounts: List<Account>): Txn {
    val acc = s.accountId?.let { id -> accounts.firstOrNull { it.id == id } }
        ?: accounts.firstOrNull { it.currency == s.currency }
        ?: accounts.firstOrNull()
    return Txn(
        type = if (s.kind == "CREDIT") "INCOME" else "EXPENSE",
        amount = s.amount,
        accountId = acc?.id ?: 0,
        categoryId = s.categoryId,
        merchant = s.merchant.ifBlank { s.bank },
        date = s.date,
        source = s.source
    )
}

@Composable
fun TxnRow(t: Txn, accounts: Map<Long, Account>, cats: Map<Long, Category>, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val cat = t.categoryId?.let { cats[it] }
    val acc = accounts[t.accountId]
    val cur = acc?.currency ?: ""
    val transferLabel = stringResource(R.string.transfer)
    val noCat = stringResource(R.string.uncategorized)
    val title = t.merchant.ifBlank {
        if (t.type == "TRANSFER") transferLabel else cat?.let { label(ctx, it.name) } ?: noCat
    }
    val sub = buildString {
        append(fmtDate(t.date))
        if (acc != null) { append(" • "); append(label(ctx, acc.name)) }
        if (t.type == "TRANSFER") {
            val to = t.toAccountId?.let { accounts[it] }
            if (to != null) { append(" → "); append(label(ctx, to.name)) }
        }
    }
    val color = when (t.type) {
        "INCOME" -> Green
        "EXPENSE" -> Red
        else -> MaterialTheme.colorScheme.primary
    }
    val sign = when (t.type) {
        "INCOME" -> "+"
        "EXPENSE" -> "−"
        else -> ""
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (t.type == "TRANSFER") "🔁" else (cat?.icon ?: "📦"), fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title + if (t.attachment != null) "  📎" else "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("$sign${money(t.amount)} $cur", color = color, fontWeight = FontWeight.Bold)
    }
}

/** نافذة إضافة/تعديل حركة. تُستعمل أيضاً لتعديل الحركة المقترحة قبل قبولها. */
@Composable
fun TxnEditorDialog(
    initial: Txn?,
    accounts: List<Account>,
    categories: List<Category>,
    onSave: (Txn) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var type by remember { mutableStateOf(initial?.type ?: "EXPENSE") }
    var amount by remember { mutableStateOf(initial?.amount?.let { trimNum(it) } ?: "") }
    var accId by remember { mutableStateOf(initial?.accountId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id ?: 0L) }
    var toId by remember { mutableStateOf(initial?.toAccountId ?: accounts.firstOrNull { it.id != accId }?.id) }
    var toAmount by remember { mutableStateOf(initial?.toAmount?.let { trimNum(it) } ?: "") }
    var catId by remember { mutableStateOf(initial?.categoryId) }
    var merchant by remember { mutableStateOf(initial?.merchant ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: System.currentTimeMillis()) }
    var attachment by remember { mutableStateOf(initial?.attachment) }
    var added by remember { mutableStateOf(listOf<String>()) }
    var saved by remember { mutableStateOf(false) }
    var bigImg by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) scope.launch {
            val name = withContext(Dispatchers.IO) { ReceiptStore.save(ctx, uri) }
            if (name != null) { added = added + name; attachment = name }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            // صور أُضيفت ولم تُحفظ مع الحركة تُحذف حتى لا تتراكم
            if (!saved) added.forEach { ReceiptStore.delete(ctx, it) }
        }
    }

    val acc = accounts.firstOrNull { it.id == accId }
    val toAcc = accounts.firstOrNull { it.id == toId }
    val crossCurrency = type == "TRANSFER" && acc != null && toAcc != null && acc.currency != toAcc.currency
    val amountVal = parseAmount(amount)
    val toAmountVal = parseAmount(toAmount)
    val valid = amountVal != null && amountVal > 0 && acc != null &&
        (type != "TRANSFER" || (toAcc != null && toAcc.id != acc.id && (!crossCurrency || (toAmountVal != null && toAmountVal > 0))))

    AppDialog(
        title = stringResource(if (initial == null || initial.id == 0L) R.string.add_txn else R.string.edit_txn),
        onDismiss = onDismiss,
        onConfirm = if (valid) ({
            saved = true
            // تنظيف الصور: القديمة المستبدلة/المحذوفة + الصور المضافة غير المستعملة
            initial?.attachment?.takeIf { it != attachment }?.let { ReceiptStore.delete(ctx, it) }
            added.filter { it != attachment }.forEach { ReceiptStore.delete(ctx, it) }
            onSave(
                Txn(
                    id = initial?.id ?: 0,
                    type = type,
                    amount = amountVal!!,
                    accountId = accId,
                    toAccountId = if (type == "TRANSFER") toId else null,
                    toAmount = if (crossCurrency) toAmountVal else null,
                    categoryId = if (type == "TRANSFER") null else catId,
                    merchant = merchant.trim(),
                    note = note.trim(),
                    date = date,
                    hash = initial?.hash,
                    source = initial?.source ?: "MANUAL",
                    attachment = attachment
                )
            )
        }) else null,
        deleteAction = onDelete
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("EXPENSE" to R.string.expense, "INCOME" to R.string.income, "TRANSFER" to R.string.transfer).forEach { (k, r) ->
                FilterChip(selected = type == k, onClick = {
                    type = k
                    if (k != "TRANSFER" && catId != null && categories.firstOrNull { it.id == catId }?.isIncome != (k == "INCOME")) catId = null
                }, label = { Text(stringResource(r)) })
            }
        }
        NumField(amount, stringResource(R.string.amount) + (acc?.let { " (${it.currency})" } ?: ""), { amount = it })
        Picker(
            stringResource(if (type == "TRANSFER") R.string.from_account else R.string.account),
            acc?.let { label(ctx, it.name) } ?: "—", accounts, { label(ctx, it.name) }
        ) { accId = it.id }
        if (type == "TRANSFER") {
            Picker(stringResource(R.string.to_account), toAcc?.let { label(ctx, it.name) } ?: "—", accounts.filter { it.id != accId }, { label(ctx, it.name) }) { toId = it.id }
            if (crossCurrency) NumField(toAmount, stringResource(R.string.to_amount) + " (${toAcc?.currency})", { toAmount = it })
        } else {
            val list = categories.filter { it.isIncome == (type == "INCOME") }
            val sel = categories.firstOrNull { it.id == catId }
            Picker(
                stringResource(R.string.category),
                sel?.let { it.icon + " " + label(ctx, it.name) } ?: stringResource(R.string.uncategorized),
                list, { it.icon + " " + label(ctx, it.name) }
            ) { catId = it.id }
            TextFieldS(merchant, stringResource(R.string.merchant)) { merchant = it }
        }
        TextFieldS(note, stringResource(R.string.note)) { note = it }
        if (attachment == null) {
            OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("📎 " + stringResource(R.string.attach_receipt))
            }
        } else {
            val bmp = remember(attachment, bigImg) { ReceiptStore.load(ctx, attachment!!, if (bigImg) 1200 else 300) }
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(if (bigImg) 360.dp else 120.dp).clickable { bigImg = !bigImg }
                )
            } else Text(stringResource(R.string.receipt_missing), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch("image/*") }) { Text(stringResource(R.string.receipt_replace)) }
                OutlinedButton(onClick = { attachment = null }) { Text(stringResource(R.string.receipt_remove)) }
            }
        }
        OutlinedButton(onClick = {
            val c = Calendar.getInstance().apply { timeInMillis = date }
            DatePickerDialog(ctx, { _, y, m, d ->
                val c2 = Calendar.getInstance().apply { timeInMillis = date; set(y, m, d) }
                TimePickerDialog(ctx, { _, h, mi ->
                    c2.set(Calendar.HOUR_OF_DAY, h); c2.set(Calendar.MINUTE, mi)
                    date = c2.timeInMillis
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("📅 " + fmtDate(date)) }
    }
}

package com.auditcafe.wallet.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.auditcafe.wallet.R

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    var sub by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = sub.isNotEmpty()) { sub = "" }
    when (sub) {
        "accounts" -> AccountsScreen(vm) { sub = "" }
        "categories" -> CategoriesScreen(vm) { sub = "" }
        "goals" -> GoalsScreen(vm) { sub = "" }
        "recurring" -> RecurringScreen(vm) { sub = "" }
        "rates" -> RatesScreen(vm) { sub = "" }
        "banks" -> BanksScreen(vm) { sub = "" }
        "debts" -> DebtsScreen(vm) { sub = "" }
        "split" -> SplitScreen(vm) { sub = "" }
        else -> SettingsMain(vm) { sub = it }
    }
}

@Composable
private fun SettingsMain(vm: MainViewModel, navigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val prefs = vm.prefs

    var smsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        smsGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }
    var pinStep by remember { mutableStateOf("") }
    var pinA by remember { mutableStateOf("") }
    var pinB by remember { mutableStateOf("") }
    var pinErr by remember { mutableStateOf(false) }
    var abDialog by remember { mutableStateOf(false) }
    var abPass by remember { mutableStateOf("") }
    val treeL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            prefs.setAutoBackupUri(uri.toString())
            abDialog = true
        }
    }
    var pass by remember { mutableStateOf("") }
    var passDialog by remember { mutableStateOf("") }
    val exportL = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> if (uri != null) vm.exportExcel(uri) }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importExcel(uri) }
    val backupL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.backup(uri, pass.ifEmpty { null })
    }
    val restoreL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restore(uri, pass.ifEmpty { null })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionTitle(stringResource(R.string.manage))
        SettingRow("🏦 " + stringResource(R.string.accounts), onClick = { navigate("accounts") })
        SettingRow("🗂 " + stringResource(R.string.categories_budgets), onClick = { navigate("categories") })
        SettingRow("🎯 " + stringResource(R.string.goals), onClick = { navigate("goals") })
        SettingRow("🔁 " + stringResource(R.string.recurring), onClick = { navigate("recurring") })
        SettingRow("💱 " + stringResource(R.string.currency_rates), onClick = { navigate("rates") })
        SettingRow("🤝 " + stringResource(R.string.debts), stringResource(R.string.debts_short), onClick = { navigate("debts") })
        SettingRow("🍽 " + stringResource(R.string.split_bill), stringResource(R.string.split_short), onClick = { navigate("split") })
        SettingRow("🔔 " + stringResource(R.string.reminders), stringResource(R.string.reminders_desc)) {
            Switch(checked = prefs.reminders, onCheckedChange = { prefs.setReminders(it) })
        }

        SectionTitle(stringResource(R.string.sms_smart))
        SettingRow(
            "📩 " + stringResource(R.string.sms_permission),
            stringResource(if (smsGranted) R.string.granted else R.string.not_granted),
            onClick = {
                val p = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                if (android.os.Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
                permLauncher.launch(p.toTypedArray())
            }
        )
        SettingRow("🏛 " + stringResource(R.string.banks), stringResource(R.string.banks_short), onClick = { navigate("banks") })
        SettingRow("⚡ " + stringResource(R.string.auto_register), stringResource(R.string.auto_register_desc)) {
            Switch(checked = prefs.autoRegister, onCheckedChange = { prefs.setAutoRegister(it) })
        }
        SettingRow("🔔 " + stringResource(R.string.notif_capture), stringResource(R.string.notif_capture_desc)) {
            Switch(checked = prefs.notifCapture, onCheckedChange = {
                prefs.setNotifCapture(it)
                if (it) ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }

        SectionTitle(stringResource(R.string.security))
        SettingRow("🔐 " + stringResource(R.string.biometric_lock), stringResource(R.string.biometric_desc)) {
            Switch(checked = prefs.biometric, onCheckedChange = {
                if (it) {
                    val ok = BiometricManager.from(ctx).canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    ) == BiometricManager.BIOMETRIC_SUCCESS
                    if (ok) prefs.setBiometric(true) else Toast.makeText(ctx, R.string.biometric_unavailable, Toast.LENGTH_LONG).show()
                } else prefs.setBiometric(false)
            })
        }
        if (prefs.biometric) {
            val opts = listOf(0, 15, 30, 60, 300)
            val optText: (Int) -> String = {
                when (it) { 0 -> ctx.getString(R.string.immediately); 60 -> "1 min"; 300 -> "5 min"; else -> "$it s" }
            }
            Picker(stringResource(R.string.lock_after), optText(prefs.lockTimeout), opts, optText) { prefs.setLockTimeout(it) }
        }
        SettingRow(
            "🔢 " + stringResource(R.string.pin_lock),
            stringResource(if (prefs.hasPin) R.string.pin_on else R.string.pin_desc),
            onClick = { pinStep = if (prefs.hasPin) "remove" else "set"; pinA = ""; pinB = ""; pinErr = false }
        )
        SettingRow("🛡 " + stringResource(R.string.secure_screen), stringResource(R.string.secure_screen_desc)) {
            Switch(checked = prefs.secureScreen, onCheckedChange = { prefs.setSecureScreen(it) })
        }

        SectionTitle(stringResource(R.string.data))
        SettingRow("📤 " + stringResource(R.string.export_excel), onClick = { exportL.launch("wallet_export.xlsx") })
        SettingRow("📥 " + stringResource(R.string.import_excel), stringResource(R.string.import_excel_desc), onClick = { importL.launch(arrayOf("*/*")) })
        SettingRow("☁️ " + stringResource(R.string.backup), stringResource(R.string.backup_desc), onClick = { pass = ""; passDialog = "backup" })
        SettingRow("♻️ " + stringResource(R.string.restore), onClick = { pass = ""; passDialog = "restore" })
        SettingRow(
            "🗓 " + stringResource(R.string.auto_backup),
            stringResource(
                if (prefs.autoBackupUri.isEmpty()) R.string.auto_backup_desc
                else if (prefs.lastAutoBackup == 0L) R.string.auto_backup_never
                else R.string.auto_backup_last
            ).let { if (prefs.lastAutoBackup > 0L) it + " " + fmtDate(prefs.lastAutoBackup) else it },
            onClick = { treeL.launch(null) }
        ) {
            if (prefs.autoBackupUri.isNotEmpty()) Switch(checked = prefs.autoBackup, onCheckedChange = { prefs.setAutoBackup(it) })
        }
        if (prefs.autoBackupUri.isNotEmpty()) {
            SettingRow("▶️ " + stringResource(R.string.auto_backup_now), onClick = { vm.runAutoBackupNow() })
        }

        SectionTitle(stringResource(R.string.language))
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("" to stringResource(R.string.lang_system), "ar" to "العربية", "en" to "English").forEach { (tag, name) ->
                FilterChip(
                    selected = current.startsWith(tag) && (tag.isNotEmpty() || current.isEmpty()),
                    onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                    label = { Text(name) }
                )
            }
        }
        SectionTitle(stringResource(R.string.about))
        Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
    }

    if (abDialog) {
        AppDialog(
            title = stringResource(R.string.auto_backup),
            onDismiss = { abDialog = false },
            onConfirm = {
                abDialog = false
                prefs.setAutoBackupPassEnc(if (abPass.isEmpty()) "" else com.auditcafe.wallet.data.KeystoreBox.encrypt(abPass))
                prefs.setAutoBackup(true)
                abPass = ""
            },
            confirmText = stringResource(R.string.save)
        ) {
            Text(stringResource(R.string.auto_backup_pass_desc), style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.OutlinedTextField(
                value = abPass, onValueChange = { abPass = it }, label = { Text(stringResource(R.string.password_optional)) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (pinStep.isNotEmpty()) {
        val numeric = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
        AppDialog(
            title = stringResource(R.string.pin_lock),
            onDismiss = { pinStep = "" },
            onConfirm = {
                if (pinStep == "set") {
                    if (pinA.length in 4..8 && pinA == pinB) {
                        prefs.setPin(com.auditcafe.wallet.data.PinStore.create(pinA)); pinStep = ""
                    } else pinErr = true
                } else {
                    if (com.auditcafe.wallet.data.PinStore.verify(pinA, prefs.pinHash)) { prefs.setPin(""); pinStep = "" } else pinErr = true
                }
            },
            confirmText = stringResource(R.string.save)
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = pinA, onValueChange = { v -> pinA = v.filter { it.isDigit() }.take(8); pinErr = false },
                label = { Text(stringResource(if (pinStep == "set") R.string.pin_new else R.string.pin_enter)) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = numeric,
                isError = pinErr, modifier = Modifier.fillMaxWidth()
            )
            if (pinStep == "set") {
                androidx.compose.material3.OutlinedTextField(
                    value = pinB, onValueChange = { v -> pinB = v.filter { it.isDigit() }.take(8); pinErr = false },
                    label = { Text(stringResource(R.string.pin_confirm)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = numeric,
                    isError = pinErr, modifier = Modifier.fillMaxWidth()
                )
            }
            if (pinErr) Text(stringResource(R.string.pin_wrong), color = MaterialTheme.colorScheme.error)
        }
    }

    if (passDialog.isNotEmpty()) {
        val isBackup = passDialog == "backup"
        AppDialog(
            title = stringResource(if (isBackup) R.string.backup else R.string.restore),
            onDismiss = { passDialog = "" },
            onConfirm = {
                passDialog = ""
                if (isBackup) backupL.launch("wallet_backup.mwb") else restoreL.launch(arrayOf("*/*"))
            },
            confirmText = stringResource(R.string.next)
        ) {
            Text(stringResource(if (isBackup) R.string.backup_pass_desc else R.string.restore_pass_desc), style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.OutlinedTextField(
                value = pass, onValueChange = { pass = it }, label = { Text(stringResource(R.string.password_optional)) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

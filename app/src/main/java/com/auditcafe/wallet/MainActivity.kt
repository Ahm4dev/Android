package com.auditcafe.wallet

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.auditcafe.wallet.data.PinStore
import com.auditcafe.wallet.ui.AppRoot
import com.auditcafe.wallet.ui.MainViewModel
import com.auditcafe.wallet.ui.WalletTheme

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()
    private var locked by mutableStateOf(false)
    private var lastStop = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locked = savedInstanceState?.getBoolean("locked") ?: WalletApp.prefs.lockEnabled
        val startTab = intent?.getStringExtra("tab")
        val startQuick = intent?.getBooleanExtra("quick", false) ?: false
        setContent {
            WalletTheme {
                val secure = WalletApp.prefs.secureScreen
                LaunchedEffect(secure, locked) {
                    if (secure || locked) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                LaunchedEffect(Unit) {
                    vm.messages.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
                }
                LaunchedEffect(Unit) { vm.restartRequest.collect { restartApp() } }
                LaunchedEffect(locked) { if (locked && WalletApp.prefs.biometric) authenticate() }
                if (locked) LockScreen(
                    hasPin = WalletApp.prefs.hasPin,
                    hasBio = WalletApp.prefs.biometric,
                    onBio = { authenticate() },
                    onPin = { pin ->
                        val ok = PinStore.verify(pin, WalletApp.prefs.pinHash)
                        if (ok) locked = false
                        ok
                    }
                ) else AppRoot(vm, startTab, startQuick)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("locked", locked)
    }

    override fun onStart() {
        super.onStart()
        vm.refreshRange()
        val p = WalletApp.prefs
        if (p.lockEnabled && !locked && lastStop != 0L &&
            System.currentTimeMillis() - lastStop > p.lockTimeout * 1000L
        ) locked = true
    }

    override fun onStop() {
        super.onStop()
        lastStop = System.currentTimeMillis()
    }

    private fun authenticate() {
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            // لا توجد وسيلة قفل على الجهاز: نعتمد على PIN إن وُجد وإلا لا يمكن فرض القفل
            if (!WalletApp.prefs.hasPin) locked = false
            return
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_title))
            .setAllowedAuthenticators(auth)
            .build()
        BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                }
            }
        ).authenticate(info)
    }

    private fun restartApp() {
        Toast.makeText(this, R.string.restore_done, Toast.LENGTH_LONG).show()
        Handler(Looper.getMainLooper()).postDelayed({
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            if (launch?.component != null) startActivity(Intent.makeRestartActivityTask(launch.component))
            Runtime.getRuntime().exit(0)
        }, 900)
    }
}

@Composable
private fun LockScreen(hasPin: Boolean, hasBio: Boolean, onBio: () -> Unit, onPin: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var fails by remember { mutableStateOf(0) }
    var blockedUntil by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔒", fontSize = 64.sp)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 12.dp))
            if (hasPin) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8); error = false },
                    label = { Text(stringResource(R.string.pin_enter)) },
                    singleLine = true,
                    isError = error,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (error) Text(stringResource(R.string.pin_wrong), color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (now < blockedUntil) {
                            error = true
                        } else if (!onPin(pin)) {
                            fails++
                            error = true
                            pin = ""
                            if (fails >= 5) { blockedUntil = now + 30_000L; fails = 0 }
                        }
                    },
                    enabled = pin.length >= 4,
                    modifier = Modifier.padding(top = 12.dp)
                ) { Text(stringResource(R.string.unlock)) }
            }
            if (hasBio) {
                Button(onClick = onBio, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.unlock_bio)) }
            }
        }
    }
}

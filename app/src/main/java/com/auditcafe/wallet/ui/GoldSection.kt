package com.auditcafe.wallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auditcafe.wallet.R

const val GRAMS_PER_OUNCE = 31.1034768

/** سعر جرام الذهب عيار 24 بالعملة الأساسية، أو null إن لم تتوفر الأسعار. */
fun goldGram24(goldUsdOz: Double, base: String, rates: Map<String, Double>): Double? {
    val usd = if (base == "USD") 1.0 else rates["USD"]
    if (goldUsdOz <= 0 || usd == null || usd <= 0) return null
    return goldUsdOz / GRAMS_PER_OUNCE * usd
}

@Composable
fun GoldSection(vm: MainViewModel) {
    val base by vm.base.collectAsState()
    val rates by vm.rates.collectAsState()
    val oz = vm.prefs.goldUsdOz
    val g24 = goldGram24(oz, base, rates)
    val perGram = stringResource(R.string.per_gram)
    var grams by remember { mutableStateOf("") }
    var karat by remember { mutableStateOf(21) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🪙 " + stringResource(R.string.gold_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (g24 == null) {
                Text(stringResource(R.string.gold_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                listOf(24, 21, 18).forEach { k ->
                    Text("${k}K:  ${money(g24 * k / 24)} $base $perGram")
                }
                Text(stringResource(R.string.gold_pound) + ":  " + money(g24 * 21 / 24 * 8) + " " + base)
                Text(stringResource(R.string.gold_ounce) + ":  " + money(oz) + " USD", style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.gold_calc), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                NumField(grams, stringResource(R.string.grams), { grams = it })
                Picker(stringResource(R.string.karat), "${karat}K", listOf(24, 21, 18, 14), { "${it}K" }) { karat = it }
                val gv = parseAmount(grams)
                if (gv != null && gv > 0) {
                    Text(
                        stringResource(R.string.gold_value, money(gv * g24 * karat / 24), base),
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

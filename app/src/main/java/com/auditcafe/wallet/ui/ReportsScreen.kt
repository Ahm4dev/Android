package com.auditcafe.wallet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.auditcafe.wallet.R
import kotlin.math.min

@Composable
fun ReportsScreen(vm: MainViewModel, onShowTxns: () -> Unit = {}) {
    val ctx = LocalContext.current
    val rep by vm.report.collectAsState()
    val period by vm.reportPeriod.collectAsState()
    val type by vm.reportType.collectAsState()
    val base by vm.base.collectAsState()
    val cats by vm.categories.collectAsState()
    val catMap = cats.associateBy { it.id }
    val uncategorized = stringResource(R.string.uncategorized)
    val nl by vm.nlAnswer.collectAsState()
    val nlFailed by vm.nlFailed.collectAsState()
    var question by remember { mutableStateOf("") }
    val xlsL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) vm.exportReportExcel(uri)
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🧠 " + stringResource(R.string.nl_title), fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = question, onValueChange = { question = it; if (it.isBlank()) vm.clearNl() },
                        placeholder = { Text(stringResource(R.string.nl_hint)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { vm.askNl(question) }, enabled = question.isNotBlank()) { Text(stringResource(R.string.nl_ask)) }
                    if (nlFailed) Text(stringResource(R.string.nl_unknown), color = MaterialTheme.colorScheme.error)
                    nl?.let { a ->
                        Text(
                            stringResource(if (a.result.type == "INCOME") R.string.nl_income_is else R.string.nl_expense_is) + " " +
                                money(a.total) + " $base  (" + stringResource(R.string.nl_count, a.count) + ")",
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(onClick = { vm.setFilter(a.filter); onShowTxns() }) { Text(stringResource(R.string.nl_show)) }
                    }
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "MONTH" to R.string.p_month, "LAST" to R.string.p_last, "3M" to R.string.p_3m,
                    "YEAR" to R.string.p_year, "ALL" to R.string.p_all
                ).forEach { (k, r) ->
                    FilterChip(selected = period == k, onClick = { vm.reportPeriod.value = k }, label = { Text(stringResource(r)) })
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.income)); Text("+${money(rep.income)} $base", color = Green, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.expense)); Text("−${money(rep.expense)} $base", color = Red, fontWeight = FontWeight.Bold)
                    }
                    val net = rep.income - rep.expense
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.net), fontWeight = FontWeight.Bold)
                        Text((if (net >= 0) "+" else "−") + money(kotlin.math.abs(net)) + " $base", fontWeight = FontWeight.Bold, color = if (net >= 0) Green else Red)
                    }
                    if (rep.income > 0) {
                        Text(
                            stringResource(R.string.savings_rate, (net / rep.income * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = type == "EXPENSE", onClick = { vm.reportType.value = "EXPENSE" }, label = { Text(stringResource(R.string.expense)) })
                FilterChip(selected = type == "INCOME", onClick = { vm.reportType.value = "INCOME" }, label = { Text(stringResource(R.string.income)) })
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.by_category), fontWeight = FontWeight.Bold)
                    val total = rep.slices.sumOf { it.total }
                    if (total <= 0) {
                        Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        PieChart(
                            rep.slices.map { it.total.toFloat() },
                            MaterialTheme.colorScheme.surface,
                            Modifier.fillMaxWidth().height(180.dp)
                        )
                        rep.slices.forEachIndexed { i, s ->
                            val cat = s.categoryId?.let { catMap[it] }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(ChartColors[i % ChartColors.size]))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    (cat?.icon ?: "📦") + " " + (cat?.let { label(ctx, it.name) } ?: uncategorized),
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${money(s.total)}  (${(s.total / total * 100).toInt()}%)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.monthly_trend), fontWeight = FontWeight.Bold)
                    MonthlyBars(rep.months)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("■ " + stringResource(R.string.income), color = Green, style = MaterialTheme.typography.bodySmall)
                        Text("■ " + stringResource(R.string.expense), color = Red, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { xlsL.launch("wallet_report.xlsx") }, modifier = Modifier.fillMaxWidth()) {
                Text("📊 " + stringResource(R.string.export_report))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun PieChart(values: List<Float>, hole: Color, modifier: Modifier) {
    Canvas(modifier) {
        val sum = values.sum()
        if (sum <= 0f) return@Canvas
        val d = min(size.width, size.height)
        val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        var start = -90f
        values.forEachIndexed { i, v ->
            val sweep = v / sum * 360f
            drawArc(ChartColors[i % ChartColors.size], start, sweep, true, topLeft, Size(d, d))
            start += sweep
        }
        drawCircle(hole, radius = d * 0.28f, center = Offset(size.width / 2f, size.height / 2f))
    }
}

@Composable
fun MonthlyBars(months: List<MonthBar>) {
    val max = months.maxOfOrNull { maxOf(it.income, it.expense) }?.takeIf { it > 0 } ?: 1.0
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val n = months.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.32f
            months.forEachIndexed { i, m ->
                val x = i * slot + slot * 0.12f
                val hi = (m.income / max * size.height).toFloat()
                val he = (m.expense / max * size.height).toFloat()
                drawRect(Green, Offset(x, size.height - hi), Size(bw, hi))
                drawRect(Red, Offset(x + bw + 2f, size.height - he), Size(bw, he))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            months.forEach {
                Text(it.ym.takeLast(2), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

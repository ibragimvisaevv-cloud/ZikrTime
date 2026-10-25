package com.zikrtime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.Gold
import com.zikrtime.app.ui.theme.Tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Нисаб: 85 г золота или 595 г серебра; закят — 2,5 % (1/40). */
private const val NISAB_GOLD_G = 85.0
private const val NISAB_SILVER_G = 595.0
private const val ZAKAT_RATE = 0.025
private const val KEY = "zakat_inputs"

/** Учётные цены ЦБ РФ на золото и серебро (₽ за грамм): последняя дата за 10 дней. */
private fun cbrMetalPrices(): Pair<Double, Double>? {
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val to = LocalDate.now()
    val url = "https://www.cbr.ru/scripts/xml_metall.asp?date_req1=${to.minusDays(10).format(fmt)}&date_req2=${to.format(fmt)}"
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 15_000
    try {
        if (conn.responseCode != 200) return null
        val xml = conn.inputStream.bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
        fun last(code: Int): Double? = Regex("""<Record Date="[^"]+" Code="$code">\s*<Buy>([\d,]+)</Buy>""")
            .findAll(xml).lastOrNull()?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val gold = last(1) ?: return null
        val silver = last(2) ?: return null
        return gold to silver
    } finally {
        conn.disconnect()
    }
}

@Composable
fun ZakatScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val fields = listOf(
        "cash" to R.string.zakat_cash,
        "bank" to R.string.zakat_bank,
        "goods" to R.string.zakat_goods,
        "owed" to R.string.zakat_owed,
        "gold_g" to R.string.zakat_gold_g,
        "silver_g" to R.string.zakat_silver_g,
        "debts" to R.string.zakat_debts,
        "gold_price" to R.string.zakat_gold_price,
        "silver_price" to R.string.zakat_silver_price,
    )
    val values = remember {
        mutableStateMapOf<String, String>().apply {
            runCatching { JSONObject(Store.getRaw(KEY) ?: "{}") }.getOrNull()?.let { o -> o.keys().forEach { put(it, o.getString(it)) } }
        }
    }
    var bySilver by remember { mutableStateOf(values["by"] == "silver") }
    var loading by remember { mutableStateOf(false) }
    var pricesDate by remember { mutableStateOf<String?>(null) }

    fun save() {
        Store.putRaw(KEY, JSONObject(values.toMap() + ("by" to if (bySilver) "silver" else "gold")).toString())
    }
    fun num(k: String) = values[k]?.replace(" ", "")?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
    fun loadPrices() {
        loading = true
        scope.launch {
            val p = withContext(Dispatchers.IO) { runCatching { cbrMetalPrices() }.getOrNull() }
            loading = false
            if (p != null) {
                values["gold_price"] = "%.2f".format(Locale.US, p.first)
                values["silver_price"] = "%.2f".format(Locale.US, p.second)
                pricesDate = LocalDate.now().toString()
                save()
            }
        }
    }
    LaunchedEffect(Unit) { if (num("gold_price") == 0.0) loadPrices() }

    val goldPrice = num("gold_price")
    val silverPrice = num("silver_price")
    val total = num("cash") + num("bank") + num("goods") + num("owed") +
        num("gold_g") * goldPrice + num("silver_g") * silverPrice - num("debts")
    val nisab = if (bySilver) NISAB_SILVER_G * silverPrice else NISAB_GOLD_G * goldPrice
    val money = remember { NumberFormat.getNumberInstance(Locale("ru")).apply { maximumFractionDigits = 0 } }

    ScreenScaffold(title = stringResource(R.string.zakat_title), onBack = { nav.back() }) {
        // Итог
        GlassHero {
            Column(Modifier.padding(22.dp)) {
                Text(stringResource(R.string.zakat_due), style = MaterialTheme.typography.labelLarge, color = Cream.copy(alpha = 0.85f))
                val due = if (nisab > 0 && total >= nisab) total * ZAKAT_RATE else 0.0
                Text(money.format(due) + " ₽", fontSize = 40.sp, fontWeight = FontWeight.Light, color = Gold, style = Tabular)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        nisab <= 0 -> stringResource(R.string.zakat_need_prices)
                        total >= nisab -> stringResource(R.string.zakat_above, money.format(total), money.format(nisab))
                        else -> stringResource(R.string.zakat_below, money.format(total.coerceAtLeast(0.0)), money.format(nisab))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream,
                )
            }
        }

        ZCard {
            Text(stringResource(R.string.zakat_assets), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                fields.take(7).forEach { (k, label) -> MoneyField(values[k].orEmpty(), stringResource(label)) { values[k] = it; save() } }
            }
        }

        ZCard {
            Text(stringResource(R.string.zakat_nisab), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !bySilver, onClick = { bySilver = false; save() }, label = { Text(stringResource(R.string.zakat_by_gold)) })
                FilterChip(selected = bySilver, onClick = { bySilver = true; save() }, label = { Text(stringResource(R.string.zakat_by_silver)) })
            }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                fields.drop(7).forEach { (k, label) -> MoneyField(values[k].orEmpty(), stringResource(label)) { values[k] = it; save() } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.zakat_prices_cbr),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { loadPrices() }, enabled = !loading) {
                    Text(stringResource(if (loading) R.string.zakat_loading else R.string.zakat_update_prices))
                }
            }
        }

        Text(stringResource(R.string.zakat_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoneyField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }.take(15)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

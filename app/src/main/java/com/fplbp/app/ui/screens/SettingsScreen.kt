package com.fplbp.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fplbp.app.BuildConfig
import com.fplbp.app.data.LeagueData
import com.fplbp.app.ui.MainViewModel
import com.fplbp.app.ui.components.SectionCard
import com.fplbp.app.ui.share.ShareImage
import com.fplbp.app.ui.theme.App
import com.fplbp.app.ui.time
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val prefs = vm.prefs
    val context = LocalContext.current
    val data by vm.data.collectAsState()
    val update by vm.update.collectAsState()
    var league by remember { mutableStateOf(prefs.leagueId.toString()) }
    var debt by remember { mutableStateOf(prefs.debtAmount.toString()) }
    var nSettle by remember { mutableStateOf(prefs.notifSettle) }
    var nDeadline by remember { mutableStateOf(prefs.notifDeadline) }
    var nMatchday by remember { mutableStateOf(prefs.notifMatchday) }
    var confirmReset by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val d = data ?: return@rememberLauncherForActivityResult
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(exportJson(d).toByteArray()) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            // Read-only: every phone must use the same league and amount, or the tables would differ.
            SectionCard(title = "Liga") {
                InfoRow("Liga", "${data?.leagueName ?: prefs.leagueName} (${prefs.leagueId})")
                InfoRow("Dug po kolu", "${prefs.debtAmount} RSD")
                Text(
                    "Isto za sve u ligi. Menja se samo u novoj verziji aplikacije.",
                    style = MaterialTheme.typography.labelSmall, color = App.colors.muted, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item {
            SectionCard(title = "Obaveštenja", subtitle = "Najviše 3 dnevno · tiho od 0 do 8 h") {
                Toggle("Kolo završeno", "Ko duguje i zanimljivost kola", nSettle) { nSettle = it; prefs.notifSettle = it }
                Toggle("Posle roka", "Kapiteni, čipovi i hitovi", nDeadline) { nDeadline = it; prefs.notifDeadline = it }
                Toggle("Tokom kola", "Promene na dnu i napeta borba za dno", nMatchday) { nMatchday = it; prefs.notifMatchday = it }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Na nekim telefonima (Xiaomi, Huawei, Samsung) štednja baterije odlaže proveru u pozadini. Isključi optimizaciju za FPLBP ako obaveštenja kasne.",
                    style = MaterialTheme.typography.bodySmall, color = App.colors.muted,
                )
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }) { Text("Podešavanja baterije") }
            }
        }
        item {
            SectionCard(title = "Podaci") {
                Text("Poslednje osvežavanje: ${time(prefs.lastSync)}", style = MaterialTheme.typography.bodySmall, color = App.colors.muted)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val pad = PaddingValues(horizontal = 6.dp)
                    Button(onClick = vm::refresh, modifier = Modifier.weight(1f), contentPadding = pad) { Text("Osveži", maxLines = 1) }
                    OutlinedButton(
                        onClick = { export.launch("fplbp-${System.currentTimeMillis() / 1000}.json") }, enabled = data != null,
                        modifier = Modifier.weight(1f), contentPadding = pad,
                    ) { Text("Izvezi JSON", maxLines = 1) }
                    OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.weight(1f), contentPadding = pad) {
                        Text("Resetuj", color = App.colors.debt, maxLines = 1)
                    }
                }
            }
        }
        item {
            SectionCard(title = "O aplikaciji") {
                Text("FPLBP ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Nezvanična aplikacija. Podaci sa javnog FPL API-ja, čuvaju se samo na ovom telefonu.",
                    style = MaterialTheme.typography.bodySmall, color = App.colors.muted,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ShareImage.appLink() != null) {
                        OutlinedButton(onClick = { ShareImage.shareAppLink(context) }, modifier = Modifier.weight(1f)) {
                            Text("Podeli link", maxLines = 1)
                        }
                    }
                    if (update != null) {
                        Button(
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update!!.url))) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Nova verzija", maxLines = 1) }
                    } else if (BuildConfig.GITHUB_REPO.isNotBlank()) {
                        OutlinedButton(onClick = { vm.checkUpdate() }, modifier = Modifier.weight(1f)) { Text("Ažuriranja", maxLines = 1) }
                    }
                }
            }
        }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Obrisati podatke?") },
        text = { Text("Sve se ponovo učitava sa FPL-a. Dug se računa iz bodova, pa se ništa ne gubi.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetData() }) { Text("Obriši") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Otkaži") } },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = App.colors.muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Toggle(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun exportJson(d: LeagueData): String {
    val root = JSONObject()
    root.put("league", JSONObject().put("id", d.leagueId).put("name", d.leagueName).put("debtPerGw", d.debt))
    root.put("teams", JSONArray(d.teams.map { JSONObject().put("entry", it.entryId).put("name", it.name).put("manager", it.manager) }))
    root.put("debtTable", JSONArray(d.debtTable.map {
        JSONObject().put("entry", it.entryId).put("team", d.teamName(it.entryId))
            .put("timesLast", it.timesLast).put("total", it.total.toBigDecimal(2).toPlainString())
    }))
    root.put("gameweeks", JSONArray(d.gws.map { g ->
        JSONObject().put("gw", g.gw).put("status", g.status.name)
            .put("losers", JSONArray(g.outcome?.losers.orEmpty()))
            .put("scores", JSONArray(g.scores.map {
                JSONObject().put("entry", it.entryId).put("points", it.points).put("transferCost", it.transferCost)
                    .put("net", it.net).put("bench", it.benchPoints).put("chip", it.chip ?: JSONObject.NULL)
            }))
    }))
    return root.toString(2)
}

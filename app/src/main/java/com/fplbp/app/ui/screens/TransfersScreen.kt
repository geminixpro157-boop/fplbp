package com.fplbp.app.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fplbp.app.data.LeagueData
import com.fplbp.app.domain.TransferAnalysis
import com.fplbp.app.ui.components.EmptyState
import com.fplbp.app.ui.components.Pill
import com.fplbp.app.ui.components.SectionCard
import com.fplbp.app.ui.components.TeamDot
import com.fplbp.app.ui.components.teamColor
import com.fplbp.app.ui.theme.App

fun signed(n: Int) = if (n > 0) "+$n" else "$n"

@Composable
fun signColor(n: Int): Color = when {
    n > 0 -> App.colors.safe
    n < 0 -> App.colors.debt
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
fun TransfersScreen(data: LeagueData) {
    if (data.transfers.isEmpty() && data.transferSummary.values.all { it.freeHits.isEmpty() }) {
        return EmptyState("Još nema transfera (prvi su mogući od kola 2).")
    }
    var selected by rememberSaveable { mutableLongStateOf(data.teams.firstOrNull()?.entryId ?: 0L) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Overview(data) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data.teams.forEach { t ->
                    FilterChip(
                        selected = selected == t.entryId,
                        onClick = { selected = t.entryId },
                        label = { Text(t.name) },
                        leadingIcon = { TeamDot(teamColor(data, t.entryId)) },
                    )
                }
            }
        }
        item { TeamTransfers(data, selected) }
    }
}

@Composable
private fun Overview(data: LeagueData) {
    val rows = data.transferSummary.values.sortedByDescending { it.netAfterHits }
    SectionCard(
        title = "Transferi u sezoni",
        subtitle = "Bodovi novih igrača dok su bili u timu, naspram prodatih u istim kolima (bez kapitena)",
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text("Tim", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
            Header("Tr.", 34)
            Header("Dobit", 52)
            Header("Hitovi", 52)
            Header("Neto", 52)
        }
        HorizontalDivider(color = App.colors.grid)
        rows.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    TeamDot(teamColor(data, s.entryId))
                    Spacer(Modifier.width(8.dp))
                    Text(data.teamName(s.entryId), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Cell("${s.transfers}", 34)
                Cell(signed(s.heldNet), 52, signColor(s.heldNet))
                Cell(if (s.hits > 0) "−${s.hits}" else "0", 52, if (s.hits > 0) App.colors.debt else App.colors.muted)
                Cell(signed(s.netAfterHits), 52, signColor(s.netAfterHits), bold = true)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Neto = dobit od transfera minus hitovi. Free Hit kola se računaju posebno.",
            style = MaterialTheme.typography.labelSmall, color = App.colors.muted,
        )
    }
}

@Composable
private fun TeamTransfers(data: LeagueData, entryId: Long) {
    val summary = data.transferSummary[entryId]
    val list = data.transfers.filter { it.entryId == entryId }.sortedByDescending { it.gw }
    SectionCard(title = data.teamName(entryId), subtitle = "Za svaki transfer: isto kolo · dok je bio u timu") {
        if (summary != null) {
            summary.best?.let { Highlight("Najbolji", it, data) }
            summary.worst?.let { Highlight("Najgori", it, data) }
            summary.freeHits.forEach { fh ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill("Free Hit · K${fh.gw}", App.colors.gold)
                    Spacer(Modifier.width(8.dp))
                    Text("${fh.points} umesto ${fh.wouldHave}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(signed(fh.diff), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = signColor(fh.diff))
                }
            }
            if (summary.best != null || summary.worst != null || summary.freeHits.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = App.colors.grid)
            }
        }
        if (list.isEmpty()) {
            Text("Nijedan transfer.", style = MaterialTheme.typography.bodySmall, color = App.colors.muted)
        }
        var lastGw = -1
        list.forEach { r ->
            if (r.gw != lastGw) {
                lastGw = r.gw
                Row(Modifier.padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Kolo ${r.gw}", style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
                    if (r.wildcard) { Spacer(Modifier.width(8.dp)); Pill("Wildcard", App.colors.gold) }
                    val hit = data.gws.firstOrNull { it.gw == r.gw }?.scores?.firstOrNull { it.entryId == entryId }?.transferCost ?: 0
                    if (hit > 0) { Spacer(Modifier.width(8.dp)); Text("hit −$hit", style = MaterialTheme.typography.labelMedium, color = App.colors.debt) }
                }
            }
            TransferRow(data, r)
        }
    }
}

@Composable
private fun Highlight(label: String, r: TransferAnalysis.Result, data: LeagueData) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(70.dp), style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
        Text(
            "${data.playerName(r.elementOut)} → ${data.playerName(r.elementIn)} · K${r.gw}",
            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(signed(r.heldDiff), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = signColor(r.heldDiff))
    }
}

@Composable
fun TransferRow(data: LeagueData, r: TransferAnalysis.Result, weekOnly: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${data.playerName(r.elementOut)} → ${data.playerName(r.elementIn)}",
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                signed(if (weekOnly) r.weekDiff else r.heldDiff), Modifier.width(44.dp), textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = signColor(if (weekOnly) r.weekDiff else r.heldDiff),
            )
        }
        val week = "to kolo ${r.weekIn} : ${r.weekOut}"
        val held = when (r.heldGws.size) {
            0 -> "nije zadržan"
            1 -> "zadržan 1 kolo"
            else -> "zadržan ${r.heldGws.size} kola: ${r.heldIn} : ${r.heldOut}"
        }
        Text(if (weekOnly) week else "$week · $held", style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
    }
}

@Composable
private fun Header(text: String, width: Int) =
    Text(text, Modifier.width(width.dp), style = MaterialTheme.typography.labelSmall, color = App.colors.muted, textAlign = TextAlign.End)

@Composable
private fun Cell(text: String, width: Int, color: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false) =
    Text(
        text, Modifier.width(width.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium,
        color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )

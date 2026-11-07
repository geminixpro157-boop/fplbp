package com.fplbp.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fplbp.app.data.GwView
import com.fplbp.app.data.LeagueData
import com.fplbp.app.domain.Chips
import com.fplbp.app.domain.Fmt
import com.fplbp.app.domain.SeasonStats
import com.fplbp.app.ui.components.ChartSeries
import com.fplbp.app.ui.components.Legend
import com.fplbp.app.ui.components.LineChart
import com.fplbp.app.ui.components.Pill
import com.fplbp.app.ui.components.PositionHeatmap
import com.fplbp.app.ui.components.SectionCard
import com.fplbp.app.ui.components.StatusBadge
import com.fplbp.app.ui.components.TeamDot
import com.fplbp.app.ui.components.teamColor
import com.fplbp.app.ui.theme.App

@Composable
fun GameweeksScreen(data: LeagueData, onGw: (Int) -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Lista", "Grafikoni").forEachIndexed { i, label ->
                    SegmentedButton(selected = mode == i, onClick = { mode = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }
        }
        if (mode == 0) {
            items(data.gws.reversed(), key = { it.gw }) { v -> GwCard(data, v) { onGw(v.gw) } }
        } else {
            item { PointsChart(data) }
            item { HeatmapCard(data) }
        }
    }
}

@Composable
private fun GwCard(data: LeagueData, v: GwView, onClick: () -> Unit) {
    val o = v.outcome
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kolo ${v.gw}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusBadge(v.status)
        }
        Spacer(Modifier.height(8.dp))
        v.scores.sortedByDescending { it.net }.forEach { s ->
            val loser = o != null && s.entryId in o.losers
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamDot(teamColor(data, s.entryId))
                Spacer(Modifier.width(8.dp))
                Text(
                    data.teamName(s.entryId), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (loser) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (loser) App.colors.debt else MaterialTheme.colorScheme.onSurface,
                )
                s.chip?.let { Pill(Chips.short(it), App.colors.gold); Spacer(Modifier.width(6.dp)) }
                val scoreText = if (s.transferCost > 0) "${s.points} −${s.transferCost} = ${s.net}" else "${s.net}"
                Text(scoreText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        if (o != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                (if (v.isFinal) "Duguje: " else "Trenutno duguje: ") +
                    o.losers.joinToString { data.teamName(it) } + " · " + Fmt.rsdShort(o.share) +
                    (if (o.losers.size > 1) " svako" else ""),
                style = MaterialTheme.typography.labelMedium, color = App.colors.debt,
            )
        }
    }
}

@Composable
private fun PointsChart(data: LeagueData) {
    val gws = data.gws
    val teams = data.debtTable.map { it.entryId }
    val series = teams.map { id ->
        ChartSeries(data.teamName(id), teamColor(data, id), gws.map { g -> g.scores.firstOrNull { it.entryId == id }?.net?.toDouble() })
    } + ChartSeries("Prosek lige", App.colors.muted, gws.map { SeasonStats.leagueAverage(it.toScores()) }, dashed = true)
    SectionCard(title = "Neto bodovi po kolu", subtitle = "Posle minusa za transfere") {
        LineChart(gws.map { "${it.gw}" }, series, yFormat = { Fmt.decimal(it, 0) }, tooltipTitle = { "Kolo ${gws[it].gw}" })
        Spacer(Modifier.height(8.dp))
        Legend(series.map { it.name to it.color }, dashedLast = true)
    }
}

@Composable
private fun HeatmapCard(data: LeagueData) {
    val gws = data.gws
    val teams = data.debtTable.map { it.entryId }
    SectionCard(title = "Plasman po kolu", subtitle = "${teams.size} = poslednji (plaća). Jača boja = gore.") {
        PositionHeatmap(
            rows = teams.map { id -> data.teamName(id) to gws.map { g -> g.outcome?.ranks?.get(id) } },
            gws = gws.map { it.gw },
            teamCount = teams.size,
        )
    }
}

package com.fplbp.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fplbp.app.data.LeagueData
import com.fplbp.app.domain.Fmt
import com.fplbp.app.domain.TeamSeasonStats
import com.fplbp.app.domain.poena
import com.fplbp.app.ui.components.BarItem
import com.fplbp.app.ui.components.HBarChart
import com.fplbp.app.ui.components.PositionHeatmap
import com.fplbp.app.ui.components.SectionCard
import com.fplbp.app.ui.components.TeamDot
import com.fplbp.app.ui.components.teamColor
import com.fplbp.app.ui.theme.App

@Composable
fun StatsScreen(data: LeagueData, onTeam: (Long) -> Unit) {
    val stats = data.seasonStats.values.toList()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Highlights(data, stats) }
        item { BarCard(data, "Puta poslednji", "Uključena deljena dna", stats) { it.timesLast } }
        item { BarCard(data, "Minusi za transfere", "Ukupno bodova izgubljeno na hitove", stats, suffix = { s -> " (${s.hitsCount}×)" }) { it.hitsPoints } }
        item { BarCard(data, "Bodovi ostavljeni na klupi", "Bez kola sa Bench Boost-om", stats) { it.benchPoints } }
        if (data.gws.size >= 2) item { HeatmapCard(data) }
    }
}

@Composable
private fun Highlights(data: LeagueData, stats: List<TeamSeasonStats>) {
    if (stats.isEmpty()) return
    SectionCard(title = "Sramota i slava") {
        val lines = buildList {
            stats.maxByOrNull { it.timesLast }?.takeIf { it.timesLast > 0 }?.let {
                add("Najčešće na dnu" to "${data.teamName(it.entryId)} · ${it.timesLast}×")
            }
            stats.maxByOrNull { it.longestLosingStreak }?.takeIf { it.longestLosingStreak > 1 }?.let {
                add("Najduži niz plaćanja" to "${data.teamName(it.entryId)} · ${it.longestLosingStreak} kola")
            }
            stats.maxByOrNull { it.currentSafeStreak }?.takeIf { it.currentSafeStreak > 0 }?.let {
                add("Najduže bez duga (sada)" to "${data.teamName(it.entryId)} · ${it.currentSafeStreak} kola")
            }
            stats.filter { it.closestEscape != null }.minByOrNull { it.closestEscape!!.second }?.let {
                add("Najtesnije spasavanje" to "${data.teamName(it.entryId)} · ${poena(it.closestEscape!!.second)} (K${it.closestEscape.first})")
            }
            stats.mapNotNull { s -> s.best?.let { s to it } }.maxByOrNull { it.second.second }?.let { (s, b) ->
                add("Najbolje kolo" to "${data.teamName(s.entryId)} · ${b.second} (K${b.first})")
            }
            stats.mapNotNull { s -> s.worst?.let { s to it } }.minByOrNull { it.second.second }?.let { (s, w) ->
                add("Najgore kolo" to "${data.teamName(s.entryId)} · ${w.second} (K${w.first})")
            }
            stats.maxByOrNull { it.hitsPoints }?.takeIf { it.hitsPoints > 0 }?.let {
                add("Kralj hitova" to "${data.teamName(it.entryId)} · −${it.hitsPoints}")
            }
        }
        lines.forEachIndexed { i, (label, value) ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = App.colors.muted)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            if (i < lines.lastIndex) HorizontalDivider(color = App.colors.grid)
        }
    }
}

@Composable
private fun BarCard(
    data: LeagueData, title: String, subtitle: String, stats: List<TeamSeasonStats>,
    suffix: (TeamSeasonStats) -> String = { "" },
    value: (TeamSeasonStats) -> Int,
) {
    SectionCard(title = title, subtitle = subtitle) {
        HBarChart(stats.sortedByDescending(value).map { s ->
            BarItem(data.teamName(s.entryId), value(s).toDouble(), teamColor(data, s.entryId), "${value(s)}${suffix(s)}")
        })
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

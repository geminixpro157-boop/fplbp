package com.fplbp.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
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
        item { TotalPoints(data, onTeam) }
        item { Highlights(data, stats) }
        item { BarCard(data, "Minusi za transfere", "Ukupno bodova izgubljeno na hitove", stats, suffix = { s -> " (×${s.hitsCount})" }) { it.hitsPoints } }
        item { BarCard(data, "Bodovi ostavljeni na klupi", "Bez kola sa Bench Boost-om", stats) { it.benchPoints } }
        if (data.gws.size >= 2) item { HeatmapCard(data) }
    }
}

/** Overall points (net, after hits) from confirmed gameweeks; the current one is added in amber. */
@Composable
private fun TotalPoints(data: LeagueData, onTeam: (Long) -> Unit) {
    val confirmed = data.teams.associate { t ->
        t.entryId to data.gws.filter { it.isFinal }.sumOf { g -> g.scores.firstOrNull { it.entryId == t.entryId }?.net ?: 0 }
    }
    val live = data.liveGw
    val rows = confirmed.entries.sortedByDescending { it.value }
    val leader = rows.firstOrNull()?.value ?: 0
    SectionCard(title = "Ukupni bodovi", subtitle = "Posle minusa za transfere") {
        rows.forEachIndexed { i, (id, pts) ->
            val now = live?.scores?.firstOrNull { it.entryId == id }?.net
            Row(
                Modifier.fillMaxWidth().clickable { onTeam(id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", Modifier.width(24.dp), style = MaterialTheme.typography.bodyMedium, color = App.colors.muted)
                TeamDot(teamColor(data, id))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(data.teamName(id), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (pts < leader) Text("−${leader - pts} od prvog", style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$pts", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (live != null && now != null) {
                        Text("+$now u toku → ${pts + now}", style = MaterialTheme.typography.labelSmall, color = App.colors.provisional)
                    }
                }
            }
            if (i < rows.lastIndex) HorizontalDivider(color = App.colors.grid)
        }
    }
}

private data class Fame(val emoji: String, val label: String, val team: Long, val value: String, val note: String? = null)

@Composable
private fun Highlights(data: LeagueData, stats: List<TeamSeasonStats>) {
    if (stats.isEmpty()) return
    val shame = buildList {
        stats.maxByOrNull { it.timesLast }?.takeIf { it.timesLast > 0 }?.let { s ->
            val debt = data.debtTable.firstOrNull { it.entryId == s.entryId }?.total
            add(Fame("💸", "Najčešće na dnu", s.entryId, "×${s.timesLast}", debt?.let { Fmt.rsdShort(it) }))
        }
        stats.maxByOrNull { it.longestLosingStreak }?.takeIf { it.longestLosingStreak > 1 }?.let {
            add(Fame("🔁", "Najduži niz plaćanja", it.entryId, "${it.longestLosingStreak} kola"))
        }
        stats.mapNotNull { s -> s.worst?.let { s to it } }.minByOrNull { it.second.second }?.let { (s, w) ->
            add(Fame("🥶", "Najgore kolo", s.entryId, "${w.second}", "kolo ${w.first}"))
        }
        stats.maxByOrNull { it.hitsPoints }?.takeIf { it.hitsPoints > 0 }?.let {
            add(Fame("✂️", "Kralj hitova", it.entryId, "−${it.hitsPoints}"))
        }
    }
    val glory = buildList {
        stats.maxByOrNull { it.currentSafeStreak }?.takeIf { it.currentSafeStreak > 0 }?.let {
            add(Fame("🛡️", "Najduže bez duga", it.entryId, "${it.currentSafeStreak} kola", "sada"))
        }
        stats.filter { it.closestEscape != null }.minByOrNull { it.closestEscape!!.second }?.let {
            add(Fame("😅", "Najtesnije spasavanje", it.entryId, poena(it.closestEscape!!.second), "kolo ${it.closestEscape.first}"))
        }
        stats.mapNotNull { s -> s.best?.let { s to it } }.maxByOrNull { it.second.second }?.let { (s, b) ->
            add(Fame("🚀", "Najbolje kolo", s.entryId, "${b.second}", "kolo ${b.first}"))
        }
    }
    // One lazy-list item holds both cards, so stack them explicitly.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (shame.isNotEmpty()) FameCard(data, "😬 Sramota", shame, App.colors.debt)
        if (glory.isNotEmpty()) FameCard(data, "🏆 Slava", glory, App.colors.gold)
    }
}

@Composable
private fun FameCard(data: LeagueData, title: String, rows: List<Fame>, accent: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            rows.forEachIndexed { i, f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f.emoji, Modifier.width(32.dp), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f)) {
                        Text(f.label, style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamDot(teamColor(data, f.team))
                            Spacer(Modifier.width(6.dp))
                            Text(data.teamName(f.team), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(f.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        f.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = App.colors.muted, modifier = Modifier.padding(top = 2.dp)) }
                    }
                }
                if (i < rows.lastIndex) HorizontalDivider(color = App.colors.grid.copy(alpha = 0.6f))
            }
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
    SectionCard(title = "Plasman po kolu", subtitle = "Broj = mesto u kolu · ${teams.size} plaća") {
        PositionHeatmap(
            rows = teams.map { id -> data.teamName(id) to gws.map { g -> g.outcome?.ranks?.get(id) } },
            gws = gws.map { it.gw },
            teamCount = teams.size,
        )
    }
}

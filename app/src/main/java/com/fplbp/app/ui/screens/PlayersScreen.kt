package com.fplbp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fplbp.app.data.Category
import com.fplbp.app.data.LeagueData
import com.fplbp.app.data.PlayerContribution
import com.fplbp.app.domain.poena
import com.fplbp.app.ui.components.BarItem
import com.fplbp.app.ui.components.ColumnChart
import com.fplbp.app.ui.components.EmptyState
import com.fplbp.app.ui.components.HBarChart
import com.fplbp.app.ui.components.SectionCard
import com.fplbp.app.ui.components.ClubChip
import com.fplbp.app.ui.components.DonutChart
import com.fplbp.app.ui.components.Slice
import com.fplbp.app.ui.components.StatTile
import com.fplbp.app.ui.components.teamColor
import com.fplbp.app.ui.theme.App

private const val LEAGUE = -1L

/** Color follows the category (fixed order), never its rank. */
@Composable
private fun categoryColor(cat: String): Color =
    App.colors.series[Category.POSITIVE.indexOf(cat).coerceAtLeast(0) % App.colors.series.size]

@Composable
fun PlayersScreen(data: LeagueData) {
    var selected by rememberSaveable { mutableLongStateOf(LEAGUE) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selected == LEAGUE, onClick = { selected = LEAGUE }, label = { Text("Cela liga") })
                data.teams.forEach { t ->
                    FilterChip(selected = selected == t.entryId, onClick = { selected = t.entryId }, label = { Text(t.name) })
                }
            }
        }
        if (data.contributions.isEmpty()) {
            item { EmptyState("Još nema podataka o igračima.") }
        } else if (selected == LEAGUE) {
            leagueView(data)
        } else {
            teamView(data, selected)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.teamView(data: LeagueData, entryId: Long) {
    val list = data.contributions[entryId].orEmpty()
    val totals = HashMap<String, Int>()
    list.forEach { c -> c.byCategory.forEach { (k, v) -> totals[k] = (totals[k] ?: 0) + v } }
    val positive = Category.POSITIVE.map { it to (totals[it] ?: 0) }.filter { it.second > 0 }.sortedByDescending { it.second }
    val minus = Category.MINUS.map { it to (totals[it] ?: 0) }.filter { it.second != 0 }.sortedBy { it.second }

    item {
        SectionCard(title = "Odakle dolaze bodovi", subtitle = "Ceo tim, sa kapitenskim množenjem") {
            val top = positive.take(5)
            val rest = positive.drop(5).sumOf { it.second }
            val slices = top.map { (cat, v) -> Slice(Category.label(cat), v, categoryColor(cat)) } +
                listOfNotNull(if (rest > 0) Slice("Ostalo", rest, App.colors.muted) else null)
            DonutChart(slices, centerValue = "${positive.sumOf { it.second }}", centerLabel = "bodova")
        }
    }
    if (minus.isNotEmpty()) item {
        SectionCard(title = "Minusi", subtitle = "Bodovi izgubljeni, po vrsti (ukupno ${minus.sumOf { it.second }})") {
            HBarChart(minus.map { (cat, v) ->
                BarItem(Category.label(cat), -v.toDouble(), App.colors.debt, "$v")
            }, labelWidth = 120.dp)
        }
    }
    item {
        SectionCard(title = "Top 10 igrača", subtitle = "Bodovi koji su ušli u zbir tima") {
            val maxTotal = list.firstOrNull()?.total?.coerceAtLeast(1) ?: 1
            val color = teamColor(data, entryId)
            list.take(10).forEachIndexed { i, c ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", Modifier.width(24.dp), style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
                        Text(data.playerName(c.element), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(6.dp))
                        ClubChip(data.clubShort(c.element), data.players[c.element]?.elementType)
                        Spacer(Modifier.weight(1f))
                        Text("${c.total}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.padding(start = 24.dp).fillMaxWidth(c.total.toFloat() / maxTotal).height(6.dp)
                        .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).background(color))
                    Spacer(Modifier.height(4.dp))
                    Text(breakdown(c), Modifier.padding(start = 24.dp), style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "G golovi · A asistencije · ČM čista mreža · B bonus · Min minutaža · DEF def. doprinos · Odb odbrane · " +
                    "kapitenski bodovi su uračunati",
                style = MaterialTheme.typography.labelSmall, color = App.colors.muted,
            )
        }
    }
    val caps = data.captains[entryId].orEmpty()
    if (caps.isNotEmpty()) item {
        SectionCard(title = "Kapiteni", subtitle = "Bodovi kapitena po kolu (sa množenjem)") {
            val total = caps.sumOf { it.points }
            val best = caps.maxBy { it.points }
            val worst = caps.minBy { it.points }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Ukupno", "$total", Modifier.weight(1f))
                StatTile("Najbolji", "${best.points}", Modifier.weight(1f), sub = "${data.playerName(best.element)} · K${best.gw}")
                StatTile("Najgori", "${worst.points}", Modifier.weight(1f), sub = "${data.playerName(worst.element)} · K${worst.gw}")
            }
            Spacer(Modifier.height(12.dp))
            val flopColor = App.colors.debt
            ColumnChart(
                xLabels = caps.map { "${it.gw}" },
                values = caps.map { it.points },
                color = teamColor(data, entryId),
                highlight = { i -> if (caps[i].points <= 4) flopColor else null },
                tooltip = { i ->
                    val c = caps[i]
                    "Kolo ${c.gw}: ${data.playerName(c.element)} · ${poena(c.points)}" + if (c.passedToVice) " (vice-kapiten)" else ""
                },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.leagueView(data: LeagueData) {
    // Sum over all teams.
    val byPlayer = HashMap<Int, MutableList<Pair<Long, PlayerContribution>>>()
    data.contributions.forEach { (team, list) -> list.forEach { byPlayer.getOrPut(it.element) { mutableListOf() } += team to it } }
    val differentials = byPlayer.entries.filter { it.value.size == 1 }
        .map { Triple(it.key, it.value[0].first, it.value[0].second.total) }
        .sortedByDescending { it.third }.take(8)

    if (differentials.isNotEmpty()) item {
        SectionCard(title = "Diferencijali", subtitle = "Igrači koje je imao samo jedan tim") {
            HBarChart(differentials.map { (el, team, pts) ->
                BarItem(data.playerName(el), pts.toDouble(), teamColor(data, team), "$pts", sub = data.teamName(team))
            }, labelWidth = 110.dp)
        }
    }
    item {
        SectionCard(title = "Kapitenski bodovi po timu", subtitle = "Ukupno, sa množenjem") {
            HBarChart(data.teams.map { t ->
                val pts = data.captains[t.entryId].orEmpty().sumOf { it.points }
                BarItem(t.name, pts.toDouble(), teamColor(data, t.entryId), "$pts")
            }.sortedByDescending { it.value })
        }
    }
}

/** Full one-line breakdown, e.g. "G 24 · A 9 · ČM 4 · B 11 · Min 30 · Minusi −3". */
private fun breakdown(c: PlayerContribution): String {
    val parts = Category.POSITIVE.mapNotNull { cat -> c.byCategory[cat]?.takeIf { it != 0 }?.let { "${Category.short(cat)} $it" } }
    val minus = Category.MINUS.sumOf { c.byCategory[it] ?: 0 }
    return (parts + listOfNotNull(if (minus != 0) "Minusi $minus" else null)).joinToString(" · ") +
        if (c.captainExtra > 0) " · od toga kapiten +${c.captainExtra}" else ""
}

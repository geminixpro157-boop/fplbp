package com.fplbp.app.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.Modifier
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
import com.fplbp.app.ui.components.Segment
import com.fplbp.app.ui.components.StackedBar
import com.fplbp.app.ui.components.StatTile
import com.fplbp.app.ui.components.teamColor
import com.fplbp.app.ui.theme.App

private const val LEAGUE = -1L

/** Category order for the stacked bar; colors follow the validated categorical order. */
private val STACK = listOf("goals", "assists", "cs", "bonus", "minutes", "defcon", "saves")

@Composable
private fun categoryColor(cat: String): Color = App.colors.series[STACK.indexOf(cat).coerceAtLeast(0) % App.colors.series.size]

private fun stackKey(cat: String) = if (cat == "pens") "saves" else cat

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
    list.forEach { c -> c.byCategory.forEach { (k, v) -> totals[stackKey(k)] = (totals[stackKey(k)] ?: 0) + v } }
    val minus = totals["minus"] ?: 0

    item {
        SectionCard(title = "Odakle dolaze bodovi", subtitle = "Ceo tim, sa kapitenskim množenjem") {
            StackedBar(STACK.map { Segment(Category.label(it).let { l -> if (it == "saves") "Odbrane i penali" else l }, totals[it] ?: 0, categoryColor(it)) })
            if (minus != 0) {
                Spacer(Modifier.height(6.dp))
                Text("Minusi (kartoni, primljeni golovi, autogolovi, promašeni penali): $minus",
                    style = MaterialTheme.typography.bodySmall, color = App.colors.debt)
            }
        }
    }
    item {
        SectionCard(title = "Top 10 igrača", subtitle = "Bodovi koji su ušli u zbir tima") {
            HBarChart(list.take(10).map { c ->
                BarItem(
                    label = data.playerName(c.element),
                    value = c.total.toDouble(),
                    color = teamColor(data, entryId),
                    valueText = "${c.total}",
                    sub = topCategories(c),
                )
            })
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
    val top = byPlayer.entries.map { it.key to it.value.sumOf { p -> p.second.total } }.sortedByDescending { it.second }.take(10)
    val differentials = byPlayer.entries.filter { it.value.size == 1 }
        .map { Triple(it.key, it.value[0].first, it.value[0].second.total) }
        .sortedByDescending { it.third }.take(8)

    item {
        SectionCard(title = "Najvredniji igrači lige", subtitle = "Zbir bodova koje je doneo svim timovima") {
            HBarChart(top.map { (el, pts) ->
                BarItem(
                    label = data.playerName(el), value = pts.toDouble(), color = App.colors.gold, valueText = "$pts",
                    sub = byPlayer[el].orEmpty().joinToString { data.teamName(it.first) },
                )
            }, labelWidth = 110.dp)
        }
    }
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

private fun topCategories(c: PlayerContribution): String =
    c.byCategory.filter { it.value > 0 && it.key != "minutes" }
        .entries.sortedByDescending { it.value }.take(2)
        .joinToString(" · ") { "${Category.label(it.key)} ${it.value}" }
        .ifBlank { "Minutaža ${c.byCategory["minutes"] ?: 0}" }

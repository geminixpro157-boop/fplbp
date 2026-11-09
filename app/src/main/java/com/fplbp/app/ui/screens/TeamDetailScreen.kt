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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fplbp.app.data.LeagueData
import com.fplbp.app.domain.Fmt
import com.fplbp.app.domain.Rational
import com.fplbp.app.domain.SeasonStats
import com.fplbp.app.ui.components.ChartSeries
import com.fplbp.app.ui.components.EmptyState
import com.fplbp.app.ui.components.Legend
import com.fplbp.app.ui.components.LineChart
import com.fplbp.app.ui.components.SectionCard
import com.fplbp.app.ui.components.StatTile
import com.fplbp.app.ui.components.teamColor
import com.fplbp.app.ui.theme.App

@Composable
fun TeamDetailScreen(data: LeagueData, entryId: Long, onGw: (Int) -> Unit) {
    val team = data.teamById[entryId] ?: return EmptyState("Tim nije pronađen.")
    val debt = data.debtTable.firstOrNull { it.entryId == entryId }
    val st = data.seasonStats[entryId]
    val color = teamColor(data, entryId)
    val finals = data.gws.filter { it.isFinal && it.scores.any { s -> s.entryId == entryId } }
    val losses = finals.filter { entryId in it.outcome?.losers.orEmpty() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard {
                Text(team.manager, style = MaterialTheme.typography.bodyMedium, color = App.colors.muted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val max = data.debtTable.maxOfOrNull { it.total } ?: Rational.ZERO
                    StatTile("Dug", debt?.let { Fmt.rsdShort(it.total) } ?: "0 RSD", Modifier.weight(1f),
                        valueColor = debtColor(debt?.total ?: Rational.ZERO, max))
                    StatTile("Puta poslednji", "${debt?.timesLast ?: 0}", Modifier.weight(1f))
                    StatTile("Prosek", st?.let { Fmt.decimal(it.avgNet, 1) } ?: "–", Modifier.weight(1f))
                }
            }
        }
        if (finals.size >= 2) {
            item {
                SectionCard(title = "Plasman po kolu", subtitle = "1 = najbolji, ${data.teams.size} = plaća") {
                    LineChart(
                        xLabels = finals.map { "${it.gw}" },
                        series = listOf(ChartSeries(team.name, color, finals.map { g -> g.outcome?.ranks?.get(entryId)?.toDouble() })),
                        yFormat = { it.toInt().toString() },
                        invertY = true,
                        height = 160.dp,
                        tooltipTitle = { "Kolo ${finals[it].gw}" },
                    )
                }
            }
        }
        item {
            SectionCard(title = "Kola u kojima je platio", subtitle = if (losses.isEmpty()) "Još nijedno. Za sada." else null) {
                losses.reversed().forEachIndexed { i, g ->
                    val o = g.outcome!!
                    val s = g.scores.first { it.entryId == entryId }
                    Row(Modifier.fillMaxWidth().clickable { onGw(g.gw) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Kolo ${g.gw}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            buildString {
                                append("${s.net}")
                                if (s.transferCost > 0) append(" (−${s.transferCost})")
                                if (o.losers.size > 1) append(" · deljeno")
                            },
                            style = MaterialTheme.typography.bodySmall, color = App.colors.muted,
                        )
                        Text("  " + Fmt.rsdShort(o.share), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = App.colors.debt)
                    }
                    if (i < losses.lastIndex) HorizontalDivider(color = App.colors.grid)
                }
            }
        }
        st?.let { s ->
            item {
                SectionCard(title = "Sitnice") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Hitovi", "−${s.hitsPoints}", Modifier.weight(1f), sub = "${s.hitsCount} kola")
                        StatTile("Klupa", "${s.benchPoints}", Modifier.weight(1f), sub = "izgubljeno")
                        StatTile("Najduži niz", "${s.longestLosingStreak}", Modifier.weight(1f), sub = "plaćanja zaredom")
                    }
                }
            }
        }
    }
}

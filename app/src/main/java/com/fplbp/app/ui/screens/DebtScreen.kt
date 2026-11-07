package com.fplbp.app.ui.screens

import androidx.compose.foundation.clickable
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
import com.fplbp.app.data.GwStatus
import com.fplbp.app.data.LeagueData
import com.fplbp.app.domain.Fmt
import com.fplbp.app.domain.Rational
import com.fplbp.app.domain.joinSr
import com.fplbp.app.domain.poena
import com.fplbp.app.ui.components.ChartSeries
import com.fplbp.app.ui.components.Legend
import com.fplbp.app.ui.components.LineChart
import com.fplbp.app.ui.components.Pill
import com.fplbp.app.ui.components.SectionCard
import com.fplbp.app.ui.components.StatusBadge
import com.fplbp.app.ui.components.TeamDot
import com.fplbp.app.ui.components.teamColor
import com.fplbp.app.ui.theme.App

@Composable
fun DebtScreen(data: LeagueData, onTeam: (Long) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PotCard(data) }
        data.liveGw?.let { live -> item { DangerZone(data, live.gw) } }
        item { DebtTable(data, onTeam) }
        if (data.raceGws.size >= 2) item { DebtRace(data) }
    }
}

@Composable
private fun PotCard(data: LeagueData) {
    SectionCard {
        Text("Ukupno u kasi za večeru", style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
        Text(Fmt.rsd(data.pot), style = MaterialTheme.typography.headlineMedium, color = App.colors.gold)
        Text(
            "${data.finalCount} završenih kola × ${Fmt.rsd(data.debt)}",
            style = MaterialTheme.typography.bodySmall, color = App.colors.muted,
        )
    }
}

@Composable
private fun DangerZone(data: LeagueData, gw: Int) {
    val v = data.gws.first { it.gw == gw }
    val o = v.outcome ?: return
    val subtitle = if (v.fixturesTotal > 0) "Odigrano ${v.fixturesDone}/${v.fixturesTotal} utakmica" else null
    SectionCard(title = "Opasna zona · Kolo $gw", subtitle = subtitle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(v.status)
            Spacer(Modifier.width(8.dp))
            Text(
                if (v.status == GwStatus.AWAITING) "FPL još potvrđuje bodove" else "Uključeni predviđeni bonus i izmene",
                style = MaterialTheme.typography.labelSmall, color = App.colors.muted,
            )
        }
        Spacer(Modifier.height(10.dp))
        val names = o.losers.map { data.teamName(it) }
        Text(
            if (o.losers.size == 1) "Trenutno plaća: ${names[0]}" else "Trenutno dele dug: ${joinSr(names)}",
            style = MaterialTheme.typography.titleMedium, color = App.colors.debt,
        )
        val gap = o.gapToSafety
        Text(
            buildString {
                append("${poena(o.lowestNet)} neto")
                if (gap != null) append(" · ${poena(gap)} do spasa")
                if (o.losers.size > 1) append(" · po ${Fmt.rsdShort(o.share)}")
            },
            style = MaterialTheme.typography.bodySmall, color = App.colors.muted,
        )
        Spacer(Modifier.height(10.dp))
        v.scores.sortedByDescending { it.net }.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamDot(teamColor(data, s.entryId))
                Spacer(Modifier.width(8.dp))
                Text(data.teamName(s.entryId), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (s.entryId in o.losers) App.colors.debt else MaterialTheme.colorScheme.onSurface)
                if (s.transferCost > 0) Text("-${s.transferCost}  ", style = MaterialTheme.typography.labelMedium, color = App.colors.debt)
                Text("${s.net}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DebtTable(data: LeagueData, onTeam: (Long) -> Unit) {
    SectionCard(title = "Tabela duga", subtitle = "Tapni tim za detalje") {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text("#", Modifier.width(24.dp), style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
            Text("Tim", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
            Text("Puta zadnji", Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall, color = App.colors.muted, textAlign = TextAlign.End)
            Text("Dug", Modifier.width(104.dp), style = MaterialTheme.typography.labelSmall, color = App.colors.muted, textAlign = TextAlign.End)
        }
        HorizontalDivider(color = App.colors.grid)
        data.debtTable.forEachIndexed { i, row ->
            val team = data.teamById[row.entryId]
            Row(
                Modifier.fillMaxWidth().clickable { onTeam(row.entryId) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", Modifier.width(24.dp), style = MaterialTheme.typography.bodyMedium, color = App.colors.muted)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeamDot(teamColor(data, row.entryId))
                        Spacer(Modifier.width(6.dp))
                        Text(team?.name ?: "?", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(team?.manager ?: "", style = MaterialTheme.typography.labelSmall, color = App.colors.muted, maxLines = 1)
                }
                Column(Modifier.width(72.dp), horizontalAlignment = Alignment.End) {
                    Text("${row.timesLast}", style = MaterialTheme.typography.bodyMedium)
                    if (row.timesLastShared > 0) Text("${row.timesLastShared} deljeno", style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
                }
                Text(
                    Fmt.rsd(row.total), Modifier.width(104.dp),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
                    color = if (row.total > Rational.ZERO) App.colors.debt else App.colors.safe,
                )
            }
            if (i < data.debtTable.lastIndex) HorizontalDivider(color = App.colors.grid)
        }
        if (data.debtTable.all { it.total == Rational.ZERO }) {
            Spacer(Modifier.height(8.dp))
            Pill("Još nijedno kolo nije završeno", App.colors.muted)
        }
    }
}

@Composable
private fun DebtRace(data: LeagueData) {
    val teams = data.debtTable.map { it.entryId }
    SectionCard(title = "Trka za dug", subtitle = "Ukupan dug posle svakog kola · tapni ili prevuci") {
        LineChart(
            xLabels = data.raceGws.map { "$it" },
            series = teams.map { ChartSeries(data.teamName(it), teamColor(data, it), data.raceSeries[it].orEmpty()) },
            yFormat = { Fmt.decimal(it, 0) },
            startAtZero = true,
            tooltipTitle = { "Posle kola ${data.raceGws[it]} (RSD)" },
        )
        Spacer(Modifier.height(8.dp))
        Legend(teams.map { data.teamName(it) to teamColor(data, it) })
    }
}

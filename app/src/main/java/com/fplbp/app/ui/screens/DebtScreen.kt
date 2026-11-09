package com.fplbp.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fplbp.app.data.GwStatus
import com.fplbp.app.data.GwView
import com.fplbp.app.data.LeagueData
import com.fplbp.app.data.PlayState
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
import com.fplbp.app.ui.components.kickoffShort
import com.fplbp.app.ui.components.teamColor
import com.fplbp.app.ui.theme.App
import com.fplbp.app.ui.time

/**
 * During a gameweek the live card comes first; between gameweeks the last result does.
 * The debt table always follows right after.
 */
@Composable
fun DebtScreen(data: LeagueData, onTeam: (Long) -> Unit, onGw: (Int) -> Unit) {
    val live = data.liveGw
    val lastFinal = data.gws.lastOrNull { it.isFinal }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (live != null) item { LiveCard(data, live) }
        else if (lastFinal != null) item { LastResultCard(data, lastFinal, onGw) }
        item { DebtTable(data, onTeam) }
        item { PotCard(data) }
        if (data.raceGws.size >= 2) item { DebtRace(data) }
    }
}

@Composable
private fun LastResultCard(data: LeagueData, v: GwView, onGw: (Int) -> Unit) {
    val o = v.outcome ?: return
    val names = o.losers.map { data.teamName(it) }
    SectionCard(Modifier.clickable { onGw(v.gw) }, title = "Kolo ${v.gw} · završeno", subtitle = "Tapni za detalje kola") {
        Text(
            if (o.losers.size == 1) "${names[0]} je platio ${Fmt.rsdShort(o.share)}"
            else "${joinSr(names)} dele po ${Fmt.rsdShort(o.share)}",
            style = MaterialTheme.typography.titleMedium, color = App.colors.debt,
        )
        o.gapToSafety?.let {
            Text("${poena(it)} do spasa", style = MaterialTheme.typography.bodySmall, color = App.colors.muted)
        }
        Spacer(Modifier.height(8.dp))
        v.scores.sortedByDescending { it.net }.forEach { s ->
            val loser = s.entryId in o.losers
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamDot(teamColor(data, s.entryId))
                Spacer(Modifier.width(8.dp))
                Text(data.teamName(s.entryId), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (loser) App.colors.debt else MaterialTheme.colorScheme.onSurface)
                if (s.transferCost > 0) Text("−${s.transferCost}  ", style = MaterialTheme.typography.labelMedium, color = App.colors.debt)
                Text("${s.net}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Red only for the biggest debt (all tied leaders), green only for zero. */
@Composable
fun debtColor(total: Rational, max: Rational): Color = when {
    total == Rational.ZERO -> App.colors.safe
    total == max -> App.colors.debt
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun PotCard(data: LeagueData) {
    SectionCard {
        Text("Ukupno u kasi", style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
        Text(Fmt.rsdShort(data.pot), style = MaterialTheme.typography.headlineMedium, color = App.colors.gold)
        Text(
            "${data.finalCount} završenih kola × ${Fmt.rsdShort(data.debt)}",
            style = MaterialTheme.typography.bodySmall, color = App.colors.muted,
        )
        data.liveGw?.takeIf { it.outcome != null }?.let { live ->
            Text(
                "+${Fmt.rsdShort(Rational.of(data.debt))} u toku (Kolo ${live.gw}) → " +
                    Fmt.rsdShort(data.pot + data.debt),
                style = MaterialTheme.typography.bodySmall, color = App.colors.provisional,
            )
        }
    }
}

/** Match status counts for one team's counting players. */
private data class TeamLive(
    val done: Int,
    val live: List<Pair<Int, Int>>,        // element to points so far
    val upcoming: List<Pair<Int, Long?>>,  // element to kickoff
    val bench: List<Triple<Int, PlayState, Long?>>,
) {
    val left get() = live.size + upcoming.size
}

private fun teamLive(data: LeagueData, v: GwView, entryId: Long): TeamLive? {
    val lu = v.lineups[entryId] ?: return null
    var done = 0
    val live = mutableListOf<Pair<Int, Int>>()
    val upcoming = mutableListOf<Pair<Int, Long?>>()
    lu.picks.filter { it.multiplier > 0 }.forEach { p ->
        val st = v.clubState(data.players[p.element]?.clubId)
        when (st.state) {
            PlayState.LIVE -> live += p.element to p.total
            PlayState.UPCOMING -> upcoming += p.element to st.nextKickoff
            else -> done++
        }
    }
    val chip = v.scores.firstOrNull { it.entryId == entryId }?.chip
    val bench = if (chip == "bboost") emptyList() else lu.picks
        .filter { it.multiplier == 0 && !it.subbedOut }
        .sortedBy { it.position }
        .map { p -> v.clubState(data.players[p.element]?.clubId).let { Triple(p.element, it.state, it.nextKickoff) } }
    return TeamLive(done, live, upcoming.sortedBy { it.second ?: Long.MAX_VALUE }, bench)
}

@Composable
private fun LiveCard(data: LeagueData, v: GwView) {
    val o = v.outcome ?: return
    var open by rememberSaveable(v.gw) { mutableStateOf(false) }
    val title = if (v.status == GwStatus.LIVE) "Uživo · Kolo ${v.gw}" else "Kolo ${v.gw}"
    val subtitle = buildString {
        if (v.status == GwStatus.LIVE) {
            if (v.fixturesTotal > 0) append("Odigrano ${v.fixturesDone}/${v.fixturesTotal} utakmica · ")
        } else {
            append("Sve utakmice odigrane · ")
        }
        append("osveženo ${time(data.lastSync).substringAfter(' ')}")
    }
    val expandable = v.status == GwStatus.LIVE // details only matter while matches are on
    SectionCard(Modifier.clickable(enabled = expandable) { open = !open }, title = title, subtitle = subtitle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(v.status)
            Spacer(Modifier.width(8.dp))
            Text(
                if (v.status == GwStatus.AWAITING) "Dug se upisuje kad FPL potvrdi bodove" else "Uključeni predviđeni bonus i izmene",
                style = MaterialTheme.typography.labelSmall, color = App.colors.muted, modifier = Modifier.weight(1f),
            )
            if (expandable) Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (open) "Sakrij detalje" else "Prikaži detalje", tint = App.colors.muted,
            )
        }
        Spacer(Modifier.height(10.dp))
        val sorted = v.scores.sortedByDescending { it.net }
        sorted.forEachIndexed { i, s ->
            val tl = teamLive(data, v, s.entryId)
            val loser = s.entryId in o.losers
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamDot(teamColor(data, s.entryId))
                Spacer(Modifier.width(8.dp))
                Text(data.teamName(s.entryId), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = if (loser) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (loser) App.colors.debt else MaterialTheme.colorScheme.onSurface)
                if (tl != null && tl.left > 0 && v.status == GwStatus.LIVE) {
                    Text("još ${tl.left}  ", style = MaterialTheme.typography.labelMedium, color = App.colors.provisional)
                }
                if (s.transferCost > 0) Text("−${s.transferCost}  ", style = MaterialTheme.typography.labelMedium, color = App.colors.debt)
                Text("${s.net}", Modifier.width(36.dp), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            if (tl != null) AnimatedVisibility(open && v.status == GwStatus.LIVE) {
                TeamLiveDetails(data, tl)
            }
            if (open && i < sorted.lastIndex) HorizontalDivider(color = App.colors.grid)
        }
        if (!open && v.status == GwStatus.LIVE) {
            Text("Tapni za igrače koji još igraju", style = MaterialTheme.typography.labelSmall, color = App.colors.muted,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun TeamLiveDetails(data: LeagueData, tl: TeamLive) {
    Column(Modifier.padding(start = 18.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row {
            Count("✓ ${tl.done} završilo", App.colors.muted)
            Count("● ${tl.live.size} igra", App.colors.safe)
            Count("◷ ${tl.upcoming.size} tek igra", App.colors.provisional)
        }
        if (tl.left == 0) {
            Text("Svi igrači su završili.", style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
        }
        if (tl.live.isNotEmpty()) Detail("Igraju sada", tl.live.joinToString(", ") { (e, pts) -> "${data.playerName(e)} $pts" })
        if (tl.upcoming.isNotEmpty()) Detail("Tek igraju", tl.upcoming.joinToString(", ") { (e, k) ->
            data.playerName(e) + (k?.let { " (${kickoffShort(it)})" } ?: "")
        })
        val benchLeft = tl.bench.filter { it.second == PlayState.LIVE || it.second == PlayState.UPCOMING }
        if (benchLeft.isNotEmpty() && tl.left > 0) Detail("Klupa (ulazi ako neko ne zaigra)", benchLeft.joinToString(", ") { (e, st, k) ->
            data.playerName(e) + when (st) { PlayState.LIVE -> " (igra)"; else -> k?.let { " (${kickoffShort(it)})" } ?: "" }
        })
    }
}

@Composable
private fun Count(text: String, color: Color) =
    Text(text + "   ", style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)

@Composable
private fun Detail(label: String, text: String) {
    Text(
        "$label: $text",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DebtTable(data: LeagueData, onTeam: (Long) -> Unit) {
    val max = data.debtTable.maxOfOrNull { it.total } ?: Rational.ZERO
    // Current (not yet confirmed) gameweek: who would pay and how much.
    val pending = data.liveGw?.outcome
    val pendingShare = pending?.share
    SectionCard(title = "Tabela dugovanja", subtitle = "Tapni tim za detalje") {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text("#", Modifier.width(24.dp), style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
            Text("Tim", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
            Text("Puta poslednji", Modifier.width(86.dp), style = MaterialTheme.typography.labelSmall, color = App.colors.muted, textAlign = TextAlign.End)
            Text("Dug", Modifier.width(96.dp), style = MaterialTheme.typography.labelSmall, color = App.colors.muted, textAlign = TextAlign.End)
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
                val paysNow = pending != null && pendingShare != null && row.entryId in pending.losers
                Column(Modifier.width(86.dp), horizontalAlignment = Alignment.End) {
                    Text("${row.timesLast}", style = MaterialTheme.typography.bodyMedium)
                    if (row.timesLastShared > 0) Text("${row.timesLastShared} deljeno", style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
                    if (paysNow) Text("+1", style = MaterialTheme.typography.labelSmall, color = App.colors.provisional)
                }
                Column(Modifier.width(96.dp), horizontalAlignment = Alignment.End) {
                    Text(
                        Fmt.rsdShort(row.total),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
                        color = debtColor(row.total, max),
                    )
                    if (paysNow) {
                        val share = pendingShare!!
                        Text("+${Fmt.rsdShort(share)} u toku", style = MaterialTheme.typography.labelSmall,
                            color = App.colors.provisional, textAlign = TextAlign.End)
                        Text("→ ${Fmt.rsdShort(row.total + share)}", style = MaterialTheme.typography.labelSmall,
                            color = App.colors.provisional, textAlign = TextAlign.End)
                    }
                }
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
    SectionCard(title = "Dugovanje kroz sezonu", subtitle = "Dug posle svakog kola · tapni za detalje") {
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

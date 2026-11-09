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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fplbp.app.data.GwView
import com.fplbp.app.data.LeagueData
import com.fplbp.app.domain.Chips
import com.fplbp.app.domain.Decider
import com.fplbp.app.domain.Fmt
import com.fplbp.app.ui.components.PlayerRow
import com.fplbp.app.ui.components.EmptyState
import com.fplbp.app.ui.components.Pill
import com.fplbp.app.ui.components.SectionCard
import com.fplbp.app.ui.components.StatusBadge
import com.fplbp.app.ui.components.TeamDot
import com.fplbp.app.ui.components.teamColor
import com.fplbp.app.ui.theme.App

@Composable
fun GameweekDetailScreen(data: LeagueData, gw: Int, onChangeGw: ((Int) -> Unit)? = null) {
    val v = data.gws.firstOrNull { it.gw == gw } ?: return EmptyState("Nema podataka za ovo kolo.")
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onChangeGw != null) item { GwSwitcher(data, v.gw, onChangeGw) }
        item { Header(data, v) }
        val weeks = data.transferWeeks.filter { it.gw == v.gw }
        if (weeks.isNotEmpty()) item { WeekTransfers(data, v, weeks) }
        if (v.lineups.size >= 2) item { OwnershipCard(data, v) }
        if (v.lineups.size >= 2) item { Differentials(data, v) }
        item { Lineups(data, v) }
    }
}

/** The Kolo tab: opens on the current gameweek, arrows step through the others. */
@Composable
fun CurrentGameweekScreen(data: LeagueData) {
    val current = data.liveGw?.gw ?: data.gws.lastOrNull()?.gw ?: return EmptyState("Još nema odigranih kola.")
    var gw by rememberSaveable(current) { mutableIntStateOf(current) }
    GameweekDetailScreen(data, gw, onChangeGw = { gw = it })
}

@Composable
private fun GwSwitcher(data: LeagueData, gw: Int, onChange: (Int) -> Unit) {
    val ids = data.gws.map { it.gw }
    val idx = ids.indexOf(gw)
    val current = data.liveGw?.gw ?: ids.lastOrNull()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(ids[idx - 1]) }, enabled = idx > 0) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prethodno kolo")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Kolo $gw", style = MaterialTheme.typography.titleLarge)
            if (gw != current && current != null) {
                Text("Nazad na tekuće (Kolo $current)", style = MaterialTheme.typography.labelMedium, color = App.colors.gold,
                    modifier = Modifier.clickable { onChange(current) }.padding(2.dp))
            }
        }
        IconButton(onClick = { onChange(ids[idx + 1]) }, enabled = idx in 0 until ids.lastIndex) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Sledeće kolo")
        }
    }
}

@Composable
private fun Header(data: LeagueData, v: GwView) {
    val o = v.outcome
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(v.status)
            Spacer(Modifier.width(8.dp))
            if (v.fixturesTotal > 0) Text("Utakmice ${v.fixturesDone}/${v.fixturesTotal}", style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
        }
        if (o != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                o.losers.joinToString(" i ") { data.teamName(it) } + " · " + Fmt.rsdShort(o.share) + if (o.losers.size > 1) " svako" else "",
                style = MaterialTheme.typography.titleMedium, color = App.colors.debt,
            )
        }
        Spacer(Modifier.height(8.dp))
        v.scores.sortedByDescending { it.net }.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamDot(teamColor(data, s.entryId))
                Spacer(Modifier.width(8.dp))
                Text(data.teamName(s.entryId), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (o != null && s.entryId in o.losers) App.colors.debt else MaterialTheme.colorScheme.onSurface)
                s.chip?.let { Pill(Chips.short(it), App.colors.gold); Spacer(Modifier.width(6.dp)) }
                Text(if (s.transferCost > 0) "${s.points} −${s.transferCost} = ${s.net}" else "${s.net}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!v.isFinal) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Bodovi uključuju predviđene izmene sa klupe, prelazak kapitenske trake i bonus po BPS-u. Dug se računa tek kad FPL potvrdi kolo.",
                style = MaterialTheme.typography.labelSmall, color = App.colors.muted,
            )
        }
    }
}

@Composable
private fun WeekTransfers(data: LeagueData, v: GwView, weeks: List<com.fplbp.app.domain.TransferAnalysis.Week>) {
    SectionCard(title = "Transferi ovog kola", subtitle = "↑ novi naspram ↓ prodatog, bodovi ovog kola") {
        val order = v.scores.sortedByDescending { it.net }.map { it.entryId }
        val sorted = weeks.sortedBy { order.indexOf(it.entryId) }
        sorted.forEachIndexed { i, w ->
            Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 0.dp else 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamDot(teamColor(data, w.entryId))
                Spacer(Modifier.width(8.dp))
                Text(data.teamName(w.entryId), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (w.results.any { it.wildcard }) { Pill("Wildcard", App.colors.gold); Spacer(Modifier.width(8.dp)) }
                Text(signed(w.net), style = MaterialTheme.typography.titleSmall, color = signColor(w.net))
            }
            w.freeHit?.let { fh ->
                Text("Free Hit: ${fh.points} umesto ${fh.wouldHave} sa prošlonedeljnim timom (${signed(fh.diff)})",
                    style = MaterialTheme.typography.bodySmall)
            }
            w.results.sortedByDescending { it.weekDiff }.forEachIndexed { j, r ->
                if (j > 0) Spacer(Modifier.height(6.dp))
                TransferCard(data, r, held = false, showGw = false)
            }
            if (w.hit > 0) Text("Hit −${w.hit}", style = MaterialTheme.typography.labelMedium, color = App.colors.debt)
            if (i < sorted.lastIndex) HorizontalDivider(Modifier.padding(top = 6.dp), color = App.colors.grid)
        }
    }
}

@Composable
private fun Differentials(data: LeagueData, v: GwView) {
    val diffs = remember(v) { Decider.leagueDifferentials(v.lineups) }
    SectionCard(title = "Diferencijali", subtitle = "Niko drugi ih nije imao · tapni za bodove") {
        val order = v.scores.sortedByDescending { it.net }
        order.forEachIndexed { i, s ->
            val list = diffs[s.entryId].orEmpty()
            Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 0.dp else 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamDot(teamColor(data, s.entryId))
                Spacer(Modifier.width(8.dp))
                Text(data.teamName(s.entryId), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (list.isEmpty()) "ništa posebno" else "ukupno ${list.sumOf { it.total }}",
                    style = MaterialTheme.typography.labelMedium, color = App.colors.muted,
                )
            }
            list.forEach { PlayerRow(data, v, it) }
            if (i < order.lastIndex) HorizontalDivider(Modifier.padding(top = 6.dp), color = App.colors.grid)
        }
    }
}

@Composable
private fun Lineups(data: LeagueData, v: GwView) {
    SectionCard(title = "Postave", subtitle = "Tapni tim za postavu, pa igrača za bodove") {
        v.scores.sortedByDescending { it.net }.forEachIndexed { i, s ->
            var open by remember(v.gw, s.entryId) { mutableStateOf(false) }
            val lu = v.lineups[s.entryId]
            Row(
                Modifier.fillMaxWidth().clickable(enabled = lu != null) { open = !open }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeamDot(teamColor(data, s.entryId))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(data.teamName(s.entryId), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("klupa ${s.benchPoints}" + (s.chip?.let { " · ${Chips.label(it)}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
                }
                Text("${s.net}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (lu != null) Icon(
                    if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (open) "Sakrij" else "Prikaži",
                    tint = App.colors.muted,
                )
            }
            if (lu != null) AnimatedVisibility(open) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    if (v.isFinal && v.verified[s.entryId] == false) {
                        Text("Raspodela po igračima se razlikuje od FPL zbira; tabela koristi zvanične bodove.",
                            style = MaterialTheme.typography.labelSmall, color = App.colors.provisional)
                    }
                    val starters = if (s.chip == "bboost") lu.picks
                    else lu.picks.filter { (it.position <= 11 && !it.subbedOut) || it.subbedIn }
                    val bench = lu.picks - starters.toSet()
                    // Ordered GOL → ODB → VEZ → NAP; the club chip already shows the position.
                    starters.sortedWith(compareBy({ data.players[it.element]?.elementType ?: 0 }, { it.position }))
                        .forEach { PlayerRow(data, v, it) }
                    if (bench.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = App.colors.grid)
                        Text("Klupa", style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
                        bench.sortedBy { it.position }.forEach { PlayerRow(data, v, it, dim = true) }
                    }
                }
            }
            if (i < v.scores.lastIndex) HorizontalDivider(color = App.colors.grid)
        }
    }
}

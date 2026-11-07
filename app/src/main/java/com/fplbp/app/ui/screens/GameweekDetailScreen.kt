package com.fplbp.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fplbp.app.data.GwView
import com.fplbp.app.data.LeagueData
import com.fplbp.app.domain.Chips
import com.fplbp.app.domain.Decider
import com.fplbp.app.domain.EffectivePick
import com.fplbp.app.domain.Fmt
import com.fplbp.app.ui.components.ClubChip
import com.fplbp.app.ui.components.EmptyState
import com.fplbp.app.ui.components.Pill
import com.fplbp.app.ui.components.SectionCard
import com.fplbp.app.ui.components.StatusBadge
import com.fplbp.app.ui.components.TeamDot
import com.fplbp.app.ui.components.teamColor
import com.fplbp.app.ui.theme.App

@Composable
fun GameweekDetailScreen(data: LeagueData, gw: Int) {
    val v = data.gws.firstOrNull { it.gw == gw } ?: return EmptyState("Nema podataka za ovo kolo.")
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(data, v) }
        if (v.lineups.size >= 2 && v.scores.size >= 2) item { Decided(data, v) }
        if (v.lineups.size >= 2) item { Differentials(data, v) }
        item { Lineups(data, v) }
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

/** Default pair: the (first) loser vs the first team above the bottom. */
private fun defaultPair(v: GwView): Pair<Long, Long> {
    val sorted = v.scores.filter { it.entryId in v.lineups }.sortedBy { it.net }
    val a = sorted.first()
    val b = sorted.firstOrNull { it.net > a.net } ?: sorted[1]
    return a.entryId to b.entryId
}

@Composable
private fun Decided(data: LeagueData, v: GwView) {
    val (defA, defB) = remember(v.gw) { defaultPair(v) }
    var teamA by rememberSaveable(v.gw) { mutableLongStateOf(defA) }
    var teamB by rememberSaveable(v.gw) { mutableLongStateOf(defB) }
    val sa = v.scores.firstOrNull { it.entryId == teamA }
    val sb = v.scores.firstOrNull { it.entryId == teamB }
    val la = v.lineups[teamA]
    val lb = v.lineups[teamB]

    SectionCard(title = "Presudilo", subtitle = "Samo ono po čemu su se dva tima razlikovala") {
        TeamPicker("Tim", data, v, teamA) { if (it != teamB) teamA = it else { teamB = teamA; teamA = it } }
        Spacer(Modifier.height(4.dp))
        TeamPicker("Protiv", data, v, teamB) { if (it != teamA) teamB = it else { teamA = teamB; teamB = it } }
        if (sa == null || sb == null || la == null || lb == null) return@SectionCard

        val cmp = remember(v, teamA, teamB) { Decider.compare(la, sa.transferCost, sa.net, lb, sb.transferCost, sb.net) }
        val nameA = data.teamName(teamA)
        val nameB = data.teamName(teamB)
        Spacer(Modifier.height(12.dp))
        Text(
            "$nameA ${sa.net} · $nameB ${sb.net} · razlika ${Decider.signed(cmp.gapNet)}",
            style = MaterialTheme.typography.titleSmall,
        )
        val verdict = Decider.verdict(cmp, nameA, nameB, sa.points, sa.transferCost, sb.net) { data.playerName(it) }
            ?: Decider.verdict(
                Decider.compare(lb, sb.transferCost, sb.net, la, sa.transferCost, sa.net),
                nameB, nameA, sb.points, sb.transferCost, sa.net,
            ) { data.playerName(it) }
        verdict?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = App.colors.gold)
        }
        if (cmp.lines.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Isti bodovi iz istih igrača. Nema razlike.", style = MaterialTheme.typography.bodySmall, color = App.colors.muted)
        }
        DiffSection("Za tim $nameA", cmp.forA, data, teamColor(data, teamA), positive = true, nameA, nameB)
        DiffSection("Za tim $nameB", cmp.forB, data, teamColor(data, teamB), positive = false, nameA, nameB)
        val pendingOnly = cmp.lines.filter { it.diff == 0 && it.pending }
        DiffSection("Još igraju", pendingOnly, data, App.colors.provisional, positive = true, nameA, nameB)
        if (!cmp.exact) {
            Spacer(Modifier.height(8.dp))
            Text("Zbir stavki se ne poklapa u potpunosti sa zvaničnim bodovima (FPL ponekad naknadno ispravi bodove).",
                style = MaterialTheme.typography.labelSmall, color = App.colors.provisional)
        }
    }
}

@Composable
private fun TeamPicker(label: String, data: LeagueData, v: GwView, selected: Long, onPick: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(52.dp), style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            v.scores.sortedBy { it.net }.filter { it.entryId in v.lineups }.forEach { s ->
                FilterChip(
                    selected = s.entryId == selected,
                    onClick = { onPick(s.entryId) },
                    label = { Text(data.teamName(s.entryId), maxLines = 1) },
                    leadingIcon = { TeamDot(teamColor(data, s.entryId)) },
                )
            }
        }
    }
}

@Composable
private fun DiffSection(
    title: String, lines: List<Decider.Line>, data: LeagueData, color: Color, positive: Boolean,
    nameA: String, nameB: String,
) {
    if (lines.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamDot(color)
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = App.colors.grid)
    lines.forEach { l ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    l.element?.let { data.playerName(it) } ?: "Minusi za transfere",
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    describe(l, nameA, nameB),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (l.pending) App.colors.provisional else App.colors.muted,
                )
            }
            if (l.element != null) {
                ClubChip(data.clubShort(l.element), data.players[l.element]?.elementType)
                Spacer(Modifier.width(8.dp))
            }
            val shown = if (positive) l.diff else -l.diff
            Text(
                Decider.signed(shown), Modifier.width(40.dp), textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun describe(l: Decider.Line, nameA: String, nameB: String): String {
    val base = when (l.kind) {
        Decider.Kind.ONLY_A -> "samo $nameA · ${l.pointsA} b." + mult(l.multA)
        Decider.Kind.ONLY_B -> "samo $nameB · ${l.pointsB} b." + mult(l.multB)
        Decider.Kind.MULTIPLIER -> "oba imaju · ${label(l.multA)} naspram ${label(l.multB)}"
        Decider.Kind.HITS -> "$nameA ${l.pointsA} · $nameB ${l.pointsB}"
    }
    return base + (if (l.subbedIn) " · ušao sa klupe" else "") + (if (l.pending) " · još igra" else "")
}

private fun mult(m: Int) = when (m) { 2 -> " (kapiten)"; 3 -> " (TC)"; else -> "" }
private fun label(m: Int) = when (m) { 3 -> "TC"; 2 -> "kapiten"; else -> "običan" }

@Composable
private fun Differentials(data: LeagueData, v: GwView) {
    val diffs = remember(v) { Decider.leagueDifferentials(v.lineups) }
    SectionCard(title = "Samo kod njega", subtitle = "Igrači koje u ovom kolu niko drugi u ligi nije imao") {
        v.scores.sortedByDescending { it.net }.forEachIndexed { i, s ->
            val list = diffs[s.entryId].orEmpty()
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Row(Modifier.width(110.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamDot(teamColor(data, s.entryId))
                    Spacer(Modifier.width(6.dp))
                    Text(data.teamName(s.entryId), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    if (list.isEmpty()) "ništa posebno"
                    else list.take(4).joinToString(" · ") { "${data.playerName(it.element)} ${it.total}" + if (it.pending) "*" else "" },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (list.isEmpty()) App.colors.muted else MaterialTheme.colorScheme.onSurface,
                )
                Text("${list.sumOf { it.total }}", Modifier.width(36.dp), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            if (i < v.scores.lastIndex) HorizontalDivider(color = App.colors.grid)
        }
        if (!v.isFinal) Text("* još igra", style = MaterialTheme.typography.labelSmall, color = App.colors.provisional)
    }
}

@Composable
private fun Lineups(data: LeagueData, v: GwView) {
    SectionCard(title = "Postave", subtitle = "Tapni tim za celu postavu") {
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
                Column(Modifier.padding(start = 18.dp, bottom = 8.dp)) {
                    if (v.verified[s.entryId] == false) {
                        Text("Raspodela po igračima se razlikuje od FPL zbira; tabela koristi zvanične bodove.",
                            style = MaterialTheme.typography.labelSmall, color = App.colors.provisional)
                    }
                    val starters = if (s.chip == "bboost") lu.picks
                    else lu.picks.filter { (it.position <= 11 && !it.subbedOut) || it.subbedIn }
                    val bench = lu.picks - starters.toSet()
                    starters.sortedBy { data.players[it.element]?.elementType ?: 0 }.forEach { PickRow(data, it) }
                    if (bench.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = App.colors.grid)
                        Text("Klupa", style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
                        bench.forEach { PickRow(data, it, dim = true) }
                    }
                }
            }
            if (i < v.scores.lastIndex) HorizontalDivider(color = App.colors.grid)
        }
    }
}

@Composable
private fun PickRow(data: LeagueData, p: EffectivePick, dim: Boolean = false) {
    val player = data.players[p.element]
    val color = if (dim) App.colors.muted else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(data.playerName(p.element), style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    p.captaincyPassed -> Badge("C←V")
                    p.isCaptain && p.multiplier == 3 -> Badge("TC")
                    p.isCaptain -> Badge("C")
                    p.isVice -> Badge("V")
                }
                if (p.subbedIn) Text("  ↑ ušao", style = MaterialTheme.typography.labelSmall, color = App.colors.safe)
                if (p.subbedOut) Text("  ↓ izašao", style = MaterialTheme.typography.labelSmall, color = App.colors.debt)
                if (p.pending) Text("  još igra", style = MaterialTheme.typography.labelSmall, color = App.colors.provisional)
            }
            Spacer(Modifier.height(2.dp))
            ClubChip(data.clubShort(p.element), player?.elementType)
        }
        Text(
            if (p.multiplier > 1) "${p.points}×${p.multiplier}" else "",
            style = MaterialTheme.typography.labelSmall, color = App.colors.muted,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            "${if (p.multiplier == 0) p.points else p.total}",
            Modifier.width(32.dp), textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color,
        )
    }
}

@Composable
private fun Badge(text: String) {
    Spacer(Modifier.width(6.dp))
    Pill(text, App.colors.gold, filled = true)
}

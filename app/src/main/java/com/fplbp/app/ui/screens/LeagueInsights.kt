package com.fplbp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fplbp.app.domain.Decider
import com.fplbp.app.ui.components.StatCounts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fplbp.app.data.Category
import com.fplbp.app.data.GwView
import com.fplbp.app.data.PlayerContribution
import com.fplbp.app.data.LeagueData
import com.fplbp.app.domain.Fmt
import com.fplbp.app.domain.Insights
import com.fplbp.app.domain.Pos
import com.fplbp.app.ui.components.ClubChip
import com.fplbp.app.ui.components.ClubTag
import com.fplbp.app.ui.components.SectionCard
import com.fplbp.app.ui.components.TeamDot
import com.fplbp.app.ui.components.teamColor
import com.fplbp.app.ui.theme.App
import kotlin.math.roundToInt

/** Confirmed gameweeks with lineups, for season-level insights. */
fun finalInsightGws(data: LeagueData) = data.gws.filter { it.isFinal && it.lineups.isNotEmpty() }.map { Insights.Gw(it.gw, it.lineups) }

private fun pct(part: Int, total: Int) = if (total <= 0) 0 else (part * 100.0 / total).roundToInt()

private val posColors = mapOf(
    Pos.GK to Color(0xFFC98500), Pos.DEF to Color(0xFF199E70), Pos.MID to Color(0xFF3987E5), Pos.FWD to Color(0xFFD55181),
)

// ------------------------------------------------------------------ 1. captains

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptainsCard(data: LeagueData) {
    val popular = Insights.popularCaptains(finalInsightGws(data)).take(8)
    if (popular.isEmpty()) return
    val teams = data.teams.map { it.entryId }.sorted()
    SectionCard(title = "Najčešći kapiteni u sezoni", subtitle = "×broj = koliko puta izabran · prosek sa trakom") {
        popular.forEachIndexed { i, p ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(data.playerName(p.element), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    ClubChip(data.clubShort(p.element), data.players[p.element]?.elementType)
                    Spacer(Modifier.weight(1f))
                    Text("×${p.times} · prosek ${Fmt.decimal(p.avgWithArmband, 1)} bodova", style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
                }
                Spacer(Modifier.height(4.dp))
                // Full team names, so no legend is needed.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    teams.filter { (p.byTeam[it] ?: 0) > 0 }.sortedByDescending { p.byTeam[it] }.forEach { id ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamDot(teamColor(data, id))
                            Spacer(Modifier.width(5.dp))
                            Text("${data.teamName(id)} ×${p.byTeam[id]}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (i < popular.lastIndex) HorizontalDivider(color = App.colors.grid)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamLegend(data: LeagueData, teams: List<Long>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        teams.forEach { id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamDot(teamColor(data, id))
                Spacer(Modifier.width(6.dp))
                Text(data.teamName(id), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ------------------------------------------------------------------ 2. ownership grid

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OwnershipCard(data: LeagueData, v: GwView) {
    if (v.lineups.size < 2) return
    val rows = Insights.ownership(Insights.Gw(v.gw, v.lineups), limit = Int.MAX_VALUE)
    val teams = data.teams.map { it.entryId }.sorted()
    SectionCard(
        title = "Zajednički igrači",
        subtitle = "Broj = bodovi za taj tim · zlatno kapiten · ○ klupa",
    ) {
        if (rows.isEmpty()) Text("Nijedan igrač nije zajednički.", style = MaterialTheme.typography.bodySmall, color = App.colors.muted)
        val gold = App.colors.gold
        rows.forEach { o ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
                    // Rows where the same player gave teams different points are the interesting ones.
                    .background(if (o.decisive) gold.copy(alpha = 0.10f) else Color.Transparent)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(data.playerName(o.element), style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(5.dp))
                    ClubTag(data.clubShort(o.element), data.players[o.element]?.elementType)
                }
                teams.forEach { id ->
                    Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                        PointsMark(o.owners[id], o.contrib[id] ?: 0, teamColor(data, id))
                    }
                }
                Num("${o.points}", 30, App.colors.muted)
            }
        }
        if (rows.any { it.decisive }) {
            Text("Zlatna pozadina = isti igrač, različiti bodovi", style = MaterialTheme.typography.labelSmall,
                color = App.colors.muted, modifier = Modifier.padding(top = 6.dp))
        }
        HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 8.dp), color = App.colors.grid)
        TeamLegend(data, teams)
    }
}

@Composable
private fun PointsMark(mark: Insights.Mark?, pts: Int, color: Color) {
    val shape = RoundedCornerShape(50)
    when (mark) {
        null -> Text("–", style = MaterialTheme.typography.labelSmall, color = App.colors.grid)
        Insights.Mark.BENCH -> Box(Modifier.size(26.dp).clip(shape).border(1.5.dp, color, shape), contentAlignment = Alignment.Center) {
            Text("–", style = MaterialTheme.typography.labelSmall, color = color)
        }
        else -> Box(
            Modifier.size(26.dp).clip(shape).background(if (mark == Insights.Mark.CAPTAIN) App.colors.gold else color),
            contentAlignment = Alignment.Center,
        ) {
            Text("$pts", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.background, maxLines = 1)
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(Modifier.size(20.dp).clip(RoundedCornerShape(50)).background(color), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
    }
}

// ------------------------------------------------------------------ 3. own players

@Composable
fun OwnPlayersCard(data: LeagueData) {
    val shares = Insights.ownShare(finalInsightGws(data))
    if (shares.isEmpty()) return
    SectionCard(title = "Bodovi od diferencijala", subtitle = "Igrači koje niko drugi nije imao · tapni tim") {
        shares.values.sortedByDescending { it.share }.forEachIndexed { i, s ->
            var open by remember(s.entryId) { mutableStateOf(false) }
            Column(
                Modifier.fillMaxWidth().clickable(enabled = s.players.isNotEmpty()) { open = !open }.padding(vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamName(data, s.entryId, Modifier.weight(1f))
                    Text("${pct(s.own, s.total)}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("  ${s.own} od ${s.total}", style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
                    if (s.players.isNotEmpty()) Arrow(open)
                }
                Spacer(Modifier.height(5.dp))
                SplitBar(listOf(s.own to teamColor(data, s.entryId), (s.total - s.own).coerceAtLeast(0) to App.colors.grid))
            }
            AnimatedVisibility(open) { OwnPlayersList(data, s.entryId) }
            if (i < shares.size - 1) HorizontalDivider(color = App.colors.grid)
        }
    }
}

/** A team's season differentials, shown exactly like the Top 10 list (icons, bar, tap for points split). */
@Composable
private fun OwnPlayersList(data: LeagueData, entryId: Long) {
    val rows = remember(data, entryId) {
        val acc = HashMap<Int, PlayerContribution>()
        data.gws.filter { it.isFinal && it.lineups.isNotEmpty() }.forEach { v ->
            Decider.leagueDifferentials(v.lineups)[entryId].orEmpty().forEach { p ->
                val cats = HashMap<String, Int>()
                val counts = HashMap<String, Int>()
                var bonus = 0
                v.stats[p.element].orEmpty().forEach { st ->
                    val c = Category.of(st.identifier)
                    cats[c] = (cats[c] ?: 0) + st.points * p.multiplier
                    counts[st.identifier] = (counts[st.identifier] ?: 0) + st.value
                    if (st.identifier == "bonus") bonus += st.points
                }
                val prev = acc[p.element]
                acc[p.element] = PlayerContribution(
                    element = p.element,
                    total = (prev?.total ?: 0) + p.total,
                    byCategory = (prev?.byCategory.orEmpty().keys + cats.keys).associateWith { (prev?.byCategory?.get(it) ?: 0) + (cats[it] ?: 0) },
                    captainExtra = (prev?.captainExtra ?: 0) + p.points * (p.multiplier - 1).coerceAtLeast(0),
                    gwsCounted = (prev?.gwsCounted ?: 0) + 1,
                    counts = (prev?.counts.orEmpty().keys + counts.keys).associateWith { (prev?.counts?.get(it) ?: 0) + (counts[it] ?: 0) },
                    bonusRaw = (prev?.bonusRaw ?: 0) + bonus,
                )
            }
        }
        acc.values.sortedByDescending { it.total }
    }
    val maxTotal = rows.firstOrNull()?.total?.coerceAtLeast(1) ?: 1
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        rows.forEachIndexed { i, c -> TopPlayer(data, i, c, maxTotal, teamColor(data, entryId)) }
    }
}

/**
 * A team's points by club: top 6, then the rest in one row. Bars span exactly the
 * 4 share cells above (4 × 50dp) so the drill-down never sticks out of the table.
 */
@Composable
private fun AllClubs(data: LeagueData, entryId: Long, byClub: Map<Int, Int>) {
    val total = byClub.values.sum().coerceAtLeast(1)
    val sorted = byClub.entries.filter { it.value != 0 }.sortedByDescending { it.value }
    val shown = sorted.take(6)
    val rest = sorted.drop(6)
    val max = shown.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    val color = teamColor(data, entryId)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        shown.forEach { (club, pts) ->
            ClubLine(data.clubs[club]?.shortName ?: "?", "${pct(pts, total)}% · $pts") {
                Box(Modifier.fillMaxWidth((pts.toFloat() / max).coerceIn(0.02f, 1f)).height(6.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).background(color))
            }
        }
        if (rest.isNotEmpty()) {
            val restPts = rest.sumOf { it.value }
            ClubLine("+ ${rest.size} ${if (rest.size == 1) "klub" else if (rest.size in 2..4) "kluba" else "klubova"}",
                "${pct(restPts, total)}% · $restPts", muted = true) {}
        }
    }
}

@Composable
private fun ClubLine(label: String, value: String, muted: Boolean = false, bar: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.padding(start = 18.dp).weight(1f), style = MaterialTheme.typography.labelMedium,
            fontWeight = if (muted) FontWeight.Normal else FontWeight.SemiBold, color = App.colors.muted, maxLines = 1)
        Text(value, Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelMedium,
            color = if (muted) App.colors.muted else MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Box(Modifier.width(200.dp)) { bar() }
        Spacer(Modifier.width(24.dp))
    }
}

@Composable
private fun Arrow(open: Boolean) = Icon(
    if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
    contentDescription = if (open) "Sakrij" else "Prikaži", tint = App.colors.muted,
)

// ------------------------------------------------------------------ 4./5. shaded tables

/**
 * Teams × columns table of shares (%). Cells are shaded gold by value; the highest
 * value in each column is bold. [rowLabel] = full team name, or only its color dot (+ legend).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareTable(
    data: LeagueData,
    columns: List<String>,
    values: Map<Long, List<Int>>, // team -> points per column
    fullNames: Boolean,
    /** When set, each team row gets a ⌄ arrow and opens this content below it. */
    details: (@Composable (Long) -> Unit)? = null,
) {
    val teams = data.teams.map { it.entryId }.sorted()
    val pctRows = teams.associateWith { id ->
        val v = values[id].orEmpty()
        val total = v.sum()
        columns.indices.map { pct(v.getOrElse(it) { 0 }, total) }
    }
    val colMax = columns.indices.map { c -> teams.maxOfOrNull { pctRows.getValue(it)[c] } ?: 0 }
    val maxAll = colMax.maxOrNull()?.coerceAtLeast(1) ?: 1
    val gold = App.colors.gold
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(if (fullNames) Modifier.weight(1f) else Modifier.width(22.dp))
        columns.forEach { c ->
            Text(c, if (fullNames) Modifier.width(50.dp) else Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = App.colors.muted, maxLines = 1)
        }
        if (details != null) Spacer(Modifier.width(24.dp))
    }
    teams.forEach { id ->
        var open by remember(id) { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().clickable(enabled = details != null) { open = !open }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (fullNames) TeamName(data, id, Modifier.weight(1f))
            else Box(Modifier.width(22.dp)) { TeamDot(teamColor(data, id)) }
            pctRows.getValue(id).forEachIndexed { c, p ->
                val best = p == colMax[c] && p > 0
                Box(
                    (if (fullNames) Modifier.width(50.dp) else Modifier.weight(1f)).height(30.dp).padding(1.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(gold.copy(alpha = 0.06f + 0.5f * p / maxAll)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$p%", style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (best) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (details != null) Arrow(open)
        }
        if (details != null) AnimatedVisibility(open) { Box(Modifier.padding(top = 4.dp, bottom = 10.dp)) { details(id) } }
    }
    if (!fullNames) {
        HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 8.dp), color = App.colors.grid)
        TeamLegend(data, teams)
    }
}

@Composable
fun PositionsCard(data: LeagueData) {
    val byPos = Insights.pointsBy(finalInsightGws(data)) { data.players[it]?.elementType }
    if (byPos.isEmpty()) return
    val order = listOf(Pos.GK, Pos.DEF, Pos.MID, Pos.FWD)
    SectionCard(title = "Bodovi po poziciji", subtitle = "Najveći udeo u koloni je podebljan") {
        ShareTable(
            data = data,
            columns = order.map { Pos.short(it) },
            values = byPos.mapValues { (_, m) -> order.map { m[it] ?: 0 } },
            fullNames = true,
        )
    }
}

@Composable
fun ClubsCard(data: LeagueData) {
    val byClub = Insights.pointsBy(finalInsightGws(data)) { data.players[it]?.clubId }
    if (byClub.isEmpty()) return
    // Same columns for every team: the league's top 3 clubs by points, then the rest.
    val top = byClub.values.flatMap { it.entries }.groupBy({ it.key }, { it.value })
        .mapValues { it.value.sum() }.entries.sortedByDescending { it.value }.take(3).map { it.key }
    SectionCard(title = "Bodovi po klubu", subtitle = "Najveći udeo podebljan · tapni tim za sve klubove") {
        ShareTable(
            data = data,
            columns = top.map { data.clubs[it]?.shortName ?: "?" } + "Ost.",
            values = byClub.mapValues { (_, m) -> top.map { m[it] ?: 0 } + (m.values.sum() - top.sumOf { m[it] ?: 0 }) },
            fullNames = true,
            details = { id -> AllClubs(data, id, byClub[id].orEmpty()) },
        )
    }
}

// ------------------------------------------------------------------ helpers

@Composable
private fun SplitBar(parts: List<Pair<Int, Color>>) {
    val visible = parts.filter { it.first > 0 }
    if (visible.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        visible.forEach { (v, c) -> Box(Modifier.weight(v.toFloat()).height(10.dp).background(c)) }
    }
}

@Composable
private fun TeamName(data: LeagueData, id: Long, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TeamDot(teamColor(data, id))
        Spacer(Modifier.width(8.dp))
        Text(data.teamName(id), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Head(text: String, width: Int) =
    Text(text, Modifier.width(width.dp), style = MaterialTheme.typography.labelSmall, color = App.colors.muted, textAlign = TextAlign.End)

@Composable
private fun Num(text: String, width: Int, color: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false) =
    Text(text, Modifier.width(width.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium,
        color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

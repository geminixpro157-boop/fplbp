package com.fplbp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.fplbp.app.data.GwView
import com.fplbp.app.data.LeagueData
import com.fplbp.app.domain.Fmt
import com.fplbp.app.domain.Insights
import com.fplbp.app.domain.Pos
import com.fplbp.app.ui.components.ClubChip
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
    SectionCard(title = "Najčešći kapiteni u sezoni", subtitle = "Završena kola · broj uz boju = koliko puta ga je tim kapitenirao · prosek bez množenja") {
        popular.forEachIndexed { i, p ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(data.playerName(p.element), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    ClubChip(data.clubShort(p.element), data.players[p.element]?.elementType)
                    Spacer(Modifier.weight(1f))
                    Text("${p.times}× · prosek ${Fmt.decimal(p.avgRaw, 1)}", style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    teams.filter { (p.byTeam[it] ?: 0) > 0 }.sortedByDescending { p.byTeam[it] }.forEach { id ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamDot(teamColor(data, id))
                            Spacer(Modifier.width(4.dp))
                            Text("${p.byTeam[id]}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (i < popular.lastIndex) HorizontalDivider(color = App.colors.grid)
        }
        HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 8.dp), color = App.colors.grid)
        TeamLegend(data, teams)
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
        subtitle = "Igrači koje ima bar dva tima · C kapiten, V vice, ● igra, ○ klupa · broj desno = bodovi u ovom kolu",
    ) {
        if (rows.isEmpty()) Text("Nijedan igrač nije zajednički.", style = MaterialTheme.typography.bodySmall, color = App.colors.muted)
        rows.forEach { o ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(data.playerName(o.element), style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                teams.forEach { id -> Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) { Mark(o.owners[id], teamColor(data, id)) } }
                Num("${o.points}", 40)
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 8.dp), color = App.colors.grid)
        TeamLegend(data, teams)
    }
}

@Composable
private fun Mark(mark: Insights.Mark?, color: Color) {
    when (mark) {
        null -> Text("–", style = MaterialTheme.typography.labelSmall, color = App.colors.grid)
        Insights.Mark.CAPTAIN -> Badge("C", App.colors.gold)
        Insights.Mark.VICE -> Badge("V", color)
        Insights.Mark.PLAYING -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(color))
        Insights.Mark.BENCH -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).border(1.5.dp, color, RoundedCornerShape(50)))
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
    SectionCard(title = "Bodovi od diferencijala", subtitle = "Deo bodova od igrača koje u tom kolu niko drugi u ligi nije imao") {
        shares.values.sortedByDescending { it.share }.forEachIndexed { i, s ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamName(data, s.entryId, Modifier.weight(1f))
                    Text("${pct(s.own, s.total)}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("  ${s.own} od ${s.total}", style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
                }
                Spacer(Modifier.height(5.dp))
                SplitBar(listOf(s.own to teamColor(data, s.entryId), (s.total - s.own).coerceAtLeast(0) to App.colors.grid))
                if (s.players.isNotEmpty()) {
                    Text(
                        s.players.take(3).joinToString(" · ") { (el, pts) -> "${data.playerName(el)} $pts" },
                        style = MaterialTheme.typography.labelSmall, color = App.colors.muted, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (i < shares.size - 1) HorizontalDivider(color = App.colors.grid)
        }
    }
}

// ------------------------------------------------------------------ 4. positions

@Composable
fun PositionsCard(data: LeagueData) {
    val byPos = Insights.pointsBy(finalInsightGws(data)) { data.players[it]?.elementType }
    if (byPos.isEmpty()) return
    val order = listOf(Pos.GK, Pos.DEF, Pos.MID, Pos.FWD)
    SectionCard(title = "Bodovi po poziciji", subtitle = "Završena kola, sa kapitenskim množenjem") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            order.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(posColors.getValue(p)))
                    Spacer(Modifier.width(4.dp))
                    Text(Pos.short(p), style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
                }
            }
        }
        data.teams.sortedBy { it.entryId }.forEach { t ->
            val m = byPos[t.entryId].orEmpty()
            val total = m.values.sum()
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                TeamName(data, t.entryId, Modifier)
                Spacer(Modifier.height(4.dp))
                SplitBar(order.map { (m[it] ?: 0) to posColors.getValue(it) })
                Text(
                    order.joinToString(" · ") { "${Pos.short(it)} ${pct(m[it] ?: 0, total)}%" },
                    style = MaterialTheme.typography.labelSmall, color = App.colors.muted, modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 5. clubs

@Composable
fun ClubsCard(data: LeagueData) {
    val byClub = Insights.pointsBy(finalInsightGws(data)) { data.players[it]?.clubId }
    if (byClub.isEmpty()) return
    SectionCard(title = "Bodovi po klubu", subtitle = "Pet klubova od kojih je tim dobio najviše bodova, ostalo zajedno") {
        data.teams.sortedBy { it.entryId }.forEachIndexed { i, t ->
            val m = byClub[t.entryId].orEmpty()
            val total = m.values.sum()
            val sorted = m.entries.sortedByDescending { it.value }
            val rest = sorted.drop(5).sumOf { it.value }
            val top: List<Pair<String, Int>> = sorted.take(5).map { (data.clubs[it.key]?.shortName ?: "?") to it.value } +
                listOfNotNull(if (rest > 0) "Ostalo" to rest else null)
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                TeamName(data, t.entryId, Modifier)
                top.forEach { (club, pts) ->
                    Row(Modifier.padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(club, Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold, color = App.colors.muted)
                        Box(Modifier.weight(1f)) {
                            Box(Modifier.fillMaxWidth((pts.toFloat() / total.coerceAtLeast(1)).coerceIn(0.02f, 1f)).height(8.dp)
                                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                .background(if (club == "Ostalo") App.colors.muted.copy(alpha = 0.5f) else teamColor(data, t.entryId)))
                        }
                        Text("${pct(pts, total)}%", Modifier.width(48.dp), textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (i < data.teams.lastIndex) HorizontalDivider(color = App.colors.grid)
        }
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

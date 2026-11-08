package com.fplbp.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fplbp.app.R
import com.fplbp.app.data.GwView
import com.fplbp.app.data.LeagueData
import com.fplbp.app.data.PlayState
import com.fplbp.app.data.db.PlayerGwStatEntity
import com.fplbp.app.domain.EffectivePick
import com.fplbp.app.ui.theme.App
import java.time.Instant
import java.time.ZoneId

/** "pon 21:00" in the phone's time zone. */
fun kickoffShort(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    val day = when (t.dayOfWeek.value) { 1 -> "pon"; 2 -> "uto"; 3 -> "sre"; 4 -> "čet"; 5 -> "pet"; 6 -> "sub"; else -> "ned" }
    return "%s %02d:%02d".format(day, t.hour, t.minute)
}

private fun statLabel(id: String, value: Int): String = when (id) {
    "minutes" -> "Minutaža ($value')"
    "goals_scored" -> "Golovi ($value)"
    "assists" -> "Asistencije ($value)"
    "clean_sheets" -> "Čista mreža"
    "goals_conceded" -> "Primljeni golovi ($value)"
    "own_goals" -> "Autogolovi ($value)"
    "penalties_saved" -> "Odbranjeni penali ($value)"
    "penalties_missed" -> "Promašeni penali ($value)"
    "yellow_cards" -> "Žuti karton"
    "red_cards" -> "Crveni karton"
    "saves" -> "Odbrane ($value)"
    "bonus" -> "Bonus"
    "defensive_contribution" -> "Def. doprinos ($value)"
    else -> id.replace('_', ' ')
}

/**
 * One player in a gameweek: name, club, events as icons, minutes / kickoff / live,
 * and points. Tap to see the exact points breakdown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerRow(data: LeagueData, gv: GwView, p: EffectivePick, dim: Boolean = false) {
    var open by remember(gv.gw, p.element) { mutableStateOf(false) }
    val player = data.players[p.element]
    val stats = gv.stats[p.element].orEmpty()
    val predictedBonus = gv.provisionalBonus[p.element] ?: 0
    val club = gv.clubState(player?.clubId)
    val textColor = if (dim) App.colors.muted else MaterialTheme.colorScheme.onSurface
    fun v(id: String) = stats.firstOrNull { it.identifier == id }?.value ?: 0
    val bonusPts = (stats.firstOrNull { it.identifier == "bonus" }?.points ?: 0).let { if (it > 0) it else predictedBonus }

    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(data.playerName(p.element), style = MaterialTheme.typography.bodyMedium, color = textColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    when {
                        p.captaincyPassed -> Badge("C←V")
                        p.isCaptain && p.multiplier == 3 -> Badge("TC")
                        p.isCaptain -> Badge("C")
                        p.isVice -> Badge("V")
                    }
                    if (p.subbedIn) Text(" ↑", color = App.colors.safe, style = MaterialTheme.typography.labelMedium)
                    if (p.subbedOut) Text(" ↓", color = App.colors.debt, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(3.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    ClubChip(data.clubShort(p.element), player?.elementType)
                    Events(
                        goals = v("goals_scored"), assists = v("assists"), cs = v("clean_sheets") > 0,
                        bonus = bonusPts, bonusPredicted = predictedBonus > 0 && v("bonus") == 0,
                        saves = v("saves"), pensSaved = v("penalties_saved"), pensMissed = v("penalties_missed"),
                        ownGoals = v("own_goals"), yellow = v("yellow_cards"), red = v("red_cards"),
                        defcon = stats.any { it.identifier == "defensive_contribution" && it.points > 0 },
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Status(club.state, club.nextKickoff, p.minutes)
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.End) {
                Text(
                    if (club.state == PlayState.UPCOMING && p.minutes == 0) "–" else "${if (p.multiplier == 0) p.points else p.total}",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = textColor,
                )
                if (p.multiplier > 1) Text("${p.points}×${p.multiplier}", style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
            }
        }
        AnimatedVisibility(open) {
            Breakdown(stats, predictedBonus, p)
        }
    }
}

@Composable
private fun Breakdown(stats: List<PlayerGwStatEntity>, predictedBonus: Int, p: EffectivePick) {
    Column(
        Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (stats.isEmpty() && predictedBonus == 0) {
            Text("Još nema bodova.", style = MaterialTheme.typography.labelMedium, color = App.colors.muted)
        }
        stats.filter { it.points != 0 || it.identifier == "minutes" }.forEach { s ->
            Line(statLabel(s.identifier, s.value), s.points)
        }
        if (predictedBonus > 0 && stats.none { it.identifier == "bonus" && it.points > 0 }) {
            Line("Bonus (predviđeno po BPS)", predictedBonus, App.colors.provisional)
        }
        if (p.multiplier > 1) {
            Line("× ${p.multiplier} (${if (p.multiplier == 3) "Triple Captain" else "kapiten"})", p.total, App.colors.gold, total = true)
        } else if (p.multiplier == 0) {
            Text("Na klupi, ne ulazi u zbir.", style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
        }
    }
}

@Composable
private fun Line(label: String, pts: Int, color: Color = MaterialTheme.colorScheme.onSurface, total: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = if (total) color else App.colors.muted)
        Text(
            if (total) "= $pts" else if (pts > 0) "+$pts" else "$pts",
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = if (pts < 0) App.colors.debt else color, textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun Status(state: PlayState, kickoff: Long?, minutes: Int) {
    Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
        when (state) {
            PlayState.LIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(App.colors.safe))
                Spacer(Modifier.width(4.dp))
                Text(if (minutes > 0) "$minutes'" else "uživo", style = MaterialTheme.typography.labelSmall, color = App.colors.safe)
            }
            PlayState.UPCOMING -> Text(
                if (minutes > 0) "$minutes' · ${kickoff?.let { kickoffShort(it) } ?: ""}" else kickoff?.let { kickoffShort(it) } ?: "",
                style = MaterialTheme.typography.labelSmall, color = App.colors.provisional, textAlign = TextAlign.End,
            )
            PlayState.BLANK -> Text("ne igra", style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
            PlayState.DONE -> Text("$minutes'", style = MaterialTheme.typography.labelSmall,
                color = if (minutes == 0) App.colors.debt else App.colors.muted)
        }
    }
}

@Composable
private fun Events(
    goals: Int, assists: Int, cs: Boolean, bonus: Int, bonusPredicted: Boolean,
    saves: Int, pensSaved: Int, pensMissed: Int, ownGoals: Int, yellow: Int, red: Int, defcon: Boolean,
) {
    val gold = App.colors.gold
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Repeat(R.drawable.ic_ev_goal, goals, MaterialTheme.colorScheme.onSurface, "gol")
        Repeat(R.drawable.ic_ev_assist, assists, MaterialTheme.colorScheme.onSurface, "asistencija")
        if (cs) Ev(R.drawable.ic_ev_cs, App.colors.safe, "čista mreža")
        if (defcon) Tag("DEF", App.colors.safe)
        if (saves >= 3) { Ev(R.drawable.ic_ev_save, MaterialTheme.colorScheme.onSurface, "odbrane"); Count(saves) }
        if (pensSaved > 0) { Ev(R.drawable.ic_ev_save, gold, "odbranjen penal"); Tag("P", gold) }
        if (bonus > 0) {
            val c = if (bonusPredicted) App.colors.provisional else gold
            Ev(R.drawable.ic_ev_bonus, c, "bonus"); Text("$bonus", style = MaterialTheme.typography.labelSmall, color = c, fontWeight = FontWeight.Bold)
        }
        Repeat(R.drawable.ic_ev_pen_miss, pensMissed, App.colors.debt, "promašen penal")
        Repeat(R.drawable.ic_ev_goal, ownGoals, App.colors.debt, "autogol")
        repeat(yellow) { CardIcon(Color(0xFFF5C518)) }
        repeat(red) { CardIcon(Color(0xFFE5484D)) }
    }
}

@Composable
private fun Repeat(@DrawableRes icon: Int, n: Int, tint: Color, desc: String) {
    if (n <= 0) return
    if (n <= 2) repeat(n) { Ev(icon, tint, desc) } else { Ev(icon, tint, desc); Count(n) }
}

@Composable
private fun Ev(@DrawableRes icon: Int, tint: Color, desc: String) =
    Icon(painterResource(icon), contentDescription = desc, tint = tint, modifier = Modifier.size(14.dp))

@Composable
private fun Count(n: Int) = Text("×$n", style = MaterialTheme.typography.labelSmall, color = App.colors.muted)

@Composable
private fun Tag(text: String, color: Color) = Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)

@Composable
private fun CardIcon(color: Color) = Box(Modifier.size(9.dp, 12.dp).clip(RoundedCornerShape(2.dp)).background(color))

@Composable
private fun Badge(text: String) {
    Spacer(Modifier.width(6.dp))
    Pill(text, App.colors.gold, filled = true)
}

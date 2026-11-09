package com.fplbp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fplbp.app.data.ChipCell
import com.fplbp.app.data.LeagueData
import com.fplbp.app.domain.Chips
import com.fplbp.app.ui.components.EmptyState
import com.fplbp.app.ui.components.SectionCard
import com.fplbp.app.ui.components.TeamDot
import com.fplbp.app.ui.components.teamColor
import com.fplbp.app.ui.theme.App

@Composable
fun ChipsScreen(data: LeagueData) {
    if (data.chipWindows.isEmpty()) return EmptyState("Podaci o čipovima još nisu učitani.")
    // One card per chip period (since 2025/26: kola 1–19 and 20–38).
    val periods = data.chipWindows.groupBy { it.startEvent to it.stopEvent }.toList().sortedBy { it.first.first }
    val current = data.liveGw?.gw ?: data.gws.lastOrNull()?.gw ?: 0

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        periods.forEachIndexed { i, (range, windows) ->
            item {
                val (from, to) = range
                val title = when {
                    periods.size == 1 -> "Čipovi"
                    i == 0 -> "Prvi deo sezone"
                    i == 1 -> "Drugi deo sezone"
                    else -> "Period ${i + 1}"
                }
                val state = when {
                    current > to -> " · završeno"
                    current >= from -> " · u toku"
                    else -> " · počinje u kolu $from"
                }
                SectionCard(title = title, subtitle = "Kola $from–$to$state") {
                    ChipGrid(data, windows)
                    if (i == periods.size - 1) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            LegendCell("K5", "iskorišćen", used = true)
                            LegendCell("✓", "dostupan", used = false)
                            LegendCell("–", "istekao", used = false, expired = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipGrid(data: LeagueData, windows: List<com.fplbp.app.data.db.ChipWindowEntity>) {
    Row {
        Column(Modifier.weight(1f)) {
            Spacer(Modifier.height(28.dp))
            data.teams.forEach { t ->
                Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamDot(teamColor(data, t.entryId))
                    Spacer(Modifier.width(6.dp))
                    Text(t.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Column {
            Row {
                windows.forEach { w ->
                    Box(Modifier.width(48.dp).height(28.dp), contentAlignment = Alignment.Center) {
                        Text(Chips.short(w.name), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            data.teams.forEach { t ->
                val cells = data.chipGrid[t.entryId].orEmpty()
                Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    windows.forEach { w -> cells.firstOrNull { it.window == w }?.let { Cell(it) } ?: Box(Modifier.width(48.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Cell(c: ChipCell) {
    val used = c.usedGw != null
    Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(36.dp, 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    when {
                        used -> Modifier.background(App.colors.gold)
                        c.expired -> Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        else -> Modifier.border(1.dp, App.colors.safe, RoundedCornerShape(6.dp))
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when { used -> "K${c.usedGw}"; c.expired -> "–"; else -> "✓" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = when { used -> MaterialTheme.colorScheme.background; c.expired -> App.colors.muted; else -> App.colors.safe },
            )
        }
    }
}

@Composable
private fun LegendCell(symbol: String, label: String, used: Boolean, expired: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp, 20.dp).clip(RoundedCornerShape(5.dp)).then(
                when {
                    used -> Modifier.background(App.colors.gold)
                    expired -> Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    else -> Modifier.border(1.dp, App.colors.safe, RoundedCornerShape(5.dp))
                }
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, style = MaterialTheme.typography.labelSmall,
                color = when { used -> MaterialTheme.colorScheme.background; expired -> App.colors.muted; else -> App.colors.safe })
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = App.colors.muted)
    }
}

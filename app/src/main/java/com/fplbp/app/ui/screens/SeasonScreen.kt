package com.fplbp.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fplbp.app.data.LeagueData

private val sections = listOf("Statistika", "Transferi", "Timovi", "Čipovi")

/** Everything that isn't debt or the current gameweek, in one tab. */
@Composable
fun SeasonScreen(data: LeagueData, onTeam: (Long) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Team shown in Transferi; the hits card on Statistika jumps there with a team preselected.
    var transferTeam by rememberSaveable { mutableLongStateOf(data.teams.firstOrNull()?.entryId ?: 0L) }
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            sections.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = tab == i,
                    onClick = { tab = i },
                    shape = SegmentedButtonDefaults.itemShape(i, sections.size),
                    icon = {}, // no check mark: four labels need the room
                ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> StatsScreen(data, onTeam, onHits = { transferTeam = it; tab = 1 })
                1 -> TransfersScreen(data, transferTeam) { transferTeam = it }
                2 -> PlayersScreen(data)
                else -> ChipsScreen(data)
            }
        }
    }
}

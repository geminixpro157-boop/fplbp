package com.fplbp.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fplbp.app.data.Category
import com.fplbp.app.data.LeagueData
import com.fplbp.app.data.PlayerContribution
import com.fplbp.app.domain.poena
import com.fplbp.app.ui.components.BarItem
import com.fplbp.app.ui.components.ColumnChart
import com.fplbp.app.ui.components.EmptyState
import com.fplbp.app.ui.components.HBarChart
import com.fplbp.app.ui.components.SectionCard
import com.fplbp.app.ui.components.Segment
import com.fplbp.app.ui.components.StackedBar
import com.fplbp.app.ui.components.StatTile
import com.fplbp.app.ui.components.teamColor
import com.fplbp.app.ui.theme.App

private const val LEAGUE = -1L

/** Category order for the stacked bar; colors follow the validated categorical order. */
private val STACK = listOf("goals", "assists", "cs", "bonus", "minutes", "defcon", "saves")

@Composable
private fun categoryColor(cat: String): Color = App.colors.series[STACK.indexOf(cat).coerceAtLeast(0) % App.colors.series.size]

private fun stackKey(cat: String) = if (cat == "pens") "saves" else cat

@Composable
fun PlayersScreen(data: LeagueData) {
    var selected by rememberSaveable { mutableLongStateOf(LEAGUE) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selected == LEAGUE, onClick = { selected = LEAGUE }, label = { Text("Cela liga") })
                data.teams.forEach { t ->
                    FilterChip(selected = selected == t.entryId, onClick = { selected = t.entryId }, label = { Text(t.name) })
                }

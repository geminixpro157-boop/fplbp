package com.fplbp.app.data

import com.fplbp.app.data.db.ChipWindowEntity
import com.fplbp.app.data.db.ClubEntity
import com.fplbp.app.data.db.PlayerEntity
import com.fplbp.app.data.db.TeamEntity
import com.fplbp.app.domain.DebtRow
import com.fplbp.app.domain.GwOutcome
import com.fplbp.app.domain.GwScores
import com.fplbp.app.domain.LineupResult
import com.fplbp.app.domain.TeamScore
import com.fplbp.app.domain.TeamSeasonStats

enum class GwStatus { FINAL, AWAITING, LIVE }

data class GwView(
    val gw: Int,
    val name: String,
    val deadline: Long,
    val status: GwStatus,
    val scores: List<TeamScore>,
    val outcome: GwOutcome?,
    val lineups: Map<Long, LineupResult>,
    /** True when our computed lineup total matches FPL's official points (final GWs). */
    val verified: Map<Long, Boolean>,
    val fixturesDone: Int,
    val fixturesTotal: Int,
) {
    val isFinal get() = status == GwStatus.FINAL
    fun toScores() = GwScores(gw, isFinal, scores)
}

data class PlayerContribution(
    val element: Int,
    val total: Int,
    val byCategory: Map<String, Int>,
    /** Extra points from the captain armband (the 2nd/3rd copy). */
    val captainExtra: Int,
    val gwsCounted: Int,
)

data class CaptainPick(
    val gw: Int,
    val element: Int,
    /** Points incl. multiplier. */
    val points: Int,
    val passedToVice: Boolean,
    val isFinal: Boolean,
)

data class ChipCell(val window: ChipWindowEntity, val usedGw: Int?, val expired: Boolean)

data class LeagueData(
    val leagueId: Long,
    val leagueName: String,
    val debt: Long,
    val teams: List<TeamEntity>,
    val clubs: Map<Int, ClubEntity>,
    val players: Map<Int, PlayerEntity>,
    val gws: List<GwView>,
    val debtTable: List<DebtRow>,
    val pot: Long,
    val finalCount: Int,
    val raceGws: List<Int>,
    val raceSeries: Map<Long, List<Double>>,
    val contributions: Map<Long, List<PlayerContribution>>,
    val captains: Map<Long, List<CaptainPick>>,
    val chipWindows: List<ChipWindowEntity>,
    val chipGrid: Map<Long, List<ChipCell>>,
    val seasonStats: Map<Long, TeamSeasonStats>,
    val currentGw: Int?,
    val lastSync: Long,
) {
    val teamById: Map<Long, TeamEntity> = teams.associateBy { it.entryId }
    val liveGw: GwView? get() = gws.lastOrNull { !it.isFinal }
    fun teamName(id: Long) = teamById[id]?.name ?: "?"
    fun playerName(id: Int) = players[id]?.webName ?: "#$id"
    fun clubShort(element: Int) = players[element]?.let { clubs[it.clubId]?.shortName } ?: ""
}

/** Points categories shown in the app (FPL explain identifiers grouped). */
object Category {
    val ORDER = listOf("goals", "assists", "cs", "bonus", "minutes", "defcon", "saves", "pens", "minus")

    fun of(identifier: String): String = when (identifier) {
        "goals_scored" -> "goals"
        "assists" -> "assists"
        "clean_sheets" -> "cs"
        "bonus" -> "bonus"
        "minutes" -> "minutes"
        "defensive_contribution" -> "defcon"
        "saves" -> "saves"
        "penalties_saved" -> "pens"
        "yellow_cards", "red_cards", "own_goals", "penalties_missed", "goals_conceded" -> "minus"
        else -> "other"
    }

    fun label(cat: String): String = when (cat) {
        "goals" -> "Golovi"
        "assists" -> "Asistencije"
        "cs" -> "Čista mreža"
        "bonus" -> "Bonus"
        "minutes" -> "Minutaža"
        "defcon" -> "Def. doprinos"
        "saves" -> "Odbrane"
        "pens" -> "Odbranjeni penali"
        "minus" -> "Minusi"
        else -> "Ostalo"
    }
}

data class SyncState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val message: String = "",
    /** FPL is updating; showing cached data. */
    val updating: Boolean = false,
    val error: String? = null,
)

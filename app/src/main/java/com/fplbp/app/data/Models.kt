package com.fplbp.app.data

import com.fplbp.app.data.db.ChipWindowEntity
import com.fplbp.app.data.db.ClubEntity
import com.fplbp.app.data.db.FixtureEntity
import com.fplbp.app.data.db.PlayerGwStatEntity
import com.fplbp.app.data.db.PlayerEntity
import com.fplbp.app.data.db.TeamEntity
import com.fplbp.app.domain.DebtRow
import com.fplbp.app.domain.GwOutcome
import com.fplbp.app.domain.GwScores
import com.fplbp.app.domain.LineupResult
import com.fplbp.app.domain.TeamScore
import com.fplbp.app.domain.TeamSeasonStats
import com.fplbp.app.domain.TransferAnalysis

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
    val fixtures: List<FixtureEntity> = emptyList(),
    /** element -> points breakdown lines for this GW. */
    val stats: Map<Int, List<PlayerGwStatEntity>> = emptyMap(),
    /** element -> predicted bonus (not yet confirmed by FPL). */
    val provisionalBonus: Map<Int, Int> = emptyMap(),
) {
    val isFinal get() = status == GwStatus.FINAL
    fun toScores() = GwScores(gw, isFinal, scores)

    /** Match status of a club in this GW (handles blanks and double gameweeks). */
    fun clubState(clubId: Int?): ClubState {
        if (clubId == null || isFinal) return ClubState(PlayState.DONE, null)
        val fx = fixtures.filter { it.teamH == clubId || it.teamA == clubId }
        if (fx.isEmpty()) return ClubState(if (fixtures.isEmpty()) PlayState.DONE else PlayState.BLANK, null)
        val open = fx.filter { !it.finished && !it.finishedProvisional }
        return when {
            open.isEmpty() -> ClubState(PlayState.DONE, null)
            open.any { it.started } -> ClubState(PlayState.LIVE, open.filter { !it.started }.mapNotNull { it.kickoff }.minOrNull())
            else -> ClubState(PlayState.UPCOMING, open.mapNotNull { it.kickoff }.minOrNull())
        }
    }
}

enum class PlayState { DONE, LIVE, UPCOMING, BLANK }

/** [nextKickoff] = kickoff of the club's next unstarted match in this GW, if any. */
data class ClubState(val state: PlayState, val nextKickoff: Long?)

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
    val transfers: List<TransferAnalysis.Result> = emptyList(),
    val transferSummary: Map<Long, TransferAnalysis.TeamSummary> = emptyMap(),
    val transferWeeks: List<TransferAnalysis.Week> = emptyList(),
) {
    val teamById: Map<Long, TeamEntity> = teams.associateBy { it.entryId }
    val liveGw: GwView? get() = gws.lastOrNull { !it.isFinal }
    fun teamName(id: Long) = teamById[id]?.name ?: "?"
    fun playerName(id: Int) = players[id]?.webName ?: "#$id"
    fun clubShort(element: Int) = players[element]?.let { clubs[it.clubId]?.shortName } ?: ""
}

/** Points categories shown in the app (FPL explain identifiers grouped). */
object Category {
    /** Positive categories, in fixed order (colors follow this order, never the rank). */
    val POSITIVE = listOf("goals", "assists", "cs", "bonus", "minutes", "defcon", "saves", "other")
    val MINUS = listOf("yellow", "red", "conceded", "own_goals", "pen_missed")

    fun of(identifier: String): String = when (identifier) {
        "goals_scored" -> "goals"
        "assists" -> "assists"
        "clean_sheets" -> "cs"
        "bonus" -> "bonus"
        "minutes" -> "minutes"
        "defensive_contribution" -> "defcon"
        "saves", "penalties_saved" -> "saves"
        "yellow_cards" -> "yellow"
        "red_cards" -> "red"
        "goals_conceded" -> "conceded"
        "own_goals" -> "own_goals"
        "penalties_missed" -> "pen_missed"
        else -> "other"
    }

    fun isMinus(cat: String) = cat in MINUS

    fun label(cat: String): String = when (cat) {
        "goals" -> "Golovi"
        "assists" -> "Asistencije"
        "cs" -> "Čista mreža"
        "bonus" -> "Bonus"
        "minutes" -> "Minutaža"
        "defcon" -> "Def. doprinos"
        "saves" -> "Odbrane i penali"
        "yellow" -> "Žuti kartoni"
        "red" -> "Crveni kartoni"
        "conceded" -> "Primljeni golovi"
        "own_goals" -> "Autogolovi"
        "pen_missed" -> "Promašeni penali"
        else -> "Ostalo"
    }

    /** Short labels for one-line breakdowns. */
    fun short(cat: String): String = when (cat) {
        "goals" -> "G"
        "assists" -> "A"
        "cs" -> "ČM"
        "bonus" -> "B"
        "minutes" -> "Min"
        "defcon" -> "DEF"
        "saves" -> "Odb"
        "other" -> "Ost"
        else -> "−"
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

package com.fplbp.app.domain

import kotlin.math.sqrt

data class TeamSeasonStats(
    val entryId: Long,
    val gwsPlayed: Int,
    val totalNet: Int,
    val avgNet: Double,
    val best: Pair<Int, Int>?,   // gw to net
    val worst: Pair<Int, Int>?,
    /** Standard deviation of net points (lower = more consistent). */
    val stdDev: Double,
    val hitsCount: Int,
    val hitsPoints: Int,
    val benchPoints: Int,
    val timesLast: Int,
    val longestLosingStreak: Int,
    /** Current run of GWs without being last. */
    val currentSafeStreak: Int,
    /** Smallest margin by which the team avoided being last (gw to margin). */
    val closestEscape: Pair<Int, Int>?,
)

object SeasonStats {

    fun compute(entryId: Long, finals: List<GwScores>, debt: Long): TeamSeasonStats {
        val sorted = finals.filter { it.isFinal }.sortedBy { it.gw }
        val mine = sorted.mapNotNull { g -> g.scores.firstOrNull { it.entryId == entryId }?.let { g to it } }
        val nets = mine.map { it.second.net }
        val avg = if (nets.isEmpty()) 0.0 else nets.average()
        val sd = if (nets.size < 2) 0.0 else sqrt(nets.sumOf { (it - avg) * (it - avg) } / nets.size)

        var longest = 0
        var run = 0
        var safeRun = 0
        var times = 0
        var closest: Pair<Int, Int>? = null
        for ((g, s) in mine) {
            val o = DebtCalculator.outcome(g, debt) ?: continue
            if (entryId in o.losers) {
                times++; run++; longest = maxOf(longest, run); safeRun = 0
            } else {
                run = 0; safeRun++
                val margin = s.net - o.lowestNet
                if (closest == null || margin < closest.second) closest = g.gw to margin
            }
        }
        return TeamSeasonStats(
            entryId = entryId,
            gwsPlayed = mine.size,
            totalNet = nets.sum(),
            avgNet = avg,
            best = mine.maxByOrNull { it.second.net }?.let { it.first.gw to it.second.net },
            worst = mine.minByOrNull { it.second.net }?.let { it.first.gw to it.second.net },
            stdDev = sd,
            hitsCount = mine.count { it.second.transferCost > 0 },
            hitsPoints = mine.sumOf { it.second.transferCost },
            benchPoints = mine.filter { it.second.chip != "bboost" }.sumOf { it.second.benchPoints },
            timesLast = times,
            longestLosingStreak = longest,
            currentSafeStreak = safeRun,
            closestEscape = closest,
        )
    }

    /** Average net points of the league per GW. */
    fun leagueAverage(g: GwScores): Double = if (g.scores.isEmpty()) 0.0 else g.scores.map { it.net }.average()
}

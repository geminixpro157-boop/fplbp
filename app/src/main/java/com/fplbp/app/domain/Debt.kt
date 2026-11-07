package com.fplbp.app.domain

/** One team's result in one gameweek. net = points - transferCost. */
data class TeamScore(
    val entryId: Long,
    val points: Int,
    val transferCost: Int,
    val benchPoints: Int,
    val chip: String?,
) {
    val net: Int get() = points - transferCost
}

/** A gameweek with every participating team's score. */
data class GwScores(val gw: Int, val isFinal: Boolean, val scores: List<TeamScore>)

data class GwOutcome(
    val gw: Int,
    val isFinal: Boolean,
    val losers: List<Long>,
    val lowestNet: Int,
    /** Gap from the lowest score to the next-lowest distinct score (null if everyone tied). */
    val gapToSafety: Int?,
    /** Debt each loser owes for this GW. */
    val share: Rational,
    /** 1-based competition ranking (ties share a rank), best first. */
    val ranks: Map<Long, Int>,
)

data class DebtRow(
    val entryId: Long,
    val total: Rational,
    /** GWs where the team was (possibly jointly) last. */
    val timesLast: Int,
    val timesLastShared: Int,
)

object DebtCalculator {

    fun outcome(g: GwScores, debt: Long): GwOutcome? {
        if (g.scores.isEmpty()) return null
        val lowest = g.scores.minOf { it.net }
        val losers = g.scores.filter { it.net == lowest }.map { it.entryId }
        val nextLowest = g.scores.map { it.net }.filter { it > lowest }.minOrNull()
        return GwOutcome(
            gw = g.gw,
            isFinal = g.isFinal,
            losers = losers,
            lowestNet = lowest,
            gapToSafety = nextLowest?.minus(lowest),
            share = Rational.of(debt, losers.size.toLong()),
            ranks = ranks(g.scores),
        )
    }

    fun ranks(scores: List<TeamScore>): Map<Long, Int> {
        val sorted = scores.sortedByDescending { it.net }
        val out = HashMap<Long, Int>()
        sorted.forEachIndexed { i, s ->
            val rank = if (i > 0 && sorted[i - 1].net == s.net) out.getValue(sorted[i - 1].entryId) else i + 1
            out[s.entryId] = rank
        }
        return out
    }

    /** Debt table over FINAL gameweeks only. Teams without losses still appear with zero. */
    fun table(teams: List<Long>, gws: List<GwScores>, debt: Long): List<DebtRow> {
        val totals = teams.associateWith { Rational.ZERO }.toMutableMap()
        val times = teams.associateWith { 0 }.toMutableMap()
        val shared = teams.associateWith { 0 }.toMutableMap()
        for (g in gws) {
            if (!g.isFinal) continue
            val o = outcome(g, debt) ?: continue
            for (l in o.losers) {
                totals[l] = (totals[l] ?: Rational.ZERO) + o.share
                times[l] = (times[l] ?: 0) + 1
                if (o.losers.size > 1) shared[l] = (shared[l] ?: 0) + 1
            }
        }
        return teams.map { DebtRow(it, totals.getValue(it), times.getValue(it), shared.getValue(it)) }
            .sortedWith(compareByDescending<DebtRow> { it.total }.thenBy { it.entryId })
    }

    /** Total pot = debt x number of final gameweeks that had at least one team. */
    fun pot(gws: List<GwScores>, debt: Long): Long = gws.count { it.isFinal && it.scores.isNotEmpty() } * debt

    /** Cumulative debt per team after each final GW (for the "debt race" chart). */
    fun cumulative(teams: List<Long>, gws: List<GwScores>, debt: Long): Pair<List<Int>, Map<Long, List<Rational>>> {
        val finals = gws.filter { it.isFinal && it.scores.isNotEmpty() }.sortedBy { it.gw }
        val running = teams.associateWith { Rational.ZERO }.toMutableMap()
        val series = teams.associateWith { mutableListOf<Rational>() }
        for (g in finals) {
            val o = outcome(g, debt)!!
            for (l in o.losers) running[l] = running.getValue(l) + o.share
            teams.forEach { series.getValue(it).add(running.getValue(it)) }
        }
        return finals.map { it.gw } to series
    }
}

package com.fplbp.app.domain

/** Gameweek comparisons across the league. */
object Decider {

    /** Per team: players counting for that team only (nobody else in the league had them counting). */
    fun leagueDifferentials(lineups: Map<Long, LineupResult>): Map<Long, List<EffectivePick>> {
        val owners = HashMap<Int, MutableSet<Long>>()
        lineups.forEach { (team, lu) -> lu.picks.filter { it.multiplier > 0 }.forEach { owners.getOrPut(it.element) { HashSet() } += team } }
        return lineups.mapValues { (team, lu) ->
            lu.picks.filter { it.multiplier > 0 && owners[it.element]?.size == 1 && owners[it.element]?.contains(team) == true }
                .sortedByDescending { it.total }
        }
    }
}

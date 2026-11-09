package com.fplbp.app.domain

/** League-wide comparisons built from each team's resolved lineups, one gameweek at a time. */
object Insights {

    /** One gameweek: lineups of every team (after auto-subs and captaincy). */
    data class Gw(val gw: Int, val lineups: Map<Long, LineupResult>)

    private fun armband(lu: LineupResult): EffectivePick? =
        lu.picks.firstOrNull { (it.isCaptain && !lu.captaincyToVice) || it.captaincyPassed }?.takeIf { it.multiplier >= 2 }

    // ---------------------------------------------------------------- captains

    data class CaptainStats(
        val entryId: Long,
        val gameweeks: Int,
        /** Gameweeks where the captain was (jointly) the team's best counting player. */
        val hits: Int,
        /** Extra points the team would have had with its best counting player as captain. */
        val lost: Int,
        /** Captain points incl. multiplier. */
        val points: Int,
    )

    fun captains(gws: List<Gw>): Map<Long, CaptainStats> {
        val acc = HashMap<Long, CaptainStats>()
        for (g in gws) for ((team, lu) in g.lineups) {
            val cap = armband(lu) ?: continue
            val best = lu.picks.filter { it.multiplier > 0 }.maxOfOrNull { it.points } ?: continue
            val prev = acc[team] ?: CaptainStats(team, 0, 0, 0, 0)
            acc[team] = prev.copy(
                gameweeks = prev.gameweeks + 1,
                hits = prev.hits + if (cap.points >= best) 1 else 0,
                lost = prev.lost + (best - cap.points).coerceAtLeast(0) * (cap.multiplier - 1),
                points = prev.points + cap.total,
            )
        }
        return acc
    }

    data class Popular(val element: Int, val times: Int, val avgRaw: Double, val teams: Set<Long>)

    /** Most captained players across the league (raw points average). */
    fun popularCaptains(gws: List<Gw>): List<Popular> {
        val picks = gws.flatMap { g -> g.lineups.mapNotNull { (team, lu) -> armband(lu)?.let { team to it } } }
        return picks.groupBy { it.second.element }.map { (el, list) ->
            Popular(el, list.size, list.map { it.second.points }.average(), list.map { it.first }.toSet())
        }.sortedWith(compareByDescending<Popular> { it.times }.thenByDescending { it.avgRaw })
    }

    // ---------------------------------------------------------------- own players

    data class OwnShare(
        val entryId: Long,
        /** Points from players nobody else in the league had counting in that gameweek. */
        val own: Int,
        val total: Int,
        /** element -> points, for the players that made up [own]. */
        val players: List<Pair<Int, Int>>,
    ) {
        val share: Double get() = if (total <= 0) 0.0 else own.toDouble() / total
    }

    fun ownShare(gws: List<Gw>): Map<Long, OwnShare> {
        val own = HashMap<Long, Int>()
        val total = HashMap<Long, Int>()
        val players = HashMap<Long, HashMap<Int, Int>>()
        for (g in gws) {
            val diffs = Decider.leagueDifferentials(g.lineups)
            for ((team, lu) in g.lineups) {
                total[team] = (total[team] ?: 0) + lu.points
                diffs[team].orEmpty().forEach { p ->
                    own[team] = (own[team] ?: 0) + p.total
                    val m = players.getOrPut(team) { HashMap() }
                    m[p.element] = (m[p.element] ?: 0) + p.total
                }
            }
        }
        return total.keys.associateWith { team ->
            OwnShare(team, own[team] ?: 0, total[team] ?: 0,
                players[team].orEmpty().entries.map { it.key to it.value }.sortedByDescending { it.second })
        }
    }

    // ---------------------------------------------------------------- grouping

    /** Counted points (with captaincy) per team, grouped by [key] (e.g. position or club). */
    fun pointsBy(gws: List<Gw>, key: (element: Int) -> Int?): Map<Long, Map<Int, Int>> {
        val acc = HashMap<Long, HashMap<Int, Int>>()
        for (g in gws) for ((team, lu) in g.lineups) {
            val m = acc.getOrPut(team) { HashMap() }
            lu.picks.filter { it.multiplier > 0 }.forEach { p ->
                val k = key(p.element) ?: return@forEach
                m[k] = (m[k] ?: 0) + p.total
            }
        }
        return acc
    }

    // ---------------------------------------------------------------- ownership

    data class Owned(val element: Int, val owners: Map<Long, Mark>, val points: Int)
    enum class Mark { CAPTAIN, VICE, PLAYING, BENCH }

    /** Players in at least [minOwners] squads this gameweek, most owned first. */
    fun ownership(g: Gw, minOwners: Int = 2, limit: Int = 15): List<Owned> {
        val map = HashMap<Int, HashMap<Long, Mark>>()
        val pts = HashMap<Int, Int>()
        for ((team, lu) in g.lineups) {
            val cap = armband(lu)
            lu.picks.forEach { p ->
                pts[p.element] = p.points
                map.getOrPut(p.element) { HashMap() }[team] = when {
                    cap?.element == p.element -> Mark.CAPTAIN
                    p.isVice && p.multiplier > 0 -> Mark.VICE
                    p.multiplier > 0 -> Mark.PLAYING
                    else -> Mark.BENCH
                }
            }
        }
        return map.filter { it.value.size >= minOwners }
            .map { (el, owners) -> Owned(el, owners, pts[el] ?: 0) }
            .sortedWith(compareByDescending<Owned> { it.owners.size }.thenByDescending { it.points })
            .take(limit)
    }
}

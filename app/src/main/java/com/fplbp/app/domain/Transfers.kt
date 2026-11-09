package com.fplbp.app.domain

/**
 * Transfer analysis, using raw player points (no captaincy):
 *  - same gameweek: player in vs player out in the gameweek of the transfer;
 *  - while held: player in for as long as he stayed in the squad, vs what the sold player
 *    scored over those same gameweeks.
 * Free Hit gameweeks are not transfers: they are judged as a whole (Free Hit squad vs the
 * squad of the previous gameweek).
 */
object TransferAnalysis {

    data class Move(val entryId: Long, val gw: Int, val elementIn: Int, val elementOut: Int)

    data class Result(
        val entryId: Long,
        val gw: Int,
        val elementIn: Int,
        val elementOut: Int,
        val weekIn: Int,
        val weekOut: Int,
        val heldGws: List<Int>,
        val heldIn: Int,
        val heldOut: Int,
        val wildcard: Boolean,
    ) {
        val weekDiff: Int get() = weekIn - weekOut
        val heldDiff: Int get() = heldIn - heldOut
    }

    data class FreeHit(val entryId: Long, val gw: Int, val points: Int, val wouldHave: Int) {
        val diff: Int get() = points - wouldHave
    }

    data class TeamSummary(
        val entryId: Long,
        val transfers: Int,
        val hits: Int,
        val heldNet: Int,
        val best: Result?,
        val worst: Result?,
        val freeHits: List<FreeHit>,
    ) {
        /** What transfers brought in total, after paying for the hits. */
        val netAfterHits: Int get() = heldNet - hits
    }

    /** Transfers of one team in one gameweek, judged on that gameweek only. */
    data class Week(val entryId: Long, val gw: Int, val results: List<Result>, val hit: Int, val freeHit: FreeHit?) {
        val net: Int get() = (freeHit?.diff ?: results.sumOf { it.weekDiff }) - hit
    }

    /**
     * @param points raw points of an element in a gameweek
     * @param squad the 15 elements a team had in a gameweek (empty when unknown)
     * @param chip chip a team played in a gameweek
     * @param lastGw latest gameweek with data (the live one included)
     */
    fun analyze(
        moves: List<Move>,
        points: (gw: Int, element: Int) -> Int,
        squad: (entryId: Long, gw: Int) -> Set<Int>,
        chip: (entryId: Long, gw: Int) -> String?,
        lastGw: Int,
    ): List<Result> = moves
        .filter { chip(it.entryId, it.gw) != "freehit" }
        .sortedWith(compareBy({ it.gw }, { it.entryId }))
        .map { m ->
            val held = mutableListOf<Int>()
            var g = m.gw
            while (g <= lastGw) {
                // A later Free Hit temporarily removes the player; he is back the week after.
                if (g != m.gw && chip(m.entryId, g) == "freehit") { g++; continue }
                val s = squad(m.entryId, g)
                if (s.isEmpty() || m.elementIn !in s) break
                held += g
                g++
            }
            Result(
                entryId = m.entryId,
                gw = m.gw,
                elementIn = m.elementIn,
                elementOut = m.elementOut,
                weekIn = points(m.gw, m.elementIn),
                weekOut = points(m.gw, m.elementOut),
                heldGws = held,
                heldIn = held.sumOf { points(it, m.elementIn) },
                heldOut = held.sumOf { points(it, m.elementOut) },
                wildcard = chip(m.entryId, m.gw) == "wildcard",
            )
        }

    fun summary(entryId: Long, results: List<Result>, hits: Int, freeHits: List<FreeHit>): TeamSummary {
        val mine = results.filter { it.entryId == entryId }
        return TeamSummary(
            entryId = entryId,
            transfers = mine.size,
            hits = hits,
            heldNet = mine.sumOf { it.heldDiff },
            best = mine.filter { it.heldDiff > 0 }.maxByOrNull { it.heldDiff },
            worst = mine.filter { it.heldDiff < 0 }.minByOrNull { it.heldDiff },
            freeHits = freeHits.filter { it.entryId == entryId },
        )
    }

    fun weeks(results: List<Result>, hitByTeamGw: Map<Pair<Long, Int>, Int>, freeHits: List<FreeHit>): List<Week> {
        val keys = results.map { it.entryId to it.gw }.toSet() +
            hitByTeamGw.filterValues { it > 0 }.keys + freeHits.map { it.entryId to it.gw }
        return keys.map { (team, gw) ->
            Week(
                entryId = team,
                gw = gw,
                results = results.filter { it.entryId == team && it.gw == gw },
                hit = hitByTeamGw[team to gw] ?: 0,
                freeHit = freeHits.firstOrNull { it.entryId == team && it.gw == gw },
            )
        }.sortedWith(compareBy({ it.gw }, { it.entryId }))
    }
}

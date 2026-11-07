package com.fplbp.app.domain

/**
 * "Who decided it": compares two teams in one gameweek and keeps only what differed.
 * Every line is (A's points - B's points) for that item, so the lines add up to netA - netB.
 */
object Decider {

    enum class Kind { ONLY_A, ONLY_B, MULTIPLIER, HITS }

    data class Line(
        val kind: Kind,
        /** Player element id, or null for the hits line. */
        val element: Int?,
        val pointsA: Int,
        val pointsB: Int,
        val multA: Int,
        val multB: Int,
        /** Still to play for either side (live GW). */
        val pending: Boolean,
        val subbedIn: Boolean,
    ) {
        val diff: Int get() = pointsA - pointsB
    }

    data class Comparison(
        val gapNet: Int,              // netA - netB
        val lines: List<Line>,        // sorted by impact
        /** True when lines explain the full gap (our lineup totals match the official ones). */
        val exact: Boolean,
    ) {
        val forA get() = lines.filter { it.diff > 0 }
        val forB get() = lines.filter { it.diff < 0 }
    }

    fun compare(a: LineupResult, costA: Int, netA: Int, b: LineupResult, costB: Int, netB: Int): Comparison {
        val counting = { lu: LineupResult -> lu.picks.filter { it.multiplier > 0 }.associateBy { it.element } }
        val ca = counting(a)
        val cb = counting(b)
        val lines = mutableListOf<Line>()
        for (e in ca.keys + cb.keys) {
            val pa = ca[e]
            val pb = cb[e]
            val ta = pa?.total ?: 0
            val tb = pb?.total ?: 0
            val kind = when {
                pa != null && pb == null -> Kind.ONLY_A
                pa == null && pb != null -> Kind.ONLY_B
                pa!!.multiplier != pb!!.multiplier -> Kind.MULTIPLIER
                else -> null
            } ?: continue
            val pending = (pa?.pending == true) || (pb?.pending == true)
            if (ta == tb && !pending) continue // e.g. a blank: nothing to explain
            lines += Line(kind, e, ta, tb, pa?.multiplier ?: 0, pb?.multiplier ?: 0, pending,
                subbedIn = (pa?.subbedIn == true) || (pb?.subbedIn == true))
        }
        if (costA != costB) lines += Line(Kind.HITS, null, -costA, -costB, 0, 0, pending = false, subbedIn = false)
        val sorted = lines.sortedWith(compareByDescending<Line> { kotlin.math.abs(it.diff) }.thenBy { it.element ?: 0 })
        val explained = lines.sumOf { it.diff }
        return Comparison(netA - netB, sorted, explained == netA - netB)
    }

    /** One sentence naming what decided it, from the loser's (A's) point of view. Null if A isn't behind. */
    fun verdict(cmp: Comparison, nameA: String, nameB: String, grossA: Int, costA: Int, netB: Int, playerName: (Int) -> String): String? {
        if (cmp.gapNet >= 0) return null
        if (costA > 0 && grossA > netB) return "Bez −$costA tim $nameA bi prestigao tim $nameB."
        if (costA > 0 && grossA == netB) return "Bez −$costA tim $nameA bi bio izjednačen sa timom $nameB."
        val top = cmp.forB.firstOrNull { it.element != null } ?: return null
        val name = playerName(top.element!!)
        return when (top.kind) {
            Kind.ONLY_B -> "Najviše je presudio $name (samo kod tima $nameB): ${signed(-top.diff)}."
            Kind.MULTIPLIER -> "Najviše je presudila kapitenska traka na igraču $name: ${signed(-top.diff)}."
            Kind.ONLY_A -> "Najviše je presudio $name kod tima $nameA: ${poena(top.pointsA)}."
            Kind.HITS -> null
        }
    }

    fun signed(n: Int) = if (n > 0) "+$n" else "$n"

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

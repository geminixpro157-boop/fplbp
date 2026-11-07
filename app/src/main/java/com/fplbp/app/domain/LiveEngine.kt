package com.fplbp.app.domain

/** FPL element_type: 1 GK, 2 DEF, 3 MID, 4 FWD. */
object Pos {
    const val GK = 1
    const val DEF = 2
    const val MID = 3
    const val FWD = 4

    /** Minimum players per position in the playing XI (FPL squad_min_play). */
    val MIN_PLAY = mapOf(GK to 1, DEF to 3, MID to 2, FWD to 1)

    fun short(t: Int) = when (t) { GK -> "GK"; DEF -> "ODB"; MID -> "VEZ"; FWD -> "NAP"; else -> "?" }
}

data class PickIn(
    val element: Int,
    /** 1..11 starters, 12..15 bench (12 = bench GK). */
    val position: Int,
    val multiplier: Int,
    val isCaptain: Boolean,
    val isVice: Boolean,
)

data class PlayerLive(
    val element: Int,
    val elementType: Int,
    val minutes: Int,
    /** Points incl. any predicted bonus not yet confirmed. */
    val points: Int,
    /** True when every fixture of the player's club in this GW is finished (or the club blanks). */
    val done: Boolean,
)

data class EffectivePick(
    val element: Int,
    val position: Int,
    val multiplier: Int,
    val points: Int,
    val isCaptain: Boolean,
    val isVice: Boolean,
    val subbedIn: Boolean,
    val subbedOut: Boolean,
    /** Vice-captain received the captaincy. */
    val captaincyPassed: Boolean,
    /** Player's club still has a fixture to finish in this GW. */
    val pending: Boolean = false,
    val minutes: Int = 0,
) {
    val total: Int get() = points * multiplier
}

data class LineupResult(
    val picks: List<EffectivePick>,
    val autoSubs: List<Pair<Int, Int>>, // out -> in
    val captaincyToVice: Boolean,
) {
    val points: Int get() = picks.sumOf { it.total }
    /** Points of players that did not count (bench), for "bench points". */
    val benchPoints: Int get() = picks.filter { it.multiplier == 0 && !it.subbedOut }.sumOf { it.points }
}

/**
 * Applies FPL's end-of-gameweek rules early, as soon as they are certain:
 * automatic substitutions and captain -> vice-captain.
 */
object LiveEngine {

    fun resolve(picks: List<PickIn>, chip: String?, live: Map<Int, PlayerLive>): LineupResult {
        val byPos = picks.sortedBy { it.position }
        val benchBoost = chip == "bboost"
        val capMult = when {
            chip == "3xc" -> 3
            else -> byPos.firstOrNull { it.isCaptain }?.multiplier?.takeIf { it >= 2 } ?: 2
        }
        fun lv(e: Int) = live[e]
        fun didNotPlay(e: Int): Boolean { val p = lv(e) ?: return false; return p.done && p.minutes == 0 }
        fun played(e: Int): Boolean = (lv(e)?.minutes ?: 0) > 0

        val lineup = byPos.filter { it.position <= 11 }.map { it.element }.toMutableList()
        val bench = byPos.filter { it.position > 11 }.map { it.element }.toMutableList()
        val subs = mutableListOf<Pair<Int, Int>>()

        if (!benchBoost) {
            for (starter in byPos.filter { it.position <= 11 }) {
                val out = starter.element
                if (!didNotPlay(out)) continue
                val outType = lv(out)?.elementType ?: continue
                for (cand in bench.toList()) {
                    if (!played(cand)) continue
                    val inType = lv(cand)?.elementType ?: continue
                    if ((outType == Pos.GK) != (inType == Pos.GK)) continue
                    val trial = lineup.map { if (it == out) cand else it }
                    if (!validFormation(trial.mapNotNull { lv(it)?.elementType })) continue
                    lineup[lineup.indexOf(out)] = cand
                    bench.remove(cand)
                    subs += out to cand
                    break
                }
            }
        }

        val counting: Set<Int> = if (benchBoost) byPos.map { it.element }.toSet() else lineup.toSet()
        val captain = byPos.firstOrNull { it.isCaptain }?.element
        val vice = byPos.firstOrNull { it.isVice }?.element
        val toVice = captain != null && didNotPlay(captain) && vice != null && vice in counting && played(vice)
        val armband = if (toVice) vice else captain

        val subbedIn = subs.map { it.second }.toSet()
        val subbedOut = subs.map { it.first }.toSet()
        val result = byPos.map { p ->
            val mult = when {
                p.element !in counting -> 0
                p.element == armband -> capMult
                else -> 1
            }
            EffectivePick(
                element = p.element,
                position = p.position,
                multiplier = mult,
                points = lv(p.element)?.points ?: 0,
                isCaptain = p.isCaptain,
                isVice = p.isVice,
                subbedIn = p.element in subbedIn,
                subbedOut = p.element in subbedOut,
                captaincyPassed = toVice && p.element == vice,
                pending = lv(p.element)?.done == false,
                minutes = lv(p.element)?.minutes ?: 0,
            )
        }
        return LineupResult(result, subs, toVice)
    }

    fun validFormation(types: List<Int>): Boolean {
        if (types.count { it == Pos.GK } != 1) return false
        return Pos.MIN_PLAY.all { (t, min) -> types.count { it == t } >= min }
    }
}

/** Predicts bonus points from BPS for a fixture whose bonus isn't confirmed yet. */
object BonusPredictor {

    /** bps: element -> bps score for one fixture. Returns element -> 3/2/1. */
    fun predict(bps: Map<Int, Int>): Map<Int, Int> {
        val groups = bps.entries.groupBy({ it.value }, { it.key }).toSortedMap(compareByDescending { it })
        val out = HashMap<Int, Int>()
        var position = 1
        for ((_, players) in groups) {
            if (position > 3) break
            val pts = 4 - position
            players.forEach { out[it] = pts }
            position += players.size
        }
        return out
    }
}

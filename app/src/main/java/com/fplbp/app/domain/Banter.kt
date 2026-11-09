package com.fplbp.app.domain

/** A notification candidate. Higher score = more interesting. */
data class Banter(val title: String, val text: String, val score: Int)

object Chips {
    fun label(name: String?): String = when (name) {
        "wildcard" -> "Wildcard"
        "freehit" -> "Free Hit"
        "bboost" -> "Bench Boost"
        "3xc" -> "Triple Captain"
        "manager" -> "Assistant Manager"
        null -> ""
        else -> name
    }

    fun short(name: String): String = when (name) {
        "wildcard" -> "WC"
        "freehit" -> "FH"
        "bboost" -> "BB"
        "3xc" -> "TC"
        "manager" -> "AM"
        else -> name.take(2).uppercase()
    }
}

/** Serbian list join: "A", "A i B", "A, B i C". */
fun joinSr(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    else -> names.dropLast(1).joinToString(", ") + " i " + names.last()
}

/** "1 poen", "2 poena", "5 poena", "21 poen" */
fun poena(n: Int): String {
    val a = kotlin.math.abs(n)
    val word = if (a % 10 == 1 && a % 100 != 11) "poen" else "poena"
    return "$n $word"
}

object BanterEngine {

    // ---------- After deadline ----------

    data class DeadlineTeam(
        val name: String,
        val captain: String,
        val chip: String?,
        val transferCost: Int,
    )

    /** Combines up to three interesting facts. Returns null if nothing is worth sending. */
    fun deadlineDigest(gw: Int, teams: List<DeadlineTeam>): Banter? {
        if (teams.size < 2) return null
        val lines = mutableListOf<Pair<Int, String>>()

        val byCap = teams.groupBy { it.captain }
        val top = byCap.maxByOrNull { it.value.size }!!
        if (byCap.size == 1) {
            lines += 1 to "Svi imaju istog kapitena: ${top.key}. Nema kockanja ovog kola."
        } else if (top.value.size > 1) {
            val rebels = teams.filter { it.captain != top.key }
            val rebelText = joinSr(rebels.map { "${it.name} (${it.captain})" })
            lines += 3 to "${top.value.size} od ${teams.size} kapitena je ${top.key}. Drugačije igra: $rebelText. Kockanje?"
        } else {
            lines += 2 to "Svako ima drugog kapitena: " + joinSr(teams.map { "${it.name} (${it.captain})" }) + "."
        }

        teams.filter { it.chip != null }.forEach {
            lines += 3 to "${it.name} aktivirao ${Chips.label(it.chip)}."
        }
        teams.filter { it.transferCost >= 4 }.sortedByDescending { it.transferCost }.forEach {
            lines += (if (it.transferCost >= 8) 3 else 2) to "${it.name} uzeo -${it.transferCost}. Isplatiće se?"
        }

        val best = lines.sortedByDescending { it.first }.take(3)
        val score = best.sumOf { it.first }
        // Only "everyone has the same captain" and nothing else -> not worth a notification.
        if (best.all { it.first <= 1 }) return null
        return Banter("Kolo $gw je počelo", best.joinToString("\n") { it.second }, score)
    }

    // ---------- After a matchday ----------

    /** A counting player whose match hasn't finished yet. */
    data class Remaining(val element: Int, val name: String, val dayAccusative: String)

    data class LiveTeam(
        val name: String,
        val net: Int,
        val captain: String,
        val captainPoints: Int, // already multiplied
        val captainDone: Boolean,
        val remaining: List<Remaining> = emptyList(),
    )

    fun matchday(gw: Int, dayLabel: String, teams: List<LiveTeam>): Banter? {
        if (teams.size < 2) return null
        val sorted = teams.sortedBy { it.net }
        val last = sorted.first()
        val lastTied = sorted.filter { it.net == last.net }.toMutableList()
        var next = sorted.firstOrNull { it.net > last.net }
        val candidates = mutableListOf<Banter>()
        val title = "Kolo $gw · posle $dayLabel"

        if (isDecided(teams.map { it.net to it.remaining.isNotEmpty() })) {
            // The "practically decided" message covers this; only captain stories remain.
            lastTied.clear()
            next = null
        }
        // A close bottom is only news if one of the teams involved still has players to come.
        val stillOpen = { list: List<LiveTeam> -> list.any { it.remaining.isNotEmpty() } }
        if (lastTied.size > 1) {
            if (stillOpen(lastTied)) {
                candidates += Banter(title, "${joinSr(lastTied.map { it.name })} izjednačeni na dnu sa ${poena(last.net)}.", 4)
            }
        } else if (next != null) {
            val gap = next.net - last.net
            if (gap <= 3 && stillOpen(listOf(last, next))) {
                candidates += Banter(title, "${last.name} i ${next.name}: samo ${poena(gap)} razlike u borbi za dno.", 4)
            } else if (gap > 3) {
                candidates += Banter(title, "${last.name} na dnu sa ${poena(last.net)}, ${poena(gap)} iza ostalih.", 1)
            }
        }

        // Suspense: the bottom two differ only in players who haven't played yet.
        val rival = if (lastTied.size > 1) lastTied[1] else next
        if (rival != null && rival.net - last.net <= 12) {
            suspense(title, last, rival)?.let { candidates += it }
        }

        val majority = teams.groupBy { it.captain }.maxByOrNull { it.value.size }!!
        val isDifferential = majority.value.size > 1
        teams.filter { it.captainDone }.forEach { t ->
            val differential = isDifferential && t.captain != majority.key
            when {
                differential && t.captainPoints >= 16 ->
                    candidates += Banter(title, "${t.name}: kapiten ${t.captain} doneo ${poena(t.captainPoints)}. Kockanje se isplatilo.", 3)
                differential && t.captainPoints <= 4 ->
                    candidates += Banter(title, "${t.name}: kapiten ${t.captain} doneo samo ${poena(t.captainPoints)}. Kockanje nije prošlo.", 3)
                t.captainPoints >= 24 ->
                    candidates += Banter(title, "${t.name}: kapiten ${t.captain} doneo ${poena(t.captainPoints)}!", 2)
            }
        }
        val best = candidates.maxByOrNull { it.score } ?: return null
        return if (best.score >= 2) best else null
    }

    private fun suspense(title: String, last: LiveTeam, rival: LiveTeam): Banter? {
        val lastIds = last.remaining.map { it.element }.toSet()
        val rivalIds = rival.remaining.map { it.element }.toSet()
        val onlyLast = last.remaining.filter { it.element !in rivalIds }
        val onlyRival = rival.remaining.filter { it.element !in lastIds }
        if (onlyLast.isEmpty() && onlyRival.isEmpty()) return null
        // Same day for every deciding player -> "Sve se odlučuje u ponedeljak".
        val days = (onlyLast + onlyRival).map { it.dayAccusative }.distinct()
        if (days.size == 1 && (onlyLast.size + onlyRival.size) <= 3) {
            val day = days[0]
            fun names(l: List<Remaining>) = joinSr(l.map { it.name })
            val text = when {
                onlyRival.isEmpty() -> "Sve se odlučuje u $day: ${last.name} ima ${names(onlyLast)}, ${rival.name} nema."
                onlyLast.isEmpty() -> "Sve se odlučuje u $day: ${rival.name} ima ${names(onlyRival)}, ${last.name} nema."
                else -> "Sve se odlučuje u $day: ${last.name} ima ${names(onlyLast)}, ${rival.name} ima ${names(onlyRival)}."
            }
            return Banter(title, text, 5)
        }
        if (last.remaining.size - rival.remaining.size >= 2) {
            val tail = if (rival.remaining.isEmpty()) "${rival.name} nema nijednog." else "${rival.name} ima ${rival.remaining.size}."
            return Banter(title, "${last.name} je na dnu, ali ima još ${last.remaining.size} igrača da igraju. $tail", 4)
        }
        return null
    }

    // ---------- Practically decided ----------

    /**
     * True when the team(s) at the bottom have no players left and can't realistically be caught:
     * the next team is 3+ points ahead, or every team within 2 points has no players left either.
     * Input: (net points, still has players to come) per team.
     */
    fun isDecided(teams: List<Pair<Int, Boolean>>): Boolean {
        if (teams.size < 2) return false
        val low = teams.minOf { it.first }
        if (teams.any { it.first == low && it.second }) return false
        val close = teams.filter { it.first in (low + 1)..(low + 2) }
        return close.none { it.second }
    }

    fun decided(gw: Int, losers: List<String>, share: Rational): Banter {
        val text = if (losers.size == 1) {
            "Kolo $gw je praktično rešeno: ${losers[0]} nema više igrača i plaća ${Fmt.rsdShort(share)}."
        } else {
            "Kolo $gw je praktično rešeno: ${joinSr(losers)} nemaju više igrača i dele po ${Fmt.rsdShort(share)}."
        }
        return Banter("Kolo $gw · rešeno", "$text Konačno kad FPL potvrdi bodove.", 6)
    }

    // ---------- One match left ----------

    /** A team at the bottom and its counting players in the last remaining match. */
    data class BottomTeam(val name: String, val net: Int, val playersInMatch: List<String>)

    /**
     * Only one match of the gameweek is left (about to start or being played) and the bottom is tight:
     * tied, or within [maxGap] points with at least one of them still having a player in it.
     */
    fun lastMatch(gw: Int, match: String, teams: List<BottomTeam>, maxGap: Int = 8): Banter? {
        if (teams.size < 2) return null
        if (isDecided(teams.map { it.net to it.playersInMatch.isNotEmpty() })) return null
        val sorted = teams.sortedBy { it.net }
        val low = sorted.first().net
        val tied = sorted.filter { it.net == low }
        val involved = if (tied.size > 1) tied else sorted.filter { it.net <= sorted.first { t -> t.net > low }.net }
        val gap = if (tied.size > 1) 0 else involved.last().net - low
        val anyPlayers = involved.any { it.playersInMatch.isNotEmpty() }
        if (tied.size == 1 && (gap > maxGap || !anyPlayers)) return null

        val title = "Kolo $gw · još jedna utakmica"
        val state = if (tied.size > 1) "${joinSr(tied.map { it.name })} izjednačeni na dnu sa ${poena(low)}."
        else "${sorted.first().name} je na dnu sa ${poena(low)}, ${joinSr(involved.drop(1).map { it.name })} ${poena(involved.last().net)}."
        val who = when {
            !anyPlayers -> "Niko od njih nema igrača u njoj."
            else -> involved.joinToString(", ") { t ->
                if (t.playersInMatch.isEmpty()) "${t.name} nema nikog" else "${t.name} ima ${joinSr(t.playersInMatch)}"
            } + "."
        }
        return Banter(title, "Ostala je još samo $match. $state $who", 6)
    }

    // ---------- Change at the bottom ----------

    fun bottomChange(gw: Int, newLast: String, newNet: Int, escaped: List<String>, gap: Int?): Banter {
        val esc = if (escaped.isEmpty()) "" else " ${joinSr(escaped)} ${if (escaped.size > 1) "su se izvukli" else "se izvukao"}."
        val tail = gap?.let { " ${poena(it)} do spasa." } ?: ""
        return Banter("Kolo $gw · preokret", "$newLast je sada poslednji sa ${poena(newNet)}.$esc$tail", 5)
    }

    // ---------- Gameweek settled ----------

    data class SettleTeam(
        val entryId: Long,
        val name: String,
        val points: Int,
        val transferCost: Int,
        val benchPoints: Int,
        val chip: String?,
        val captain: String = "",
        /** Captain points incl. multiplier. */
        val captainPoints: Int = 0,
        /** Points the bench added with Bench Boost (only when chip == bboost). */
        val benchBoostPoints: Int = 0,
    ) {
        val net get() = points - transferCost
    }

    fun settled(
        gw: Int,
        teams: List<SettleTeam>,
        debt: Long,
        totalBefore: Map<Long, Rational>,
        totalAfter: Map<Long, Rational>,
        /** Previous final GWs, oldest first, as lists of loser ids. */
        previousLosers: List<List<Long>>,
        /** Lowest net score of any team in any previous final GW (null if none). */
        previousSeasonLow: Int? = null,
    ): Banter? {
        if (teams.isEmpty()) return null
        val lowest = teams.minOf { it.net }
        val losers = teams.filter { it.net == lowest }
        val share = Rational.of(debt, losers.size.toLong())
        val potBefore = totalBefore.values.sumRational()
        val pot = totalAfter.values.sumRational()
        val title = "Kolo $gw završeno"

        val main = if (losers.size == 1) {
            "${losers[0].name} duguje ${Fmt.rsdShort(share)}. Ukupno u kasi: ${Fmt.rsdShort(pot)}."
        } else {
            "Nerešeno na dnu! ${joinSr(losers.map { it.name })} dele po ${Fmt.rsdShort(share)}."
        }

        val twists = mutableListOf<Pair<Int, String>>()
        val others = teams.filter { it.net > lowest }
        val secondLowest = others.minOfOrNull { it.net }

        if (losers.size == 1 && secondLowest != null) {
            val l = losers[0]
            if (l.transferCost > 0 && l.points > secondLowest) {
                twists += 6 to "Bez -${l.transferCost} ${l.name} ne bi platio."
            }
            if (l.chip != "bboost" && l.benchPoints >= 10 && l.net + l.benchPoints > secondLowest) {
                twists += 4 to "${l.name} ostavio ${poena(l.benchPoints)} na klupi i platio."
            }
            if (secondLowest - lowest == 1) {
                val escaped = others.filter { it.net == secondLowest }
                twists += 4 to "${joinSr(escaped.map { it.name })} ${if (escaped.size > 1) "pobegli" else "pobegao"} od duga za 1 poen."
            }
            // Captain rebel: everyone else had the same captain, the loser didn't.
            val othersCaps = others.map { it.captain }.distinct()
            if (others.size >= 2 && othersCaps.size == 1 && othersCaps[0].isNotBlank() && l.captain.isNotBlank() && l.captain != othersCaps[0]) {
                twists += 5 to "Kapiten svih osim jednog: ${othersCaps[0]}. ${l.name} je izabrao drugačije (${l.captain}) i plaća."
            }
        }

        for (l in losers) {
            when (l.chip) {
                "bboost" -> if (l.benchBoostPoints <= 5)
                    twists += 6 to "${l.name} aktivirao Bench Boost i dobio ${poena(l.benchBoostPoints)} sa klupe. I platio."
                "wildcard" -> twists += 6 to "Wildcard kolo, novi tim, isti rezultat: ${l.name} plaća."
                "freehit" -> twists += 6 to "Free Hit kolo, pozajmljen tim, isti rezultat: ${l.name} plaća."
            }
        }
        // Triple Captain flop (anyone; bigger if it was the loser).
        teams.filter { it.chip == "3xc" && it.captain.isNotBlank() && it.captainPoints <= 9 }.forEach {
            twists += (if (it in losers) 6 else 3) to "${it.name}: Triple Captain (${it.captain}) doneo samo ${poena(it.captainPoints)}."
        }

        for (l in losers) {
            val everLost = previousLosers.any { l.entryId in it }
            var streak = 1
            for (prev in previousLosers.asReversed()) { if (l.entryId in prev) streak++ else break }
            var safeRun = 0
            for (prev in previousLosers.asReversed()) { if (l.entryId !in prev) safeRun++ else break }
            when {
                streak >= 3 -> twists += 5 to "${l.name} plaća ${ordinal(streak).lowercase()} kolo zaredom."
                !everLost && previousLosers.isNotEmpty() -> twists += 4 to "Prvi dug sezone za ${l.name}. Dobrodošao u klub."
                everLost && safeRun >= 5 -> twists += 4 to "Posle $safeRun kola bez duga, ${l.name} opet plaća."
            }
        }

        // Someone on a long safe run (announced at 10, 15, 20…).
        teams.filter { it !in losers }.forEach { t ->
            var run = 1
            for (prev in previousLosers.asReversed()) { if (t.entryId !in prev) run++ else break }
            if (run >= 10 && run % 5 == 0) {
                twists += 2 to "${t.name} nije platio $run kola zaredom. Neko mora da ga zaustavi."
            }
        }

        // Season record low.
        if (previousSeasonLow != null && lowest < previousSeasonLow) {
            twists += 3 to "Novo najgore kolo sezone: ${losers[0].name} sa ${poena(lowest)}."
        }

        // New leader of the debt table.
        val leaderBefore = totalBefore.maxByOrNull { it.value }?.takeIf { it.value > Rational.ZERO }?.key
        val leaderAfter = totalAfter.entries.sortedByDescending { it.value }
        if (leaderAfter.isNotEmpty() && (leaderAfter.size < 2 || leaderAfter[0].value > leaderAfter[1].value)) {
            val id = leaderAfter[0].key
            if (id != leaderBefore) {
                teams.firstOrNull { it.entryId == id }?.let { twists += 2 to "Novi lider na tabeli duga: ${it.name}." }
            }
        }

        // Personal milestones every 1.000 RSD.
        for (l in losers) {
            val before = (totalBefore[l.entryId] ?: Rational.ZERO).toDouble()
            val after = (totalAfter[l.entryId] ?: Rational.ZERO).toDouble()
            val crossed = (after / 1000).toInt()
            if (crossed > (before / 1000).toInt() && crossed >= 1) {
                twists += 2 to "${l.name} prešao ${Fmt.rsdShort(Rational.of(crossed * 1000L))} duga. Večera mu se smeši."
            }
        }

        // Pot milestones every 5.000 RSD.
        val potK = (pot.toDouble() / 5000).toInt()
        if (potK >= 1 && potK > (potBefore.toDouble() / 5000).toInt()) {
            twists += 3 to "Kasa je prešla ${Fmt.rsdShort(Rational.of(potK * 5000L))}. Večera se širi na dezert."
        }

        // Dinner share of a single loser (after enough GWs to mean something).
        if (losers.size == 1 && previousLosers.size >= 5 && pot > Rational.ZERO) {
            val pct = ((totalAfter[losers[0].entryId] ?: Rational.ZERO).toDouble() / pot.toDouble() * 100).toInt()
            if (pct >= 30) twists += 2 to "${losers[0].name} trenutno plaća $pct% večere."
        }

        // Up to two twists, never two about the same thing.
        val picked = twists.sortedByDescending { it.first }.map { it.second }.distinct().take(2)
        return Banter(title, (listOf(main) + picked).joinToString("\n"), 10)
    }

    // ---------- Half-season and season-end recaps ----------

    data class RecapRow(val name: String, val total: Rational, val timesLast: Int)

    fun recap(gw: Int, isEnd: Boolean, rows: List<RecapRow>, pot: Rational): Banter {
        val sorted = rows.sortedByDescending { it.total }
        val lines = sorted.map { r ->
            val pct = if (pot > Rational.ZERO) (r.total.toDouble() / pot.toDouble() * 100).toInt() else 0
            "${r.name}: ${Fmt.rsdShort(r.total)} ($pct%, poslednji ×${r.timesLast})"
        }
        val head = if (isEnd) "Kraj sezone! Kasa: ${Fmt.rsdShort(pot)}." else "Polusezona (kolo $gw). Kasa: ${Fmt.rsdShort(pot)}."
        val tail = sorted.firstOrNull()?.takeIf { it.total > Rational.ZERO }?.let {
            if (isEnd) "${it.name} plaća najveći deo večere. Prijatno!" else "${it.name} za sada plaća najviše."
        }
        return Banter(if (isEnd) "Kraj sezone" else "Polusezona", (listOf(head) + lines + listOfNotNull(tail)).joinToString("\n"), 20)
    }

    private fun ordinal(n: Int): String = when (n) {
        3 -> "Treće"; 4 -> "Četvrto"; 5 -> "Peto"; 6 -> "Šesto"; 7 -> "Sedmo"; 8 -> "Osmo"
        else -> "$n."
    }
}

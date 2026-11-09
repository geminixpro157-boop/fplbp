package com.fplbp.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {

    private fun s(id: Long, pts: Int, cost: Int = 0, bench: Int = 0, chip: String? = null) =
        TeamScore(id, pts, cost, bench, chip)

    @Test fun hitDecidesLoser() {
        // Example from the brief: A 50 (-4) = 46, B 48, C 66 -> A pays.
        val g = GwScores(1, true, listOf(s(1, 50, 4), s(2, 48), s(3, 66)))
        val o = DebtCalculator.outcome(g, 350)!!
        assertEquals(listOf(1L), o.losers)
        assertEquals(Rational.of(350), o.share)
        assertEquals(2, o.gapToSafety)
    }

    @Test fun splitThreeWaysHasNoDrift() {
        val teams = listOf(1L, 2L, 3L, 4L)
        val gws = (1..3).map { GwScores(it, true, listOf(s(1, 40), s(2, 40), s(3, 40), s(4, 70))) }
        val table = DebtCalculator.table(teams, gws, 350).associateBy { it.entryId }
        // 3 x 350/3 = exactly 350 each.
        assertEquals(Rational.of(350), table.getValue(1).total)
        assertEquals("350,00 RSD", Fmt.rsd(table.getValue(1).total))
        assertEquals(1050L, DebtCalculator.pot(gws, 350))
        assertEquals(3, table.getValue(1).timesLastShared)
    }

    @Test fun oneThirdRoundsToTwoDecimals() {
        val r = Rational.of(350, 3)
        assertEquals("116,67 RSD", Fmt.rsd(r))
        assertEquals("1.166,67 RSD", Fmt.rsd(r * 10))
        assertEquals("175 RSD", Fmt.rsdShort(Rational.of(350, 2) ))
        assertEquals("175 RSD", Fmt.rsdShort(Rational.of(175)))
        assertEquals("87,50 RSD", Fmt.rsdShort(Rational.of(350, 4)))
        assertEquals("3.500 RSD", Fmt.rsdShort(Rational.of(3500)))
    }

    @Test fun nonFinalIgnoredInTable() {
        val gws = listOf(GwScores(1, true, listOf(s(1, 10), s(2, 20))), GwScores(2, false, listOf(s(1, 10), s(2, 20))))
        val t = DebtCalculator.table(listOf(1, 2), gws, 350)
        assertEquals(Rational.of(350), t.first { it.entryId == 1L }.total)
    }

    @Test fun ranksWithTies() {
        val r = DebtCalculator.ranks(listOf(s(1, 60), s(2, 50), s(3, 60), s(4, 40)))
        assertEquals(1, r[1]); assertEquals(1, r[3]); assertEquals(3, r[2]); assertEquals(4, r[4])
    }

    // ---- Live engine ----

    private fun squad(): List<PickIn> {
        // 1 GK, 4 DEF (2-5), 4 MID (6-9), 2 FWD (10-11), bench: GK 12, DEF 13, MID 14, FWD 15
        return (1..15).map { PickIn(it, it, if (it == 10) 2 else if (it <= 11) 1 else 0, it == 10, it == 6) }
    }

    private fun type(e: Int) = when (e) {
        1, 12 -> Pos.GK; in 2..5, 13 -> Pos.DEF; in 6..9, 14 -> Pos.MID; else -> Pos.FWD
    }

    private fun live(overrides: Map<Int, Pair<Int, Boolean>> = emptyMap()): Map<Int, PlayerLive> =
        (1..15).associateWith { e ->
            val (min, done) = overrides[e] ?: (90 to true)
            PlayerLive(e, type(e), min, if (min > 0) 2 else 0, done)
        }

    @Test fun noSubsWhenAllPlay() {
        val r = LiveEngine.resolve(squad(), null, live())
        assertEquals(0, r.autoSubs.size)
        assertEquals(2 * 10 + 2 * 2, r.points) // 10 starters x2 + captain 2x2
    }

    @Test fun captainToVice() {
        val r = LiveEngine.resolve(squad(), null, live(mapOf(10 to (0 to true))))
        assertTrue(r.captaincyToVice)
        assertEquals(2, r.picks.first { it.element == 6 }.multiplier)
        // forward 10 replaced by first eligible bench outfield player: 13 (DEF) keeps 3-4-... valid? lineup 4 DEF 4 MID 1 FWD + DEF -> 5-4-1 valid
        assertEquals(listOf(10 to 13), r.autoSubs)
    }

    @Test fun captainNotDoneKeepsArmband() {
        val r = LiveEngine.resolve(squad(), null, live(mapOf(10 to (0 to false))))
        assertTrue(!r.captaincyToVice)
        assertEquals(0, r.autoSubs.size)
    }

    @Test fun formationRespected() {
        // Both forwards don't play; bench: 13 DEF, 14 MID, 15 FWD. First sub for 10 -> 13 ok (1 FWD left: 11?)
        // 10 and 11 both out: after replacing 10 with 13, lineup has FWD 11 only (out). Replacing 11 needs a FWD -> 15.
        val r = LiveEngine.resolve(squad(), null, live(mapOf(10 to (0 to true), 11 to (0 to true))))
        assertEquals(listOf(10 to 13, 11 to 15), r.autoSubs)
    }

    @Test fun gkOnlyForGk() {
        val r = LiveEngine.resolve(squad(), null, live(mapOf(1 to (0 to true))))
        assertEquals(listOf(1 to 12), r.autoSubs)
        val r2 = LiveEngine.resolve(squad(), null, live(mapOf(1 to (0 to true), 12 to (0 to true))))
        assertEquals(0, r2.autoSubs.size)
    }

    @Test fun benchPlayerNotPlayedSkipped() {
        val r = LiveEngine.resolve(squad(), null, live(mapOf(2 to (0 to true), 13 to (0 to false))))
        assertEquals(listOf(2 to 14), r.autoSubs)
    }

    @Test fun benchBoostCountsAll() {
        val r = LiveEngine.resolve(squad(), "bboost", live())
        assertEquals(2 * 15 + 2, r.points)
    }

    @Test fun tripleCaptain() {
        val picks = squad().map { if (it.isCaptain) it.copy(multiplier = 3) else it }
        val r = LiveEngine.resolve(picks, "3xc", live())
        assertEquals(3, r.picks.first { it.isCaptain }.multiplier)
    }

    // ---- Bonus ----

    @Test fun bonusRules() {
        assertEquals(mapOf(1 to 3, 2 to 2, 3 to 1), BonusPredictor.predict(mapOf(1 to 40, 2 to 30, 3 to 20, 4 to 10)))
        assertEquals(mapOf(1 to 3, 2 to 3, 3 to 1), BonusPredictor.predict(mapOf(1 to 40, 2 to 40, 3 to 20, 4 to 10)))
        assertEquals(mapOf(1 to 3, 2 to 2, 3 to 2), BonusPredictor.predict(mapOf(1 to 40, 2 to 30, 3 to 30, 4 to 10)))
        assertEquals(mapOf(1 to 3, 2 to 2, 3 to 1, 4 to 1), BonusPredictor.predict(mapOf(1 to 40, 2 to 30, 3 to 20, 4 to 20)))
    }

    // ---- Banter ----

    @Test fun settledMessages() {
        val teams = listOf(
            BanterEngine.SettleTeam(1, "Marko", 50, 4, 3, null),
            BanterEngine.SettleTeam(2, "Jovan", 48, 0, 2, null),
            BanterEngine.SettleTeam(3, "Petar", 66, 0, 2, null),
        )
        val b = BanterEngine.settled(11, teams, 350, mapOf(1L to Rational.ZERO), mapOf(1L to Rational.of(350)), emptyList())!!
        assertTrue(b.text, b.text.startsWith("Marko duguje 350 RSD."))
        assertTrue(b.text, b.text.contains("Bez -4 Marko ne bi platio."))

        val tie = listOf(
            BanterEngine.SettleTeam(1, "Marko", 40, 0, 0, null),
            BanterEngine.SettleTeam(2, "Jovan", 40, 0, 0, null),
            BanterEngine.SettleTeam(3, "Petar", 66, 0, 0, null),
        )
        val t = BanterEngine.settled(12, tie, 350, emptyMap(), emptyMap(), emptyList())!!
        assertTrue(t.text, t.text.startsWith("Nerešeno na dnu! Marko i Jovan dele po 175 RSD."))
    }

    @Test fun digest() {
        val d = BanterEngine.deadlineDigest(11, listOf(
            BanterEngine.DeadlineTeam("Marko", "Saka", null, 0),
            BanterEngine.DeadlineTeam("Jovan", "Haaland", "bboost", 0),
            BanterEngine.DeadlineTeam("Petar", "Haaland", null, 8),
            BanterEngine.DeadlineTeam("Ivan", "Haaland", null, 0),
        ))
        assertNotNull(d)
        assertTrue(d!!.text, d.text.contains("3 od 4 kapitena je Haaland"))
        assertNull(BanterEngine.deadlineDigest(11, listOf(
            BanterEngine.DeadlineTeam("A", "Haaland", null, 0),
            BanterEngine.DeadlineTeam("B", "Haaland", null, 0),
        )))
    }

    @Test fun poenaGrammar() {
        assertEquals("1 poen", poena(1)); assertEquals("21 poen", poena(21)); assertEquals("11 poena", poena(11)); assertEquals("4 poena", poena(4))
    }

    // ---- Who decided it ----

    private fun ep(e: Int, pts: Int, mult: Int = 1, pending: Boolean = false) =
        EffectivePick(e, e, mult, pts, mult >= 2, false, false, false, false, pending)

    @Test fun leagueDifferentials() {
        val m = mapOf(
            1L to LineupResult(listOf(ep(1, 8), ep(2, 3)), emptyList(), false),
            2L to LineupResult(listOf(ep(1, 8), ep(5, 6)), emptyList(), false),
        )
        val d = Decider.leagueDifferentials(m)
        assertEquals(listOf(2), d.getValue(1L).map { it.element })
        assertEquals(listOf(5), d.getValue(2L).map { it.element })
    }

    // ---- New banter ----

    private fun st(id: Long, name: String, pts: Int, cost: Int = 0, chip: String? = null, cap: String = "Haaland", capPts: Int = 12, bb: Int = 0) =
        BanterEngine.SettleTeam(id, name, pts, cost, 0, chip, cap, capPts, bb)

    @Test fun firstDebtAndStreaks() {
        val teams = listOf(st(1, "Marko", 30), st(2, "Jovan", 50), st(3, "Petar", 60))
        val first = BanterEngine.settled(5, teams, 350, emptyMap(), mapOf(1L to Rational.of(350)), listOf(listOf(2L), listOf(3L)))!!
        assertTrue(first.text, first.text.contains("Prvi dug sezone za Marko"))
        val third = BanterEngine.settled(5, teams, 350, emptyMap(), mapOf(1L to Rational.of(1050)), listOf(listOf(2L), listOf(1L), listOf(1L)))!!
        assertTrue(third.text, third.text.contains("Marko plaća treće kolo zaredom."))
        val broke = BanterEngine.settled(8, teams, 350, emptyMap(), mapOf(1L to Rational.of(700)),
            listOf(listOf(1L), listOf(2L), listOf(2L), listOf(3L), listOf(3L), listOf(2L)))!!
        assertTrue(broke.text, broke.text.contains("Posle 5 kola bez duga, Marko opet plaća."))
    }

    @Test fun chipFlops() {
        val bb = BanterEngine.settled(5, listOf(st(1, "Marko", 30, chip = "bboost", bb = 3), st(2, "Jovan", 50)), 350,
            emptyMap(), mapOf(1L to Rational.of(350)), listOf(listOf(2L)))!!
        assertTrue(bb.text, bb.text.contains("aktivirao Bench Boost i dobio 3 poena sa klupe. I platio."))
        val wc = BanterEngine.settled(5, listOf(st(1, "Marko", 30, chip = "wildcard"), st(2, "Jovan", 50)), 350,
            emptyMap(), mapOf(1L to Rational.of(350)), listOf(listOf(2L)))!!
        assertTrue(wc.text, wc.text.contains("Wildcard kolo"))
    }

    @Test fun captainRebelAndRecordLow() {
        val teams = listOf(st(1, "Petar", 20, cap = "Saka"), st(2, "Jovan", 50), st(3, "Ivan", 60))
        val b = BanterEngine.settled(9, teams, 350, emptyMap(), mapOf(1L to Rational.of(350)), listOf(listOf(2L), listOf(3L)), previousSeasonLow = 25)!!
        assertTrue(b.text, b.text.contains("Kapiten svih osim jednog: Haaland. Petar je izabrao drugačije (Saka) i plaća."))
        assertEquals(3, b.text.lines().size) // main + 2 twists max
    }

    @Test fun suspense() {
        val m = BanterEngine.matchday(11, "nedelje", listOf(
            BanterEngine.LiveTeam("Marko", 40, "Haaland", 10, true, listOf(BanterEngine.Remaining(7, "Salah", "ponedeljak"))),
            BanterEngine.LiveTeam("Jovan", 44, "Haaland", 10, true, emptyList()),
            BanterEngine.LiveTeam("Petar", 70, "Haaland", 10, true),
        ))!!
        assertEquals("Sve se odlučuje u ponedeljak: Marko ima Salah, Jovan nema.", m.text)
    }

    @Test fun noTightRaceWhenNobodyLeft() {
        // Bottom two 1 point apart, but neither has players left: nothing to say about the race.
        val none = BanterEngine.matchday(10, "subote", listOf(
            BanterEngine.LiveTeam("Marko", 54, "Haaland", 10, true),
            BanterEngine.LiveTeam("Jovan", 55, "Haaland", 10, true),
            BanterEngine.LiveTeam("Petar", 62, "Haaland", 10, true, listOf(BanterEngine.Remaining(9, "Mbeumo", "nedelju"))),
        ))
        assertNull(none)
        // Same scores, but Jovan still has a player to come: worth a message.
        val open = BanterEngine.matchday(10, "subote", listOf(
            BanterEngine.LiveTeam("Marko", 54, "Haaland", 10, true),
            BanterEngine.LiveTeam("Jovan", 55, "Haaland", 10, true, listOf(BanterEngine.Remaining(9, "Mbeumo", "nedelju"))),
            BanterEngine.LiveTeam("Petar", 62, "Haaland", 10, true),
        ))
        assertNotNull(open)
    }

    @Test fun decided() {
        // Baljamendi 54, nobody left; next is 55 with nobody left; others far ahead with players.
        assertTrue(BanterEngine.isDecided(listOf(54 to false, 55 to false, 62 to true, 62 to true)))
        // Next team 1 point ahead still has players (could lose points): not decided.
        assertTrue(!BanterEngine.isDecided(listOf(54 to false, 55 to true, 62 to true)))
        // Loser still has players: not decided.
        assertTrue(!BanterEngine.isDecided(listOf(54 to true, 60 to false)))
        // 3+ points clear: decided even if the others still play.
        assertTrue(BanterEngine.isDecided(listOf(50 to false, 53 to true)))
        assertEquals(
            "Kolo 10 je praktično rešeno: Baljamendi nema više igrača i plaća 350 RSD. Konačno kad FPL potvrdi bodove.",
            BanterEngine.decided(10, listOf("Baljamendi"), Rational.of(350)).text,
        )
        assertEquals(
            "Kolo 10 je praktično rešeno: Marko i Jovan nemaju više igrača i dele po 175 RSD. Konačno kad FPL potvrdi bodove.",
            BanterEngine.decided(10, listOf("Marko", "Jovan"), Rational.of(175)).text,
        )
        // Once decided, no race message in the matchday summary.
        assertNull(BanterEngine.matchday(10, "subote", listOf(
            BanterEngine.LiveTeam("Marko", 40, "Haaland", 10, true),
            BanterEngine.LiveTeam("Jovan", 41, "Haaland", 10, true),
            BanterEngine.LiveTeam("Petar", 62, "Haaland", 10, true, listOf(BanterEngine.Remaining(9, "Mbeumo", "nedelju"))),
        )))
    }

    @Test fun lastMatch() {
        val tie = BanterEngine.lastMatch(11, "LIV–MCI", listOf(
            BanterEngine.BottomTeam("Marko", 41, listOf("Salah")),
            BanterEngine.BottomTeam("Jovan", 41, emptyList()),
            BanterEngine.BottomTeam("Petar", 60, listOf("Haaland")),
        ))!!
        assertEquals("Ostala je još samo LIV–MCI. Marko i Jovan izjednačeni na dnu sa 41 poen. Marko ima Salah, Jovan nema nikog.", tie.text)
        val gap = BanterEngine.lastMatch(11, "LIV–MCI", listOf(
            BanterEngine.BottomTeam("Marko", 38, listOf("Salah", "Gakpo")),
            BanterEngine.BottomTeam("Jovan", 43, emptyList()),
            BanterEngine.BottomTeam("Petar", 60, emptyList()),
        ))!!
        assertEquals("Ostala je još samo LIV–MCI. Marko je na dnu sa 38 poena, Jovan 43 poena. Marko ima Salah i Gakpo, Jovan nema nikog.", gap.text)
        // Decided: no one at the bottom has a player in the last match.
        assertNull(BanterEngine.lastMatch(11, "LIV–MCI", listOf(
            BanterEngine.BottomTeam("Marko", 38, emptyList()), BanterEngine.BottomTeam("Jovan", 43, emptyList()),
        )))
        // Too far apart.
        assertNull(BanterEngine.lastMatch(11, "LIV–MCI", listOf(
            BanterEngine.BottomTeam("Marko", 30, listOf("Salah")), BanterEngine.BottomTeam("Jovan", 43, emptyList()),
        )))
    }

    @Test fun bottomChange() {
        val b = BanterEngine.bottomChange(11, "Jovan", 41, listOf("Marko"), 2)
        assertEquals("Jovan je sada poslednji sa 41 poen. Marko se izvukao. 2 poena do spasa.", b.text)
    }

    @Test fun recap() {
        val r = BanterEngine.recap(19, false, listOf(
            BanterEngine.RecapRow("Marko", Rational.of(2100), 6),
            BanterEngine.RecapRow("Jovan", Rational.of(4550), 13),
        ), Rational.of(6650))
        assertTrue(r.text, r.text.contains("Jovan: 4.550 RSD (68%, 13× poslednji)"))
    }

    // ---- Transfers ----

    @Test fun transferAnalysis() {
        // Team 1: GW3 Wood(20) -> Isak(10), Isak kept GW3-4, sold GW5. GW6 Free Hit (excluded).
        val pts = mapOf(
            (3 to 10) to 8, (3 to 20) to 2,
            (4 to 10) to 2, (4 to 20) to 9,
            (5 to 10) to 12, (5 to 20) to 1,
        )
        val squads = mapOf(
            3 to setOf(10, 1), 4 to setOf(10, 1), 5 to setOf(30, 1), 6 to setOf(99), 7 to setOf(30, 1),
        )
        val chips = mapOf(6 to "freehit")
        val results = TransferAnalysis.analyze(
            moves = listOf(
                TransferAnalysis.Move(1, 3, elementIn = 10, elementOut = 20),
                TransferAnalysis.Move(1, 6, elementIn = 99, elementOut = 30), // Free Hit move: ignored
            ),
            points = { gw, el -> pts[gw to el] ?: 0 },
            squad = { _, gw -> squads[gw].orEmpty() },
            chip = { _, gw -> chips[gw] },
            lastGw = 7,
        )
        assertEquals(1, results.size)
        val r = results[0]
        assertEquals(6, r.weekDiff)          // 8 - 2 in GW3
        assertEquals(listOf(3, 4), r.heldGws) // sold before GW5
        assertEquals(10 - 11, r.heldDiff)     // (8+2) - (2+9)
        val fh = TransferAnalysis.FreeHit(1, 6, points = 62, wouldHave = 48)
        val sum = TransferAnalysis.summary(1, results, hits = 4, freeHits = listOf(fh))
        assertEquals(-1, sum.heldNet)
        assertEquals(-5, sum.netAfterHits)
        assertEquals(r, sum.worst)
        assertNull(sum.best)
        val weeks = TransferAnalysis.weeks(results, mapOf((1L to 3) to 4), listOf(fh))
        assertEquals(2, weeks.size)
        assertEquals(6 - 4, weeks.first { it.gw == 3 }.net)
        assertEquals(14, weeks.first { it.gw == 6 }.net)
    }

    @Test fun heldAcrossLaterFreeHit() {
        // Player kept GW3, team plays Free Hit GW4 (player not in FH squad), player back GW5.
        val results = TransferAnalysis.analyze(
            moves = listOf(TransferAnalysis.Move(1, 3, 10, 20)),
            points = { _, _ -> 1 },
            squad = { _, gw -> if (gw == 4) setOf(99) else setOf(10) },
            chip = { _, gw -> if (gw == 4) "freehit" else null },
            lastGw = 5,
        )
        assertEquals(listOf(3, 5), results[0].heldGws)
    }

    // ---- League insights ----

    private fun lu(vararg picks: EffectivePick) = LineupResult(picks.toList(), emptyList(), false)
    private fun cp(e: Int, pts: Int, mult: Int = 1) = EffectivePick(e, e, mult, pts, mult >= 2, false, false, false, false)

    @Test fun insights() {
        val g = Insights.Gw(1, mapOf(
            1L to lu(cp(10, 2, 2), cp(11, 12), cp(12, 5)),   // captained 2, best was 12 -> lost 10
            2L to lu(cp(11, 12, 2), cp(13, 3), cp(12, 5, 0)), // captained the best -> hit; 12 on bench
        ))
        val caps = Insights.captains(listOf(g))
        assertEquals(10, caps.getValue(1).lost)
        assertEquals(0, caps.getValue(1).hits)
        assertEquals(1, caps.getValue(2).hits)
        assertEquals(24, caps.getValue(2).points)

        val own = Insights.ownShare(listOf(g))
        // Team 1 counts 10 and 12 alone (team 2 has 12 on the bench) -> 4 + 5 = 9 of 2*2+12+5 = 21
        assertEquals(9, own.getValue(1).own)
        assertEquals(21, own.getValue(1).total)

        val owned = Insights.ownership(g)
        assertEquals(listOf(11, 12), owned.map { it.element })
        assertEquals(Insights.Mark.CAPTAIN, owned[0].owners[2L])
        assertEquals(Insights.Mark.BENCH, owned[1].owners[2L])

        val byPos = Insights.pointsBy(listOf(g)) { if (it == 10) 4 else 3 }
        assertEquals(4, byPos.getValue(1)[4])
        assertEquals(17, byPos.getValue(1)[3])
    }

    @Test fun seasonStats() {
        val gws = listOf(
            GwScores(1, true, listOf(s(1, 40), s(2, 41))),
            GwScores(2, true, listOf(s(1, 40, 4), s(2, 50))),
            GwScores(3, true, listOf(s(1, 60), s(2, 50))),
        )
        val st = SeasonStats.compute(1, gws, 350)
        assertEquals(2, st.timesLast); assertEquals(2, st.longestLosingStreak); assertEquals(4, st.hitsPoints)
        val st2 = SeasonStats.compute(2, gws, 350)
        assertEquals(1 to 1, st2.closestEscape)
    }
}

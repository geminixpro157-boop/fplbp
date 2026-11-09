package com.fplbp.app.data

import androidx.room.withTransaction
import com.fplbp.app.data.api.ApiException
import com.fplbp.app.data.api.FplApi
import com.fplbp.app.data.api.FixtureDto
import com.fplbp.app.data.db.AppDatabase
import com.fplbp.app.data.db.ChipUsageEntity
import com.fplbp.app.data.db.ChipWindowEntity
import com.fplbp.app.data.db.ClubEntity
import com.fplbp.app.data.db.FixtureEntity
import com.fplbp.app.data.db.GameweekEntity
import com.fplbp.app.data.db.PickEntity
import com.fplbp.app.data.db.PlayerEntity
import com.fplbp.app.data.db.PlayerGwEntity
import com.fplbp.app.data.db.PlayerGwStatEntity
import com.fplbp.app.data.db.ProvisionalBonusEntity
import com.fplbp.app.data.db.TeamEntity
import com.fplbp.app.data.db.TeamGwEntity
import com.fplbp.app.data.db.TransferEntity
import com.fplbp.app.domain.BonusPredictor
import com.fplbp.app.domain.DebtCalculator
import com.fplbp.app.domain.LiveEngine
import com.fplbp.app.domain.PickIn
import com.fplbp.app.domain.PlayerLive
import com.fplbp.app.domain.SeasonStats
import com.fplbp.app.domain.TeamScore
import com.fplbp.app.domain.TransferAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant

sealed class SyncResult {
    data object Ok : SyncResult()
    data object Updating : SyncResult()
    data class Failed(val message: String) : SyncResult()
}

class Repository(
    private val db: AppDatabase,
    private val api: FplApi,
    val prefs: Prefs,
) {
    private val dao = db.dao()
    private val mutex = Mutex()

    private val _sync = MutableStateFlow(SyncState(updating = prefs.updatingSince > 0))
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    private val _data = MutableStateFlow<LeagueData?>(null)
    val data: StateFlow<LeagueData?> = _data.asStateFlow()

    /** Hides the error / "FPL se ažurira" banner (swiped away); a new failure shows it again. */
    fun dismissBanner() = _sync.update { it.copy(error = null, updating = false) }

    suspend fun reload() = withContext(Dispatchers.Default) { _data.value = load() }

    // ------------------------------------------------------------------ SYNC

    suspend fun sync(): SyncResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            _sync.update { it.copy(running = true, progress = 0f, message = "Povezivanje sa FPL…", error = null) }
            val result = try {
                doSync()
                prefs.updatingSince = 0
                prefs.lastSync = System.currentTimeMillis()
                _sync.update { it.copy(updating = false, error = null) }
                SyncResult.Ok
            } catch (e: ApiException.Updating) {
                if (prefs.updatingSince == 0L) prefs.updatingSince = System.currentTimeMillis()
                _sync.update { it.copy(updating = true) }
                SyncResult.Updating
            } catch (e: ApiException) {
                val msg = when (e) {
                    is ApiException.Network -> "Nema internet veze"
                    is ApiException.Http -> "FPL server ne odgovara (greška ${e.code})"
                    is ApiException.NotFound -> "Liga nije pronađena na FPL-u"
                    else -> "FPL trenutno nije dostupan"
                }
                _sync.update { it.copy(error = msg) }
                SyncResult.Failed(msg)
            } catch (e: Exception) {
                _sync.update { it.copy(error = "Neočekivana greška") }
                SyncResult.Failed(e.message ?: "Greška")
            }
            _data.value = load()
            _sync.update { it.copy(running = false, progress = 1f, message = "") }
            result
        }
    }

    private fun progress(p: Float, msg: String) = _sync.update { it.copy(progress = p, message = msg) }

    private suspend fun doSync() {
        val leagueId = prefs.leagueId
        val now = System.currentTimeMillis()

        // 1. Static data (gameweeks, clubs, players, chip windows) is ~1-2 MB, so it is fetched only
        //    once a day, when a new gameweek starts, or while a finished GW waits for FPL's confirmation.
        val stored = dao.gameweeks()
        val since = prefs.lastBootstrap
        val storedFixtures = dao.fixtures()
        val awaitingConfirm = stored.any { g ->
            g.deadline <= now && !g.finalLocal &&
                storedFixtures.filter { it.gw == g.id }.let { fx -> fx.isNotEmpty() && fx.all { it.finished || it.finishedProvisional } }
        }
        val needBootstrap = stored.isEmpty() || now - since > DAY ||
            stored.any { it.deadline in (since + 1)..now } ||
            (awaitingConfirm && now - since > 45 * MINUTE)
        if (needBootstrap) {
            val boot = api.bootstrap()
            val known = stored.associateBy { it.id }
            val rows = boot.events.map {
                GameweekEntity(
                    id = it.id,
                    name = it.name,
                    deadline = parseTime(it.deadlineTime) ?: Long.MAX_VALUE,
                    finished = it.finished,
                    dataChecked = it.dataChecked,
                    isCurrent = it.isCurrent,
                    finalLocal = known[it.id]?.finalLocal ?: false,
                )
            }
            db.withTransaction {
                dao.upsertGameweeks(rows)
                dao.upsertClubs(boot.teams.map { ClubEntity(it.id, it.name, it.shortName) })
                dao.upsertPlayers(boot.elements.map { PlayerEntity(it.id, it.webName, it.team, it.elementType) })
                if (boot.chips.isNotEmpty()) {
                    dao.clearChipWindows()
                    dao.insertChipWindows(boot.chips.map { ChipWindowEntity(it.name, it.number, it.startEvent, it.stopEvent) })
                }
            }
            prefs.lastBootstrap = now
        }
        val gws = dao.gameweeks()
        progress(0.05f, "Učitavanje lige…")

        // 2. League members.
        val members = mutableListOf<TeamEntity>()
        var page = 1
        while (page <= 20) {
            val st = api.standings(leagueId, page)
            prefs.leagueName = st.league.name
            members += st.standings.results.map { TeamEntity(it.entry, leagueId, it.entryName, it.playerName) }
            if (!st.standings.hasNext) break
            page++
            pause()
        }
        if (members.isNotEmpty()) dao.upsertTeams(members)
        val teams = dao.teams(leagueId)
        progress(0.1f, "Učitavanje istorije timova…")

        // 3. Per-team history (all GWs in one call). Final rows are never overwritten.
        val finalGws = gws.filter { it.finalLocal }.map { it.id }.toSet()
        teams.forEachIndexed { i, t ->
            pause()
            val h = api.history(t.entryId)
            val chipByGw = h.chips.associate { it.event to it.name }
            val rows = h.current.filter { it.event !in finalGws }.map {
                TeamGwEntity(t.entryId, it.event, it.points, it.transferCost, it.benchPoints, chipByGw[it.event], false)
            }
            db.withTransaction {
                dao.upsertTeamGw(rows)
                dao.insertChipUsage(h.chips.map { ChipUsageEntity(t.entryId, it.name, it.event) })
            }
            progress(0.1f + 0.15f * (i + 1) / teams.size, "Istorija: ${t.name}")
        }

        // 3b. Transfers: they only change at a deadline, so fetch once per gameweek (or daily).
        val lastDeadline = gws.filter { it.deadline <= now }.maxOfOrNull { it.deadline } ?: 0L
        val haveTransfers = dao.transfers().map { it.entryId }.toSet()
        if (prefs.lastTransfersFetch < lastDeadline || now - prefs.lastTransfersFetch > DAY ||
            teams.any { it.entryId !in haveTransfers && prefs.lastTransfersFetch == 0L }
        ) {
            for (t in teams) {
                pause()
                val list = api.transfers(t.entryId)
                db.withTransaction {
                    dao.clearTransfers(t.entryId)
                    dao.insertTransfers(list.map { TransferEntity(t.entryId, it.event, it.elementIn, it.elementOut, it.time) })
                }
            }
            prefs.lastTransfersFetch = now
        }

        // 3c. One-time backfill: confirmed GWs synced before v1.0.15 only stored owned players' points.
        gws.filter { it.finalLocal && it.deadline <= now }.forEach { gw ->
            if (dao.playerGwCount(gw.id) < 200) {
                pause()
                val live = api.live(gw.id)
                dao.insertPlayerGw(live.elements.map { PlayerGwEntity(gw.id, it.id, it.stats.minutes, it.stats.totalPoints) })
            }
        }

        // 4. Details (picks, live points, fixtures) for every started GW not yet final.
        val pending = gws.filter { it.deadline <= now && !it.finalLocal }.sortedBy { it.id }
        pending.forEachIndexed { i, gw ->
            progress(0.25f + 0.7f * i / pending.size.coerceAtLeast(1), "Kolo ${gw.id}…")
            syncGameweek(gw, teams)
        }

        // 5. Settle: FPL marked the GW finished and data-checked -> lock it forever.
        val teamGws = dao.teamGws()
        val firstGw = teamGws.groupBy { it.entryId }.mapValues { e -> e.value.minOf { it.gw } }
        for (gw in pending) {
            if (!(gw.finished && gw.dataChecked)) continue
            val expected = teams.filter { (firstGw[it.entryId] ?: Int.MAX_VALUE) <= gw.id }.map { it.entryId }.toSet()
            val have = teamGws.filter { it.gw == gw.id }.map { it.entryId }.toSet()
            if (expected.isEmpty() || !have.containsAll(expected)) continue
            // Safety: FPL's per-team history can still be a stale cache right after confirmation.
            // Settle only when it matches our live calculation, or after waiting 6 hours.
            // Only for the latest gameweek: older ones (e.g. on a fresh install) are long final.
            val isLatest = gws.none { it.id > gw.id && it.deadline <= now }
            val view = if (isLatest) load()?.gws?.firstOrNull { it.gw == gw.id } else null
            val matches = !isLatest || (view != null && view.verified.size >= expected.size && view.verified.values.all { it })
            if (!matches) {
                val waitKey = "${gw.id}:"
                if (!prefs.settleWait.startsWith(waitKey)) { prefs.settleWait = waitKey + now; continue }
                val since = prefs.settleWait.removePrefix(waitKey).toLongOrNull() ?: now
                if (now - since < 6 * 60 * MINUTE) continue
            }
            db.withTransaction {
                dao.markTeamGwFinal(gw.id)
                dao.clearProvisionalBonus(gw.id)
                dao.markFinal(gw.id)
            }
        }
        progress(1f, "Gotovo")
    }

    private suspend fun syncGameweek(gw: GameweekEntity, teams: List<TeamEntity>) {
        pause()
        val fixtures = api.fixtures(gw.id)
        pause()
        val live = api.live(gw.id)

        // Picks never change after the deadline: fetch once per team.
        val newPicks = mutableListOf<PickEntity>()
        for (t in teams) {
            if (dao.pickCount(gw.id, t.entryId) > 0) continue
            pause()
            try {
                val p = api.picks(t.entryId, gw.id)
                newPicks += p.picks.map { PickEntity(t.entryId, gw.id, it.element, it.position, it.multiplier, it.isCaptain, it.isVice) }
            } catch (e: ApiException.NotFound) {
                // Team didn't exist yet in this GW.
            }
        }

        val owned = (dao.picks().filter { it.gw == gw.id }.map { it.element } + newPicks.map { it.element }).toSet()
        val liveOwned = live.elements.filter { it.id in owned }

        // Points of every player (also ones nobody owns) - needed to judge transfers.
        val playerGw = live.elements.map { PlayerGwEntity(gw.id, it.id, it.stats.minutes, it.stats.totalPoints) }
        val stats = liveOwned.flatMap { el ->
            el.explain.flatMap { it.stats }
                .groupBy { it.identifier }
                .map { (id, list) -> PlayerGwStatEntity(gw.id, el.id, id, list.sumOf { it.points }, list.sumOf { it.value }) }
        }

        val fixtureRows = fixtures.map { it.toEntity(gw.id) }
        val confirmedBonus = liveOwned.filter { it.stats.bonus > 0 }.map { it.id }.toSet()
        val provisional = fixtures
            .filter { (it.started == true) && !it.bonusConfirmed() }
            .flatMap { f ->
                val bps = f.stats.firstOrNull { it.identifier == "bps" } ?: return@flatMap emptyList()
                BonusPredictor.predict((bps.h + bps.a).associate { it.element to it.value }).entries
            }
            .filter { it.key in owned && it.key !in confirmedBonus }
            .groupBy({ it.key }, { it.value })
            .map { (el, pts) -> ProvisionalBonusEntity(gw.id, el, pts.sum()) }

        db.withTransaction {
            if (newPicks.isNotEmpty()) dao.insertPicks(newPicks)
            dao.upsertFixtures(fixtureRows)
            dao.clearPlayerGw(gw.id)
            dao.insertPlayerGw(playerGw)
            dao.clearStats(gw.id)
            dao.insertStats(stats)
            dao.clearProvisionalBonus(gw.id)
            dao.insertProvisionalBonus(provisional)
        }
    }

    private fun FixtureDto.bonusConfirmed(): Boolean =
        stats.any { it.identifier == "bonus" && (it.h.isNotEmpty() || it.a.isNotEmpty()) }

    private fun FixtureDto.toEntity(gw: Int) = FixtureEntity(
        id = id, gw = event ?: gw, teamH = teamH, teamA = teamA, kickoff = parseTime(kickoffTime),
        started = started == true, finished = finished, finishedProvisional = finishedProvisional,
        bonusConfirmed = bonusConfirmed(),
    )

    private suspend fun pause() = delay(120)

    /** Wipes everything and starts over (e.g. after changing the league). */
    suspend fun resetAll() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        prefs.initialSyncDone = false
        prefs.lastSync = 0
        _data.value = null
    }

    // ------------------------------------------------------------------ LOAD

    suspend fun load(): LeagueData? = withContext(Dispatchers.IO) {
        val leagueId = prefs.leagueId
        val teams = dao.teams(leagueId)
        if (teams.isEmpty()) return@withContext null
        val teamIds = teams.map { it.entryId }
        val gameweeks = dao.gameweeks()
        val teamGws = dao.teamGws().filter { it.entryId in teamIds }
        val picks = dao.picks().filter { it.entryId in teamIds }
        val playerGws = dao.playerGws().associateBy { it.gw to it.element }
        val allStats = dao.stats()
        val stats = allStats.groupBy { it.gw to it.element }
        val statsByGw = allStats.groupBy { it.gw }
        val fixtures = dao.fixtures().groupBy { it.gw }
        val provBonus = dao.provisionalBonus().associateBy { it.gw to it.element }
        val players = dao.players().associateBy { it.id }
        val clubs = dao.clubs().associateBy { it.id }
        val now = System.currentTimeMillis()
        val debt = prefs.debtAmount

        val teamGwBy = teamGws.groupBy { it.gw }
        val freeHits = mutableListOf<TransferAnalysis.FreeHit>()
        val picksBy = picks.groupBy { it.gw to it.entryId }

        val views = gameweeks.filter { it.deadline <= now && (teamGwBy[it.id] != null || picks.any { p -> p.gw == it.id }) }.map { gw ->
            val gwFixtures = fixtures[gw.id].orEmpty()
            val doneClubs = HashMap<Int, Boolean>()
            fun clubDone(club: Int): Boolean = doneClubs.getOrPut(club) {
                if (gw.finished) true
                else gwFixtures.filter { it.teamH == club || it.teamA == club }.all { it.finished || it.finishedProvisional }
            }
            val allPlayed = gwFixtures.isNotEmpty() && gwFixtures.all { it.finished || it.finishedProvisional }
            val status = when {
                gw.finalLocal -> GwStatus.FINAL
                gw.finished || allPlayed -> GwStatus.AWAITING // all matches played, FPL hasn't closed the GW yet
                else -> GwStatus.LIVE
            }
            val lineups = HashMap<Long, com.fplbp.app.domain.LineupResult>()
            val verified = HashMap<Long, Boolean>()
            val scores = teams.mapNotNull { t ->
                val row = teamGwBy[gw.id]?.firstOrNull { it.entryId == t.entryId }
                val tp = picksBy[gw.id to t.entryId].orEmpty()
                fun liveOf(element: Int): PlayerLive {
                    val pl = players[element]
                    val pg = playerGws[gw.id to element]
                    val bonus = provBonus[gw.id to element]?.points ?: 0
                    return PlayerLive(
                        element = element,
                        elementType = pl?.elementType ?: 3,
                        minutes = pg?.minutes ?: 0,
                        points = (pg?.totalPoints ?: 0) + bonus,
                        done = pl?.let { clubDone(it.clubId) } ?: gw.finished,
                    )
                }
                val lineup = if (tp.isNotEmpty()) {
                    LiveEngine.resolve(
                        tp.map { PickIn(it.element, it.position, it.multiplier, it.isCaptain, it.isVice) },
                        row?.chip, tp.associate { it.element to liveOf(it.element) },
                    ).also { lineups[t.entryId] = it }
                } else null

                // Free Hit: compare with what last week's squad would have scored this week.
                val prev = picksBy[(gw.id - 1) to t.entryId].orEmpty()
                if (row?.chip == "freehit" && lineup != null && prev.isNotEmpty()) {
                    val wouldHave = LiveEngine.resolve(
                        prev.map {
                            val mult = when { it.isCaptain -> 2; it.position <= 11 -> 1; else -> 0 }
                            PickIn(it.element, it.position, mult, it.isCaptain, it.isVice)
                        },
                        null, prev.associate { it.element to liveOf(it.element) },
                    ).points
                    freeHits += TransferAnalysis.FreeHit(t.entryId, gw.id, lineup.points, wouldHave)
                }

                when {
                    row == null && lineup == null -> null
                    // Confirmed GW: FPL's official history is the source of truth.
                    status == GwStatus.FINAL && row != null -> {
                        if (lineup != null) verified[t.entryId] = lineup.points == row.points
                        TeamScore(t.entryId, row.points, row.transferCost, row.benchPoints, row.chip)
                    }
                    // Live or waiting for confirmation: FPL's history is cached and lags, so use the live calculation.
                    lineup != null -> {
                        if (row != null) verified[t.entryId] = lineup.points == row.points
                        TeamScore(t.entryId, lineup.points, row?.transferCost ?: 0, lineup.benchPoints, row?.chip)
                    }
                    else -> TeamScore(t.entryId, row!!.points, row.transferCost, row.benchPoints, row.chip)
                }
            }
            val g = com.fplbp.app.domain.GwScores(gw.id, status == GwStatus.FINAL, scores)
            GwView(
                gw = gw.id,
                name = gw.name,
                deadline = gw.deadline,
                status = status,
                scores = scores,
                outcome = DebtCalculator.outcome(g, debt),
                lineups = lineups,
                verified = verified,
                fixturesDone = gwFixtures.count { it.finished || it.finishedProvisional },
                fixturesTotal = gwFixtures.size,
                fixtures = gwFixtures,
                stats = statsByGw[gw.id].orEmpty().groupBy { it.element },
                provisionalBonus = provBonus.values.filter { it.gw == gw.id }.associate { it.element to it.points },
            )
        }

        val allScores = views.map { it.toScores() }
        val table = DebtCalculator.table(teamIds, allScores, debt)
        val (raceGws, race) = DebtCalculator.cumulative(teamIds, allScores, debt)

        // Player contributions and captains.
        val contributions = HashMap<Long, MutableMap<Int, PlayerContribution>>()
        val captains = HashMap<Long, MutableList<CaptainPick>>()
        for (v in views) for ((entry, lu) in v.lineups) {
            val map = contributions.getOrPut(entry) { HashMap() }
            for (p in lu.picks) {
                if (p.multiplier > 0) {
                    val cats = HashMap<String, Int>()
                    stats[v.gw to p.element].orEmpty().forEach { s ->
                        val c = Category.of(s.identifier)
                        cats[c] = (cats[c] ?: 0) + s.points * p.multiplier
                    }
                    provBonus[v.gw to p.element]?.let { cats["bonus"] = (cats["bonus"] ?: 0) + it.points * p.multiplier }
                    val total = p.points * p.multiplier
                    val prev = map[p.element]
                    map[p.element] = PlayerContribution(
                        element = p.element,
                        total = (prev?.total ?: 0) + total,
                        byCategory = (prev?.byCategory.orEmpty().keys + cats.keys).associateWith { (prev?.byCategory?.get(it) ?: 0) + (cats[it] ?: 0) },
                        captainExtra = (prev?.captainExtra ?: 0) + p.points * (p.multiplier - 1).coerceAtLeast(0),
                        gwsCounted = (prev?.gwsCounted ?: 0) + 1,
                    )
                }
                val wears = (p.isCaptain && !lu.captaincyToVice) || p.captaincyPassed
                if (wears) {
                    captains.getOrPut(entry) { mutableListOf() } +=
                        CaptainPick(v.gw, p.element, p.points * p.multiplier.coerceAtLeast(1), p.captaincyPassed, v.isFinal)
                }
            }
        }

        // Chips grid.
        val windows = dao.chipWindows()
        val usage = dao.chipUsage().filter { it.entryId in teamIds }
        val currentGw = gameweeks.firstOrNull { it.isCurrent }?.id ?: views.lastOrNull()?.gw
        val grid = teamIds.associateWith { id ->
            windows.map { w ->
                val used = usage.firstOrNull { it.entryId == id && it.name == w.name && it.gw in w.startEvent..w.stopEvent }?.gw
                val lastStarted = views.lastOrNull()?.gw ?: 0
                ChipCell(w, used, used == null && lastStarted > w.stopEvent)
            }
        }

        // Transfers: raw player points, same gameweek and while the player was kept.
        val squads = picks.groupBy { it.entryId to it.gw }.mapValues { e -> e.value.map { it.element }.toSet() }
        val chipOf = teamGws.associate { (it.entryId to it.gw) to it.chip }
        fun rawPoints(gw: Int, element: Int) =
            (playerGws[gw to element]?.totalPoints ?: 0) + (provBonus[gw to element]?.points ?: 0)
        val transferResults = TransferAnalysis.analyze(
            moves = dao.transfers().filter { it.entryId in teamIds }
                .map { TransferAnalysis.Move(it.entryId, it.gw, it.elementIn, it.elementOut) },
            points = ::rawPoints,
            squad = { e, g -> squads[e to g].orEmpty() },
            chip = { e, g -> chipOf[e to g] },
            lastGw = views.lastOrNull()?.gw ?: 0,
        )
        val hitByTeamGw = views.flatMap { v -> v.scores.map { (it.entryId to v.gw) to it.transferCost } }.toMap()

        LeagueData(
            leagueId = leagueId,
            leagueName = prefs.leagueName,
            debt = debt,
            teams = teams,
            clubs = clubs,
            players = players,
            gws = views,
            debtTable = table,
            pot = DebtCalculator.pot(allScores, debt),
            finalCount = views.count { it.isFinal },
            raceGws = raceGws,
            raceSeries = race.mapValues { e -> e.value.map { it.toDouble() } },
            contributions = contributions.mapValues { it.value.values.sortedByDescending { c -> c.total } },
            captains = captains.mapValues { e -> e.value.sortedBy { it.gw } },
            chipWindows = windows,
            chipGrid = grid,
            seasonStats = teamIds.associateWith { SeasonStats.compute(it, allScores, debt) },
            transfers = transferResults,
            transferSummary = teamIds.associateWith { id ->
                TransferAnalysis.summary(id, transferResults, hitByTeamGw.filterKeys { it.first == id }.values.sum(), freeHits)
            },
            transferWeeks = TransferAnalysis.weeks(transferResults, hitByTeamGw, freeHits),
            currentGw = currentGw,
            lastSync = prefs.lastSync,
        )
    }

    companion object {
        private const val MINUTE = 60_000L
        private const val DAY = 24 * 60 * MINUTE

        fun parseTime(s: String?): Long? = s?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    }
}

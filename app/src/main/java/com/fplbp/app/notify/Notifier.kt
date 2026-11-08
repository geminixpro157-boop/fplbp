package com.fplbp.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fplbp.app.MainActivity
import com.fplbp.app.R
import com.fplbp.app.data.GwStatus
import com.fplbp.app.data.GwView
import com.fplbp.app.data.LeagueData
import com.fplbp.app.data.PlayState
import com.fplbp.app.data.Prefs
import com.fplbp.app.data.db.AppDatabase
import com.fplbp.app.data.db.FixtureEntity
import com.fplbp.app.data.db.NotifLogEntity
import com.fplbp.app.domain.Banter
import com.fplbp.app.domain.BanterEngine
import com.fplbp.app.domain.DebtCalculator
import com.fplbp.app.domain.Fmt
import com.fplbp.app.domain.sumRational
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Decides which banter to send. At most 3 notifications per day (the settlement and the
 * season recaps are always sent). Nothing interesting -> nothing sent.
 *
 * Per gameweek: deadline digest; during matches: "one match left" when the bottom is tight,
 * a change at the bottom (max once a day), a summary after each matchday; the settlement.
 */
class Notifier(private val context: Context, private val db: AppDatabase, private val prefs: Prefs) {

    private val dao = db.dao()
    private val zone: ZoneId = ZoneId.systemDefault()

    suspend fun evaluate(data: LeagueData) {
        val silent = !prefs.initialSyncDone
        try {
            settlement(data, silent)
            val live = data.gws.lastOrNull { it.status != GwStatus.FINAL } ?: return
            deadlineDigest(data, live, silent)
            if (live.status == GwStatus.LIVE) {
                decided(data, live, silent)
                lastMatch(data, live, silent)
                bottomChange(data, live, silent)
                matchdaySummary(data, live, silent)
            }
        } finally {
            if (silent) prefs.initialSyncDone = true
        }
    }

    private fun names(data: LeagueData) = data.teams.associate { it.entryId to Fmt.firstName(it.manager).ifBlank { it.name } }

    private suspend fun settlement(data: LeagueData, silent: Boolean) {
        val names = names(data)
        // Only the most recent final GW is announced; older ones are logged silently.
        val finals = data.gws.filter { it.status == GwStatus.FINAL }
        finals.forEach { v ->
            val key = "settle-${v.gw}"
            if (dao.notifSent(key) > 0) return@forEach
            val isLatest = v == finals.last()
            if (silent || !isLatest || !prefs.notifSettle) { log(key); return@forEach }
            if (quietHours()) return@forEach
            val idx = finals.indexOf(v)
            val before = DebtCalculator.table(data.teams.map { it.entryId }, finals.take(idx).map { it.toScores() }, data.debt)
                .associate { it.entryId to it.total }
            val after = DebtCalculator.table(data.teams.map { it.entryId }, finals.take(idx + 1).map { it.toScores() }, data.debt)
                .associate { it.entryId to it.total }
            val prevLosers = finals.take(idx).map { it.outcome?.losers.orEmpty() }
            val seasonLow = if (idx >= 2) finals.take(idx).flatMap { it.scores }.minOfOrNull { it.net } else null
            val banter = BanterEngine.settled(
                v.gw,
                v.scores.map { s ->
                    val lu = v.lineups[s.entryId]
                    val armband = lu?.picks?.firstOrNull { (it.isCaptain && lu?.captaincyToVice != true) || it.captaincyPassed }
                    BanterEngine.SettleTeam(
                        entryId = s.entryId,
                        name = names[s.entryId] ?: "?",
                        points = s.points,
                        transferCost = s.transferCost,
                        benchPoints = s.benchPoints,
                        chip = s.chip,
                        captain = armband?.let { data.playerName(it.element) } ?: "",
                        captainPoints = armband?.total ?: 0,
                        benchBoostPoints = if (s.chip == "bboost") lu?.picks?.filter { it.position > 11 }?.sumOf { it.total } ?: 0 else 0,
                    )
                },
                data.debt, before, after, prevLosers, seasonLow,
            )
            if (banter != null) post(banter, 1000 + v.gw, force = true)
            log(key)

            // Half-season and season-end recaps (extra, twice a season).
            val recapKey = when (v.gw) { 19 -> "recap-19"; LAST_GW -> "recap-end"; else -> null }
            if (recapKey != null && dao.notifSent(recapKey) == 0) {
                val table = DebtCalculator.table(data.teams.map { it.entryId }, finals.take(idx + 1).map { it.toScores() }, data.debt)
                val rows = table.map { BanterEngine.RecapRow(names[it.entryId] ?: "?", it.total, it.timesLast) }
                post(BanterEngine.recap(v.gw, v.gw == LAST_GW, rows, rows.map { it.total }.sumRational()), 4000 + v.gw, force = true)
                log(recapKey)
            }
        }

    }

    private suspend fun deadlineDigest(data: LeagueData, live: GwView, silent: Boolean) {
        val key = "dl-${live.gw}"
        if (dao.notifSent(key) > 0 || live.lineups.size < data.teams.size || live.status != GwStatus.LIVE) return
        if (silent || !prefs.notifDeadline) { log(key); return }
        if (quietHours()) return
        val names = names(data)
        val teams = data.teams.mapNotNull { t ->
            val lu = live.lineups[t.entryId] ?: return@mapNotNull null
            val cap = lu.picks.firstOrNull { it.isCaptain } ?: return@mapNotNull null
            val score = live.scores.firstOrNull { it.entryId == t.entryId }
            BanterEngine.DeadlineTeam(names[t.entryId] ?: t.name, data.playerName(cap.element), score?.chip, score?.transferCost ?: 0)
        }
        BanterEngine.deadlineDigest(live.gw, teams)?.let { post(it, 2000 + live.gw) }
        log(key)
    }

    /** One match of the gameweek left (starting within 30 min or being played) and a tight bottom. */
    private suspend fun lastMatch(data: LeagueData, live: GwView, silent: Boolean) {
        val key = "last-${live.gw}"
        if (dao.notifSent(key) > 0) return
        val open = live.fixtures.filter { !it.finished && !it.finishedProvisional }
        if (open.size != 1) return
        val f = open[0]
        val now = System.currentTimeMillis()
        if (!f.started && (f.kickoff ?: Long.MAX_VALUE) - now > 30 * 60_000L) return
        if (silent || !prefs.notifMatchday) { log(key); return }
        if (quietHours()) return
        val names = names(data)
        val clubs = setOf(f.teamH, f.teamA)
        val teams = live.scores.mapNotNull { s ->
            val lu = live.lineups[s.entryId] ?: return@mapNotNull null
            val inMatch = lu.picks.filter { it.multiplier > 0 && data.players[it.element]?.clubId in clubs }
                .map { data.playerName(it.element) }
            BanterEngine.BottomTeam(names[s.entryId] ?: "?", s.net, inMatch)
        }
        val label = "${data.clubs[f.teamH]?.shortName ?: "?"}–${data.clubs[f.teamA]?.shortName ?: "?"}"
        val b = BanterEngine.lastMatch(live.gw, label, teams) ?: return // not tight (yet): check again later
        if (post(b, 3500 + live.gw)) log(key)
    }

    /** The team at the bottom changed during today's matches. At most once a day. */
    private suspend fun bottomChange(data: LeagueData, live: GwView, silent: Boolean) {
        val o = live.outcome ?: return
        val ids = o.losers.sorted()
        val state = "${live.gw}:${ids.joinToString(",")}"
        val previous = prefs.bottomState
        prefs.bottomState = state
        if (silent || !prefs.notifMatchday || quietHours()) return
        if (!previous.startsWith("${live.gw}:") || previous == state || ids.size != 1) return
        val prevIds = previous.substringAfter(':').split(',').mapNotNull { it.toLongOrNull() }
        if (prevIds.isEmpty()) return
        val today = LocalDate.now(zone)
        if (live.fixtures.none { it.started && it.kickoff != null && day(it) == today }) return
        val key = "flip-${live.gw}-$today"
        if (dao.notifSent(key) > 0) return
        val names = names(data)
        val b = BanterEngine.bottomChange(
            live.gw, names[ids[0]] ?: "?", o.lowestNet,
            prevIds.filter { it !in ids }.map { names[it] ?: "?" }, o.gapToSafety,
        )
        if (post(b, 3200 + live.gw)) log(key)
    }

    /** Each team's live state: net points, captain, and counting players still to play. */
    private fun liveTeams(data: LeagueData, live: GwView): Map<Long, BanterEngine.LiveTeam> {
        val names = names(data)
        return data.teams.mapNotNull { t ->
            val lu = live.lineups[t.entryId] ?: return@mapNotNull null
            val score = live.scores.firstOrNull { it.entryId == t.entryId } ?: return@mapNotNull null
            val cap = lu.picks.firstOrNull { (it.isCaptain && !lu.captaincyToVice) || it.captaincyPassed }
                ?: lu.picks.firstOrNull { it.isCaptain } ?: return@mapNotNull null
            val capDone = live.clubState(data.players[cap.element]?.clubId).state.let { it == PlayState.DONE || it == PlayState.BLANK }
            val remaining = lu.picks.filter { it.multiplier > 0 }.mapNotNull { p ->
                val st = live.clubState(data.players[p.element]?.clubId)
                if (st.state != PlayState.UPCOMING && st.state != PlayState.LIVE) return@mapNotNull null
                // Playing right now counts as still to come (kickoff = today).
                val k = st.nextKickoff ?: System.currentTimeMillis()
                BanterEngine.Remaining(p.element, data.playerName(p.element), dayAccusative(Instant.ofEpochMilli(k).atZone(zone).toLocalDate()))
            }
            t.entryId to BanterEngine.LiveTeam(names[t.entryId] ?: t.name, score.net, data.playerName(cap.element), cap.total, capDone, remaining)
        }.toMap()
    }

    /** Once per gameweek: the bottom team(s) have no players left and can't be caught anymore. */
    private suspend fun decided(data: LeagueData, live: GwView, silent: Boolean) {
        val key = "decided-${live.gw}"
        if (dao.notifSent(key) > 0) return
        val o = live.outcome ?: return
        if (live.fixtures.none { !it.finished && !it.finishedProvisional }) return // settlement comes soon anyway
        val teams = liveTeams(data, live)
        if (teams.size < data.teams.size) return
        if (!BanterEngine.isDecided(teams.values.map { it.net to it.remaining.isNotEmpty() })) return
        if (silent || !prefs.notifMatchday) { log(key); return }
        if (quietHours()) return
        val b = BanterEngine.decided(live.gw, o.losers.mapNotNull { teams[it]?.name }, o.share)
        if (post(b, 3700 + live.gw)) log(key)
    }

    /** After each completed matchday except the last one of the gameweek (the settlement covers that). */
    private suspend fun matchdaySummary(data: LeagueData, live: GwView, silent: Boolean) {
        val fixtures = live.fixtures.filter { it.kickoff != null }
        if (fixtures.isEmpty()) return
        val days = fixtures.groupBy { day(it) }.toSortedMap()
        val lastDay = days.lastKey()
        val completed = days.filter { (d, fx) -> d != lastDay && fx.all { it.finished || it.finishedProvisional } }.keys.sorted()
        if (completed.isEmpty()) return
        // Older completed days are never announced late.
        completed.dropLast(1).forEach { log("md-${live.gw}-$it") }
        val d = completed.last()
        val key = "md-${live.gw}-$d"
        if (dao.notifSent(key) > 0) return
        if (silent || !prefs.notifMatchday) { log(key); return }
        if (quietHours()) return
        val teams = liveTeams(data, live).values.toList()
        BanterEngine.matchday(live.gw, dayGenitive(d), teams)?.let { post(it, 3000 + live.gw) }
        log(key)
    }

    private fun day(f: FixtureEntity): LocalDate = Instant.ofEpochMilli(f.kickoff!!).atZone(zone).toLocalDate()

    private fun dayGenitive(d: LocalDate): String = when (d.dayOfWeek.value) {
        1 -> "ponedeljka"; 2 -> "utorka"; 3 -> "srede"; 4 -> "četvrtka"; 5 -> "petka"; 6 -> "subote"; else -> "nedelje"
    }

    private fun dayAccusative(d: LocalDate): String = when (d.dayOfWeek.value) {
        1 -> "ponedeljak"; 2 -> "utorak"; 3 -> "sredu"; 4 -> "četvrtak"; 5 -> "petak"; 6 -> "subotu"; else -> "nedelju"
    }

    private fun quietHours(): Boolean {
        val h = java.time.LocalTime.now(zone).hour
        return h >= 23 || h < 8
    }

    private suspend fun log(key: String) = dao.logNotif(NotifLogEntity(key, System.currentTimeMillis()))

    /** Resets the counter on a new day; returns how many notifications may still be sent today. */
    private fun capLeft(): Int {
        val today = LocalDate.now(zone).toString()
        if (prefs.notifDay != today) { prefs.notifDay = today; prefs.notifCount = 0 }
        return MAX_PER_DAY - prefs.notifCount
    }

    /** Sends unless today's cap is used up ([force] for the settlement and recaps). Returns true if sent. */
    private fun post(b: Banter, id: Int, force: Boolean = false): Boolean {
        if (!force && capLeft() <= 0) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_coin)
            .setContentTitle(b.title)
            .setContentText(b.text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(b.text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(id, n)
            capLeft()
            prefs.notifCount = prefs.notifCount + 1
            true
        } catch (_: SecurityException) { false }
    }

    companion object {
        const val CHANNEL = "liga"
        const val LAST_GW = 38
        const val MAX_PER_DAY = 3

        fun createChannel(context: Context) {
            val ch = NotificationChannel(CHANNEL, "Liga i dug", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Ko duguje, kockanja kapitena i borba za dno"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}

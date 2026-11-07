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
import com.fplbp.app.data.LeagueData
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
 * Decides which banter to send. At most three per gameweek:
 * deadline digest, one matchday update, and the settlement. Nothing interesting -> nothing sent.
 */
class Notifier(private val context: Context, private val db: AppDatabase, private val prefs: Prefs) {

    private val dao = db.dao()
    private val zone: ZoneId = ZoneId.systemDefault()

    suspend fun evaluate(data: LeagueData) {
        val silent = !prefs.initialSyncDone
        val names = data.teams.associate { it.entryId to Fmt.firstName(it.manager).ifBlank { it.name } }

        // ---- Settlement: only the most recent final GW is announced; older ones are logged silently.
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
            if (banter != null) post(banter, 1000 + v.gw)
            log(key)

            // Half-season and season-end recaps (extra, twice a season).
            val recapKey = when (v.gw) { 19 -> "recap-19"; LAST_GW -> "recap-end"; else -> null }
            if (recapKey != null && dao.notifSent(recapKey) == 0) {
                val table = DebtCalculator.table(data.teams.map { it.entryId }, finals.take(idx + 1).map { it.toScores() }, data.debt)
                val rows = table.map { BanterEngine.RecapRow(names[it.entryId] ?: "?", it.total, it.timesLast) }
                post(BanterEngine.recap(v.gw, v.gw == LAST_GW, rows, rows.map { it.total }.sumRational()), 4000 + v.gw)
                log(recapKey)
            }
        }

        val live = data.gws.lastOrNull { it.status != GwStatus.FINAL } ?: return

        // ---- Deadline digest for the current GW.
        val dlKey = "dl-${live.gw}"
        if (dao.notifSent(dlKey) == 0 && live.lineups.size == data.teams.size && live.status == GwStatus.LIVE) {
            if (silent || !prefs.notifDeadline) log(dlKey)
            else if (!quietHours()) {
                val teams = data.teams.mapNotNull { t ->
                    val lu = live.lineups[t.entryId] ?: return@mapNotNull null
                    val cap = lu.picks.firstOrNull { it.isCaptain } ?: return@mapNotNull null
                    val score = live.scores.firstOrNull { it.entryId == t.entryId }
                    BanterEngine.DeadlineTeam(names[t.entryId] ?: t.name, data.playerName(cap.element), score?.chip, score?.transferCost ?: 0)
                }
                BanterEngine.deadlineDigest(live.gw, teams)?.let { post(it, 2000 + live.gw) }
                log(dlKey)
            }
        }

        // ---- One matchday update per GW, after a completed matchday that isn't the last one.
        val mdKey = "md-${live.gw}"
        if (dao.notifSent(mdKey) == 0 && live.status == GwStatus.LIVE) {
            val fixtures = dao.fixtures().filter { it.gw == live.gw && it.kickoff != null }
            val days = fixtures.groupBy { day(it) }.toSortedMap()
            val completed = days.filter { (_, fx) -> fx.all { it.finished || it.finishedProvisional } }.keys
            val lastDay = if (days.isEmpty()) null else days.lastKey()
            val day = completed.filter { it != lastDay }.maxOrNull()
            if (day != null) {
                val dayKey = "$mdKey-$day"
                if (dao.notifSent(dayKey) == 0) {
                    if (silent || !prefs.notifMatchday) log(mdKey)
                    else if (!quietHours()) {
                        val teams = data.teams.mapNotNull { t ->
                            val lu = live.lineups[t.entryId] ?: return@mapNotNull null
                            val score = live.scores.firstOrNull { it.entryId == t.entryId } ?: return@mapNotNull null
                            val cap = lu.picks.firstOrNull { (it.isCaptain && !lu.captaincyToVice) || it.captaincyPassed }
                                ?: lu.picks.first { it.isCaptain }
                            val capClubDone = data.players[cap.element]?.clubId?.let { club ->
                                fixtures.filter { it.teamH == club || it.teamA == club }.all { it.finished || it.finishedProvisional }
                            } ?: false
                            val remaining = lu.picks.filter { it.multiplier > 0 && it.pending }.mapNotNull { p ->
                                val club = data.players[p.element]?.clubId ?: return@mapNotNull null
                                val next = fixtures.filter { (it.teamH == club || it.teamA == club) && !it.finished && !it.finishedProvisional }
                                    .minByOrNull { it.kickoff!! } ?: return@mapNotNull null
                                BanterEngine.Remaining(p.element, data.playerName(p.element), dayAccusative(day(next)))
                            }
                            BanterEngine.LiveTeam(names[t.entryId] ?: t.name, score.net, data.playerName(cap.element), cap.total, capClubDone, remaining)
                        }
                        val b = BanterEngine.matchday(live.gw, dayGenitive(day), teams)
                        if (b != null) { post(b, 3000 + live.gw); log(mdKey) }
                        log(dayKey)
                    }
                }
            }
        }
        if (silent) prefs.initialSyncDone = true
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

    private fun post(b: Banter, id: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
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
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (_: SecurityException) {}
    }

    companion object {
        const val CHANNEL = "liga"
        const val LAST_GW = 38

        fun createChannel(context: Context) {
            val ch = NotificationChannel(CHANNEL, "Liga i dug", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Ko duguje, kockanja kapitena i borba za dno"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}

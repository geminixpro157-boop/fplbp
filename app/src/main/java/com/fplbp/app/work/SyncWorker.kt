package com.fplbp.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fplbp.app.FplApp
import com.fplbp.app.data.GwStatus
import com.fplbp.app.data.SyncResult
import java.util.concurrent.TimeUnit

/**
 * Runs every 30 minutes but only talks to FPL when something can have changed:
 * during matches, while a finished GW waits for FPL's final check, or every 6 hours otherwise.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as FplApp
        val repo = app.repository
        val prefs = repo.prefs
        val data = repo.data.value ?: repo.load()
        val now = System.currentTimeMillis()
        val age = now - prefs.lastSync

        val fixtures = app.database.dao().fixtures()
        val liveGw = data?.gws?.lastOrNull { it.status != GwStatus.FINAL }
        val matchWindow = fixtures.any { f ->
            val k = f.kickoff ?: return@any false
            (now in (k - 5 * MIN)..(k + 150 * MIN)) || (f.started && !f.finished && !f.finishedProvisional) ||
                (f.finishedProvisional && !f.bonusConfirmed && now - k < 8 * HOUR)
        }
        val justAfterDeadline = data?.gws?.lastOrNull()?.let { now - it.deadline in 0..(4 * HOUR) } ?: false
        val awaitingFinal = liveGw?.status == GwStatus.AWAITING
        val nextDeadlinePassed = app.database.dao().gameweeks().any { it.deadline in (prefs.lastSync + 1)..now }

        val need = data == null || age > 6 * HOUR ||
            (prefs.updatingSince > 0 && age > 15 * MIN) ||
            ((matchWindow || justAfterDeadline || nextDeadlinePassed) && age > 25 * MIN) ||
            (awaitingFinal && age > 55 * MIN)

        if (need) {
            val r = repo.sync()
            if (r is SyncResult.Failed && runAttemptCount < 2) return Result.retry()
        }
        repo.data.value?.let { app.notifier.evaluate(it) }
        return Result.success()
    }

    companion object {
        private const val MIN = 60_000L
        private const val HOUR = 60 * MIN
        private const val NAME = "fpl-sync"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

package com.fplbp.app

import android.app.Application
import com.fplbp.app.data.Prefs
import com.fplbp.app.data.Repository
import com.fplbp.app.data.api.FplApi
import com.fplbp.app.data.db.AppDatabase
import com.fplbp.app.notify.Notifier
import com.fplbp.app.update.UpdateChecker
import com.fplbp.app.work.SyncWorker

class FplApp : Application() {
    lateinit var database: AppDatabase
    lateinit var prefs: Prefs
    lateinit var api: FplApi
    lateinit var repository: Repository
    lateinit var notifier: Notifier
    lateinit var updates: UpdateChecker

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.create(this)
        prefs = Prefs(this)
        api = FplApi()
        repository = Repository(database, api, prefs)
        notifier = Notifier(this, database, prefs)
        updates = UpdateChecker(api, prefs)
        Notifier.createChannel(this)
        SyncWorker.schedule(this)
    }
}

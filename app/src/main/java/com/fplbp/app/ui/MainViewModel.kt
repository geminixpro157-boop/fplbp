package com.fplbp.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fplbp.app.FplApp
import com.fplbp.app.data.LeagueData
import com.fplbp.app.data.SyncState
import com.fplbp.app.update.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FplApp
    val repo = app.repository
    val prefs = app.prefs

    val data: StateFlow<LeagueData?> = repo.data
    val sync: StateFlow<SyncState> = repo.sync
    val update = MutableStateFlow<UpdateChecker.Update?>(app.updates.available())
    val loaded = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            repo.reload()
            loaded.value = true
            val stale = System.currentTimeMillis() - prefs.lastSync > 5 * 60_000L
            if (stale) refresh()
            update.value = app.updates.check()
        }
    }

    fun refresh() {
        if (sync.value.running) return
        viewModelScope.launch {
            repo.sync()
            repo.data.value?.let { app.notifier.evaluate(it) }
        }
    }

    fun checkUpdate() = viewModelScope.launch { update.value = app.updates.check(force = true) }

    fun changeLeague(id: Long) = viewModelScope.launch {
        if (id == prefs.leagueId) return@launch
        prefs.leagueId = id
        repo.resetAll()
        refresh()
    }

    fun changeDebt(amount: Long) = viewModelScope.launch {
        prefs.debtAmount = amount
        repo.reload()
    }

    fun resetData() = viewModelScope.launch {
        repo.resetAll()
        refresh()
    }
}

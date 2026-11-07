package com.fplbp.app.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("fplbp", Context.MODE_PRIVATE)

    var leagueId: Long
        get() = sp.getLong("leagueId", DEFAULT_LEAGUE)
        set(v) = sp.edit().putLong("leagueId", v).apply()

    var leagueName: String
        get() = sp.getString("leagueName", "FPLBP") ?: "FPLBP"
        set(v) = sp.edit().putString("leagueName", v).apply()

    var debtAmount: Long
        get() = sp.getLong("debt", DEFAULT_DEBT)
        set(v) = sp.edit().putLong("debt", v).apply()

    var lastSync: Long
        get() = sp.getLong("lastSync", 0)
        set(v) = sp.edit().putLong("lastSync", v).apply()

    /** Time FPL last answered "game is being updated" (0 = not currently). */
    var updatingSince: Long
        get() = sp.getLong("updatingSince", 0)
        set(v) = sp.edit().putLong("updatingSince", v).apply()

    var initialSyncDone: Boolean
        get() = sp.getBoolean("initialSyncDone", false)
        set(v) = sp.edit().putBoolean("initialSyncDone", v).apply()

    var notifSettle: Boolean
        get() = sp.getBoolean("notifSettle", true)
        set(v) = sp.edit().putBoolean("notifSettle", v).apply()

    var notifDeadline: Boolean
        get() = sp.getBoolean("notifDeadline", true)
        set(v) = sp.edit().putBoolean("notifDeadline", v).apply()

    var notifMatchday: Boolean
        get() = sp.getBoolean("notifMatchday", true)
        set(v) = sp.edit().putBoolean("notifMatchday", v).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0)
        set(v) = sp.edit().putLong("lastUpdateCheck", v).apply()

    var latestVersionCode: Int
        get() = sp.getInt("latestVersionCode", 0)
        set(v) = sp.edit().putInt("latestVersionCode", v).apply()

    var latestReleaseUrl: String
        get() = sp.getString("latestReleaseUrl", "") ?: ""
        set(v) = sp.edit().putString("latestReleaseUrl", v).apply()

    var batteryHintDismissed: Boolean
        get() = sp.getBoolean("batteryHintDismissed", false)
        set(v) = sp.edit().putBoolean("batteryHintDismissed", v).apply()

    companion object {
        const val DEFAULT_LEAGUE = 68740L
        const val DEFAULT_DEBT = 350L
    }
}

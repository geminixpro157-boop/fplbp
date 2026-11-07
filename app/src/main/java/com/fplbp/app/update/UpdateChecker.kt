package com.fplbp.app.update

import com.fplbp.app.BuildConfig
import com.fplbp.app.data.Prefs
import com.fplbp.app.data.api.FplApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Checks GitHub Releases (public repo) for a newer APK. Tags look like v1.0.<build number>. */
class UpdateChecker(private val api: FplApi, private val prefs: Prefs) {

    data class Update(val versionCode: Int, val url: String)

    fun available(): Update? =
        if (prefs.latestVersionCode > BuildConfig.VERSION_CODE && prefs.latestReleaseUrl.isNotBlank())
            Update(prefs.latestVersionCode, prefs.latestReleaseUrl) else null

    suspend fun check(force: Boolean = false): Update? = withContext(Dispatchers.IO) {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isBlank()) return@withContext null
        if (!force && System.currentTimeMillis() - prefs.lastUpdateCheck < 12 * 3600_000L) return@withContext available()
        runCatching {
            val rel = api.latestRelease(repo)
            val code = rel.tagName.substringAfterLast('.').filter { it.isDigit() }.toIntOrNull() ?: 0
            val apk = rel.assets.firstOrNull { it.name.endsWith(".apk") }?.url ?: rel.htmlUrl
            prefs.latestVersionCode = code
            prefs.latestReleaseUrl = apk
            prefs.lastUpdateCheck = System.currentTimeMillis()
        }
        available()
    }
}

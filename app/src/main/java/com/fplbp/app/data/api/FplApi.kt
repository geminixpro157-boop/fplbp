package com.fplbp.app.data.api

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

// ---------- Errors ----------

sealed class ApiException(msg: String) : IOException(msg) {
    /** FPL shows "The game is being updated" (usually ~1h after a deadline and at GW end). */
    class Updating : ApiException("FPL se ažurira")
    class Network(cause: Throwable) : ApiException(cause.message ?: "Nema mreže")
    class Http(val code: Int) : ApiException("HTTP $code")
    class NotFound : ApiException("Nije pronađeno")
}

// ---------- DTOs ----------

@Serializable data class BootstrapDto(
    val events: List<EventDto> = emptyList(),
    val teams: List<ClubDto> = emptyList(),
    val elements: List<ElementDto> = emptyList(),
    val chips: List<ChipDto> = emptyList(),
)

@Serializable data class EventDto(
    val id: Int,
    val name: String = "",
    @SerialName("deadline_time") val deadlineTime: String? = null,
    val finished: Boolean = false,
    @SerialName("data_checked") val dataChecked: Boolean = false,
    @SerialName("is_current") val isCurrent: Boolean = false,
    @SerialName("is_next") val isNext: Boolean = false,
)

@Serializable data class ClubDto(val id: Int, val name: String, @SerialName("short_name") val shortName: String)

@Serializable data class ElementDto(
    val id: Int,
    @SerialName("web_name") val webName: String,
    val team: Int,
    @SerialName("element_type") val elementType: Int,
)

@Serializable data class ChipDto(
    val id: Int = 0,
    val name: String,
    val number: Int = 1,
    @SerialName("start_event") val startEvent: Int = 1,
    @SerialName("stop_event") val stopEvent: Int = 38,
)

@Serializable data class StandingsDto(val league: LeagueDto, val standings: StandingsPage)
@Serializable data class LeagueDto(val id: Long, val name: String)
@Serializable data class StandingsPage(
    @SerialName("has_next") val hasNext: Boolean = false,
    val results: List<StandingRow> = emptyList(),
)
@Serializable data class StandingRow(
    val entry: Long,
    @SerialName("entry_name") val entryName: String,
    @SerialName("player_name") val playerName: String,
)

@Serializable data class HistoryDto(
    val current: List<HistoryGw> = emptyList(),
    val chips: List<HistoryChip> = emptyList(),
)
@Serializable data class HistoryGw(
    val event: Int,
    val points: Int,
    @SerialName("event_transfers_cost") val transferCost: Int = 0,
    @SerialName("points_on_bench") val benchPoints: Int = 0,
)
@Serializable data class HistoryChip(val name: String, val event: Int)

@Serializable data class PicksDto(
    @SerialName("active_chip") val activeChip: String? = null,
    val picks: List<PickDto> = emptyList(),
    @SerialName("entry_history") val entryHistory: PicksHistory? = null,
)
@Serializable data class PickDto(
    val element: Int,
    val position: Int,
    val multiplier: Int,
    @SerialName("is_captain") val isCaptain: Boolean,
    @SerialName("is_vice_captain") val isVice: Boolean,
)
@Serializable data class PicksHistory(
    val points: Int = 0,
    @SerialName("event_transfers_cost") val transferCost: Int = 0,
    @SerialName("points_on_bench") val benchPoints: Int = 0,
)

@Serializable data class LiveDto(val elements: List<LiveElement> = emptyList())
@Serializable data class LiveElement(
    val id: Int,
    val stats: LiveStats,
    val explain: List<LiveExplain> = emptyList(),
)
@Serializable data class LiveStats(
    val minutes: Int = 0,
    @SerialName("total_points") val totalPoints: Int = 0,
    val bonus: Int = 0,
    val bps: Int = 0,
)
@Serializable data class LiveExplain(val fixture: Int = 0, val stats: List<ExplainStat> = emptyList())
@Serializable data class ExplainStat(val identifier: String, val points: Int = 0, val value: Int = 0)

@Serializable data class FixtureDto(
    val id: Int,
    val event: Int? = null,
    @SerialName("team_h") val teamH: Int,
    @SerialName("team_a") val teamA: Int,
    @SerialName("kickoff_time") val kickoffTime: String? = null,
    val started: Boolean? = false,
    val finished: Boolean = false,
    @SerialName("finished_provisional") val finishedProvisional: Boolean = false,
    val stats: List<FixtureStat> = emptyList(),
)
@Serializable data class FixtureStat(
    val identifier: String,
    val a: List<FixtureStatValue> = emptyList(),
    val h: List<FixtureStatValue> = emptyList(),
)
@Serializable data class FixtureStatValue(val value: Int, val element: Int)

@Serializable data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)
@Serializable data class GithubAsset(val name: String, @SerialName("browser_download_url") val url: String)

// ---------- Client ----------

class FplApi(private val http: OkHttpClient = defaultClient()) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    fun bootstrap(): BootstrapDto = get("bootstrap-static/", BootstrapDto.serializer())

    fun standings(leagueId: Long, page: Int): StandingsDto =
        get("leagues-classic/$leagueId/standings/?page_standings=$page", StandingsDto.serializer())

    fun history(entry: Long): HistoryDto = get("entry/$entry/history/", HistoryDto.serializer())

    fun picks(entry: Long, gw: Int): PicksDto = get("entry/$entry/event/$gw/picks/", PicksDto.serializer())

    fun live(gw: Int): LiveDto = get("event/$gw/live/", LiveDto.serializer())

    fun fixtures(gw: Int): List<FixtureDto> =
        get("fixtures/?event=$gw", kotlinx.serialization.builtins.ListSerializer(FixtureDto.serializer()))

    fun latestRelease(repo: String): GithubRelease =
        fetch("https://api.github.com/repos/$repo/releases/latest", GithubRelease.serializer())

    private fun <T> get(path: String, de: DeserializationStrategy<T>): T = fetch(BASE + path, de)

    private fun <T> fetch(url: String, de: DeserializationStrategy<T>): T {
        val req = Request.Builder().url(url)
            .header("User-Agent", "FPLBP-Android")
            .header("Accept", "application/json")
            .build()
        val (code, body) = try {
            http.newCall(req).execute().use { resp -> resp.code to resp.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw ApiException.Network(e)
        }
        val trimmed = body.trimStart()
        val looksLikeJson = trimmed.startsWith("{") || trimmed.startsWith("[")
        if (code == 503 || (!looksLikeJson && body.contains("being updated", ignoreCase = true))) throw ApiException.Updating()
        if (code == 404) throw ApiException.NotFound()
        if (code !in 200..299) throw ApiException.Http(code)
        // FPL sometimes answers 200 with an HTML maintenance page.
        if (!looksLikeJson) throw ApiException.Updating()
        if (trimmed == "\"The game is being updated.\"") throw ApiException.Updating()
        return json.decodeFromString(de, body)
    }

    companion object {
        const val BASE = "https://fantasy.premierleague.com/api/"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

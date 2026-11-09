package com.fplbp.app.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "team", primaryKeys = ["entryId"])
data class TeamEntity(
    val entryId: Long,
    val leagueId: Long,
    val name: String,
    val manager: String,
)

@Entity(tableName = "gameweek", primaryKeys = ["id"])
data class GameweekEntity(
    val id: Int,
    val name: String,
    val deadline: Long,
    val finished: Boolean,
    val dataChecked: Boolean,
    val isCurrent: Boolean,
    /** Settled locally: everything stored, never fetched again. */
    val finalLocal: Boolean,
)

/** Official per-GW numbers from entry history. points are BEFORE transfer cost. */
@Entity(tableName = "team_gw", primaryKeys = ["entryId", "gw"])
data class TeamGwEntity(
    val entryId: Long,
    val gw: Int,
    val points: Int,
    val transferCost: Int,
    val benchPoints: Int,
    val chip: String?,
    val isFinal: Boolean,
)

@Entity(tableName = "club", primaryKeys = ["id"])
data class ClubEntity(val id: Int, val name: String, val shortName: String)

@Entity(tableName = "player", primaryKeys = ["id"])
data class PlayerEntity(val id: Int, val webName: String, val clubId: Int, val elementType: Int)

@Entity(tableName = "pick", primaryKeys = ["entryId", "gw", "element"], indices = [Index("gw")])
data class PickEntity(
    val entryId: Long,
    val gw: Int,
    val element: Int,
    val position: Int,
    val multiplier: Int,
    val isCaptain: Boolean,
    val isVice: Boolean,
)

/** Live/final minutes and points of a player in a GW (only players owned in the league). */
@Entity(tableName = "player_gw", primaryKeys = ["gw", "element"])
data class PlayerGwEntity(val gw: Int, val element: Int, val minutes: Int, val totalPoints: Int)

/** Points breakdown by category (goals_scored, assists, clean_sheets, bonus, ...). */
@Entity(tableName = "player_gw_stat", primaryKeys = ["gw", "element", "identifier"])
data class PlayerGwStatEntity(val gw: Int, val element: Int, val identifier: String, val points: Int, val value: Int)

@Entity(tableName = "fixture", primaryKeys = ["id"], indices = [Index("gw")])
data class FixtureEntity(
    val id: Int,
    val gw: Int,
    val teamH: Int,
    val teamA: Int,
    val kickoff: Long?,
    val started: Boolean,
    val finished: Boolean,
    val finishedProvisional: Boolean,
    val bonusConfirmed: Boolean,
)

/** Predicted bonus (from BPS) for fixtures whose bonus isn't confirmed yet. Cleared when the GW is final. */
@Entity(tableName = "provisional_bonus", primaryKeys = ["gw", "element"])
data class ProvisionalBonusEntity(val gw: Int, val element: Int, val points: Int)

@Entity(tableName = "chip_usage", primaryKeys = ["entryId", "name", "gw"])
data class ChipUsageEntity(val entryId: Long, val name: String, val gw: Int)

@Entity(tableName = "chip_window", primaryKeys = ["name", "startEvent"])
data class ChipWindowEntity(val name: String, val number: Int, val startEvent: Int, val stopEvent: Int)

/** Which notifications were already sent (key like "settle-11"). */
@Entity(tableName = "notif_log", primaryKeys = ["key"])
data class NotifLogEntity(val key: String, val sentAt: Long)

/** One transfer (FPL's transfer history). Free Hit moves may be included; the analysis skips them. */
@Entity(tableName = "transfer", primaryKeys = ["entryId", "time", "elementIn"])
data class TransferEntity(val entryId: Long, val gw: Int, val elementIn: Int, val elementOut: Int, val time: String)

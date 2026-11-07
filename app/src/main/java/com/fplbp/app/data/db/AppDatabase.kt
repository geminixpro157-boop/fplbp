package com.fplbp.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Dao
interface FplDao {
    // Teams
    @Upsert suspend fun upsertTeams(t: List<TeamEntity>)
    @Query("SELECT * FROM team WHERE leagueId = :leagueId ORDER BY name") suspend fun teams(leagueId: Long): List<TeamEntity>

    // Gameweeks
    @Upsert suspend fun upsertGameweeks(g: List<GameweekEntity>)
    @Query("SELECT * FROM gameweek ORDER BY id") suspend fun gameweeks(): List<GameweekEntity>
    @Query("UPDATE gameweek SET finalLocal = 1 WHERE id = :gw") suspend fun markFinal(gw: Int)

    // Team GW
    @Upsert suspend fun upsertTeamGw(t: List<TeamGwEntity>)
    @Query("SELECT * FROM team_gw") suspend fun teamGws(): List<TeamGwEntity>
    @Query("UPDATE team_gw SET isFinal = 1 WHERE gw = :gw") suspend fun markTeamGwFinal(gw: Int)
    @Query("SELECT * FROM team_gw WHERE isFinal = 1") suspend fun finalTeamGws(): List<TeamGwEntity>

    // Clubs / players
    @Upsert suspend fun upsertClubs(c: List<ClubEntity>)
    @Query("SELECT * FROM club") suspend fun clubs(): List<ClubEntity>
    @Upsert suspend fun upsertPlayers(p: List<PlayerEntity>)
    @Query("SELECT * FROM player") suspend fun players(): List<PlayerEntity>

    // Picks
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPicks(p: List<PickEntity>)
    @Query("SELECT * FROM pick") suspend fun picks(): List<PickEntity>
    @Query("SELECT COUNT(*) FROM pick WHERE gw = :gw AND entryId = :entryId") suspend fun pickCount(gw: Int, entryId: Long): Int

    // Player GW
    @Query("DELETE FROM player_gw WHERE gw = :gw") suspend fun clearPlayerGw(gw: Int)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPlayerGw(p: List<PlayerGwEntity>)
    @Query("SELECT * FROM player_gw") suspend fun playerGws(): List<PlayerGwEntity>
    @Query("DELETE FROM player_gw_stat WHERE gw = :gw") suspend fun clearStats(gw: Int)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertStats(s: List<PlayerGwStatEntity>)
    @Query("SELECT * FROM player_gw_stat") suspend fun stats(): List<PlayerGwStatEntity>

    // Fixtures
    @Upsert suspend fun upsertFixtures(f: List<FixtureEntity>)
    @Query("SELECT * FROM fixture") suspend fun fixtures(): List<FixtureEntity>

    // Provisional bonus
    @Query("DELETE FROM provisional_bonus WHERE gw = :gw") suspend fun clearProvisionalBonus(gw: Int)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProvisionalBonus(p: List<ProvisionalBonusEntity>)
    @Query("SELECT * FROM provisional_bonus") suspend fun provisionalBonus(): List<ProvisionalBonusEntity>

    // Chips
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChipUsage(c: List<ChipUsageEntity>)
    @Query("SELECT * FROM chip_usage") suspend fun chipUsage(): List<ChipUsageEntity>
    @Query("DELETE FROM chip_window") suspend fun clearChipWindows()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChipWindows(c: List<ChipWindowEntity>)
    @Query("SELECT * FROM chip_window ORDER BY startEvent, name") suspend fun chipWindows(): List<ChipWindowEntity>

    // Notifications
    @Query("SELECT COUNT(*) FROM notif_log WHERE `key` = :key") suspend fun notifSent(key: String): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun logNotif(n: NotifLogEntity)
}

@Database(
    entities = [
        TeamEntity::class, GameweekEntity::class, TeamGwEntity::class, ClubEntity::class, PlayerEntity::class,
        PickEntity::class, PlayerGwEntity::class, PlayerGwStatEntity::class, FixtureEntity::class,
        ProvisionalBonusEntity::class, ChipUsageEntity::class, ChipWindowEntity::class, NotifLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FplDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fplbp.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

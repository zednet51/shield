package com.aggregatorx.shielded.data.db

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aggregatorx.shielded.data.model.*

@Database(
    entities = [ProviderEntity::class, ResultItem::class, AuditLogEntity::class, AuthTokenEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ShieldDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun resultDao(): ResultDao
    abstract fun auditDao(): AuditDao
    abstract fun tokenDao(): TokenDao
}

// ── Provider DAO ──────────────────────────────────────────────────────────────
@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY name ASC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE isEnabled = 1 ORDER BY name ASC")
    fun observeEnabled(): kotlinx.coroutines.flow.Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE isEnabled = 1")
    suspend fun getEnabled(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(provider: ProviderEntity)

    @Update
    suspend fun update(provider: ProviderEntity)

    @Query("DELETE FROM providers WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("UPDATE providers SET currentPage = :page, nextPageUrl = :nextUrl WHERE name = :name")
    suspend fun updatePagination(name: String, page: Int, nextUrl: String?)

    @Query("UPDATE providers SET isEnabled = :enabled WHERE name = :name")
    suspend fun setEnabled(name: String, enabled: Boolean)

    @Query("UPDATE providers SET totalSearches = totalSearches + 1, successRate = :rate WHERE name = :name")
    suspend fun recordSearch(name: String, rate: Float)
}

// ── Result DAO ────────────────────────────────────────────────────────────────
@Dao
interface ResultDao {
    @Query("SELECT * FROM results WHERE providerName = :provider ORDER BY timestamp DESC")
    fun observe(provider: String): kotlinx.coroutines.flow.Flow<List<ResultItem>>

    @Query("SELECT * FROM results WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun observeFavorites(): kotlinx.coroutines.flow.Flow<List<ResultItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ResultItem>)

    @Query("DELETE FROM results WHERE providerName = :provider")
    suspend fun clearProvider(provider: String)

    @Query("UPDATE results SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("UPDATE results SET videoUrl = :url WHERE id = :id")
    suspend fun setVideoUrl(id: String, url: String)
}

// ── Audit DAO ─────────────────────────────────────────────────────────────────
@Dao
interface AuditDao {
    @Insert
    suspend fun insert(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeRecent(): kotlinx.coroutines.flow.Flow<List<AuditLogEntity>>

    @Query("DELETE FROM audit_logs WHERE timestamp < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)
}

// ── Token DAO ─────────────────────────────────────────────────────────────────
@Dao
interface TokenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(token: AuthTokenEntity)

    @Query("SELECT * FROM auth_tokens WHERE host = :host AND status IN ('UNTESTED','ACTIVE') ORDER BY successCount DESC")
    suspend fun getUsable(host: String): List<AuthTokenEntity>

    @Query("SELECT * FROM auth_tokens ORDER BY firstSeenAt DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<AuthTokenEntity>>

    @Query("UPDATE auth_tokens SET status = :status, successCount = successCount + :sd, failureCount = failureCount + :fd, lastUsedAt = :ts WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, sd: Int, fd: Int, ts: Long)

    @Query("DELETE FROM auth_tokens WHERE status IN ('FAILED','EXPIRED')")
    suspend fun purgeUnusable()

    @Query("DELETE FROM auth_tokens WHERE id = :id")
    suspend fun deleteById(id: String)
}

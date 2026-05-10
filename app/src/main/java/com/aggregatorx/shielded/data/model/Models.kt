package com.aggregatorx.shielded.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ── Provider ──────────────────────────────────────────────────────────────────
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val name: String,
    val baseUrl: String,
    val searchPath: String,          // e.g. /search?q={query}&page={page}
    val resultSelector: String = ".result,.item,article",
    val titleSelector: String  = "h2,h3,.title,a",
    val urlSelector: String    = "a",
    val thumbSelector: String  = "img",
    val descSelector: String   = "p,.desc,.description",
    val nextPageSelector: String = "a[rel=next],a.next,.pagination-next",
    val isEnabled: Boolean = true,
    val currentPage: Int = 1,
    val pageSize: Int = 20,
    val nextPageUrl: String? = null,
    val requiresJs: Boolean = false,
    val totalSearches: Int = 0,
    val successRate: Float = 1.0f
)

// ── Result ────────────────────────────────────────────────────────────────────
@Entity(tableName = "results")
data class ResultItem(
    @PrimaryKey val id: String,
    val providerName: String,
    val title: String,
    val url: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val videoUrl: String? = null,
    val duration: String? = null,
    val quality: String? = null,      // "4K","1080p","720p"
    val fileSize: String? = null,
    val seeders: Int? = null,
    val isFavorite: Boolean = false,
    val ocrKeywords: String? = null,  // ML Kit extracted keywords
    val timestamp: Long = System.currentTimeMillis()
)

// ── Audit Log ─────────────────────────────────────────────────────────────────
@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String,
    val providerName: String? = null,
    val details: String,
    val isSuccess: Boolean = true
)

// ── Auth Token ────────────────────────────────────────────────────────────────
@Entity(tableName = "auth_tokens")
data class AuthTokenEntity(
    @PrimaryKey val id: String,
    val host: String,
    val value: String,
    val encoding: String = "RAW",     // RAW | BASE64 | BASE44
    val headerName: String = "Authorization",
    val isBearer: Boolean = true,
    val status: String = "UNTESTED",  // UNTESTED | ACTIVE | FAILED | EXPIRED
    val expiresAtSec: Long? = null,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0
)

// ── Pagination direction ──────────────────────────────────────────────────────
enum class PageDirection { REFRESH, FORWARD, BACK }

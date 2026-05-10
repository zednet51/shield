package com.aggregatorx.shielded.data.repository

import com.aggregatorx.shielded.data.db.*
import com.aggregatorx.shielded.data.model.*
import com.aggregatorx.shielded.engine.scraper.ScrapingEngine
import com.aggregatorx.shielded.engine.token.TokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShieldRepository @Inject constructor(
    private val providerDao: ProviderDao,
    private val resultDao: ResultDao,
    private val auditDao: AuditDao,
    private val tokenDao: TokenDao,
    private val scrapingEngine: ScrapingEngine,
    private val tokenManager: TokenManager
) {
    private val locks = mutableMapOf<String, Mutex>()
    private fun lockFor(name: String) = synchronized(locks) { locks.getOrPut(name) { Mutex() } }

    // ── Providers ─────────────────────────────────────────────────────────────
    fun observeAllProviders(): Flow<List<ProviderEntity>> = providerDao.observeAll()
    fun observeEnabledProviders(): Flow<List<ProviderEntity>> = providerDao.observeEnabled()
    suspend fun upsertProvider(p: ProviderEntity) = providerDao.upsert(p)
    suspend fun deleteProvider(name: String) {
        resultDao.clearProvider(name)
        providerDao.deleteByName(name)
    }
    suspend fun setProviderEnabled(name: String, enabled: Boolean) = providerDao.setEnabled(name, enabled)

    // ── Search ────────────────────────────────────────────────────────────────
    suspend fun search(query: String) {
        val providers = providerDao.getEnabled()
        providers.forEach { p ->
            providerDao.updatePagination(p.name, 1, null)
            scrapeAndStore(p.copy(currentPage = 1, nextPageUrl = null), query, 1, replaceSlice = true)
        }
    }

    suspend fun paginate(providerName: String, direction: PageDirection, query: String) {
        val p = providerDao.getByName(providerName) ?: return
        val targetPage = when (direction) {
            PageDirection.FORWARD -> p.currentPage + 1
            PageDirection.BACK    -> (p.currentPage - 1).coerceAtLeast(1)
            PageDirection.REFRESH -> p.currentPage
        }
        scrapeAndStore(p, query, targetPage, replaceSlice = true)
        providerDao.updatePagination(providerName, targetPage, providerDao.getByName(providerName)?.nextPageUrl)
    }

    private suspend fun scrapeAndStore(p: ProviderEntity, query: String, page: Int, replaceSlice: Boolean) {
        lockFor(p.name).withLock {
            val result = scrapingEngine.scrape(p, query, page)
            if (replaceSlice) resultDao.clearProvider(p.name)
            if (result.items.isNotEmpty()) resultDao.insertAll(result.items)
            if (result.nextPageUrl != null) providerDao.updatePagination(p.name, page, result.nextPageUrl)
            val rate = if (result.items.isNotEmpty()) 1.0f else 0.0f
            providerDao.recordSearch(p.name, rate)
            auditDao.insert(AuditLogEntity(
                actionType = "SCRAPE",
                providerName = p.name,
                details = "page=$page items=${result.items.size} nextUrl=${result.nextPageUrl}",
                isSuccess = result.items.isNotEmpty()
            ))
        }
    }

    // ── Results ───────────────────────────────────────────────────────────────
    fun observeResults(provider: String): Flow<List<ResultItem>> = resultDao.observe(provider)
    fun observeFavorites(): Flow<List<ResultItem>> = resultDao.observeFavorites()
    suspend fun toggleFavorite(id: String, current: Boolean) = resultDao.setFavorite(id, !current)
    suspend fun setVideoUrl(id: String, url: String) = resultDao.setVideoUrl(id, url)

    // ── Tokens ────────────────────────────────────────────────────────────────
    fun observeTokens(): Flow<List<AuthTokenEntity>> = tokenDao.observeAll()
    suspend fun getUsableTokens(host: String) = tokenDao.getUsable(host)
    suspend fun purgeTokens() = tokenDao.purgeUnusable()
    suspend fun deleteToken(id: String) = tokenDao.deleteById(id)

    // ── Audit ─────────────────────────────────────────────────────────────────
    fun observeAuditLogs(): Flow<List<AuditLogEntity>> = auditDao.observeRecent()
    suspend fun log(type: String, provider: String?, details: String, ok: Boolean = true) =
        auditDao.insert(AuditLogEntity(actionType = type, providerName = provider, details = details, isSuccess = ok))
}

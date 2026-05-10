package com.aggregatorx.shielded

import android.app.Application
import com.aggregatorx.shielded.data.db.ProviderDao
import com.aggregatorx.shielded.data.model.ProviderEntity
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ShieldApplication : Application() {

    @Inject lateinit var providerDao: ProviderDao

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seedDefaultProviders() }
    }

    private suspend fun seedDefaultProviders() {
        // Only seed if table is empty
        val existing = providerDao.getEnabled()
        if (existing.isNotEmpty()) return

        val defaults = listOf(
            ProviderEntity(
                name = "1337x",
                baseUrl = "https://1337x.to",
                searchPath = "/search/{query}/{page}/",
                resultSelector = ".table-list tbody tr",
                titleSelector = "td.name a:nth-child(2)",
                urlSelector = "td.name a:nth-child(2)",
                thumbSelector = "img",
                descSelector = "td.seeds",
                nextPageSelector = "a.next",
                requiresJs = false
            ),
            ProviderEntity(
                name = "Nyaa",
                baseUrl = "https://nyaa.si",
                searchPath = "/?f=0&c=0_0&q={query}&p={page}",
                resultSelector = "table.torrent-list tbody tr",
                titleSelector = "td:nth-child(2) a:last-child",
                urlSelector = "td:nth-child(2) a:last-child",
                thumbSelector = "img",
                descSelector = "td:nth-child(6)",
                nextPageSelector = "li.next a",
                requiresJs = false
            ),
            ProviderEntity(
                name = "Archive.org",
                baseUrl = "https://archive.org",
                searchPath = "/search?query={query}&page={page}",
                resultSelector = ".item-ia",
                titleSelector = ".item-title",
                urlSelector = "a.stealth",
                thumbSelector = "img.item-img",
                descSelector = ".item-description",
                nextPageSelector = "a[data-page]",
                requiresJs = false
            ),
            ProviderEntity(
                name = "Pirate Bay",
                baseUrl = "https://thepiratebay.org",
                searchPath = "/search.php?q={query}&page={page}&orderby=99",
                resultSelector = "#searchResult tbody tr",
                titleSelector = ".detName a",
                urlSelector = ".detName a",
                thumbSelector = "img",
                descSelector = ".detDesc",
                nextPageSelector = "a#nextpage",
                requiresJs = false
            ),
            ProviderEntity(
                name = "Torrent Galaxy",
                baseUrl = "https://torrentgalaxy.to",
                searchPath = "/torrents.php?search={query}&page={page}",
                resultSelector = ".tgxtablerow",
                titleSelector = ".txlight",
                urlSelector = ".txlight",
                thumbSelector = "img",
                descSelector = ".badge-secondary",
                nextPageSelector = "a.next",
                requiresJs = false
            )
        )
        defaults.forEach { providerDao.upsert(it) }
    }
}

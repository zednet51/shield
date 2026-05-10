package com.aggregatorx.shielded.engine.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aggregatorx.shielded.data.model.ProviderEntity
import com.aggregatorx.shielded.data.model.ResultItem
import com.aggregatorx.shielded.engine.network.ProxyRotator
import com.aggregatorx.shielded.engine.ocr.VisionEngine
import com.aggregatorx.shielded.engine.token.TokenManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.random.Random

data class ScrapeResult(
    val items: List<ResultItem>,
    val nextPageUrl: String? = null
)

@Singleton
class ScrapingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val proxyRotator: ProxyRotator,
    private val tokenManager: TokenManager,
    private val visionEngine: VisionEngine
) {
    companion object {
        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 13; SM-A325F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        )
        private val QUALITY_PATTERNS = listOf("4K","2160p","1080p","720p","480p","360p","HD","SD","CAM","HDCAM","WEB-DL","BluRay","REMUX")
        private val DURATION_REGEX = Regex("""(\d{1,2}:\d{2}(?::\d{2})?|\d+\s*(?:min|hr|h|m))""", RegexOption.IGNORE_CASE)
        private val SIZE_REGEX = Regex("""(\d+(?:\.\d+)?\s*(?:GB|MB|KB|GiB|MiB))""", RegexOption.IGNORE_CASE)
        private val SEEDER_REGEX = Regex("""(?:seed(?:ers?)?|S)\s*[:\s]?\s*(\d+)""", RegexOption.IGNORE_CASE)
    }

    private fun buildClient(ua: String): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", ua)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("DNT", "1")
                    .build()
                chain.proceed(req)
            }
        if (proxyRotator.isActive()) builder.proxy(proxyRotator.rotate())
        return builder.build()
    }

    /** Entry point — runs Pass 1 (Jsoup) then Pass 2 (WebView) if needed. */
    suspend fun scrape(provider: ProviderEntity, query: String, page: Int): ScrapeResult =
        withContext(Dispatchers.IO) {
            val url = buildUrl(provider, query, page)
            val ua = USER_AGENTS.random()

            // ── Pass 1: Jsoup (fast, no JS) ───────────────────────────────────
            val pass1 = try {
                val html = fetchHttp(url, ua)
                if (html.isNotBlank()) {
                    val doc = Jsoup.parse(html, url)
                    tokenManager.scanAndPersist(url, html)
                    parseDocument(doc, provider, url)
                } else ScrapeResult(emptyList())
            } catch (_: Exception) { ScrapeResult(emptyList()) }

            if (pass1.items.isNotEmpty()) return@withContext enrichWithOcr(pass1)

            // ── Pass 2: Native WebView (JS-rendered) ──────────────────────────
            val pass2 = try {
                val html = fetchWebView(url, ua)
                if (html.isNotBlank()) {
                    val doc = Jsoup.parse(html, url)
                    tokenManager.scanAndPersist(url, html)
                    parseDocument(doc, provider, url)
                } else ScrapeResult(emptyList())
            } catch (_: Exception) { ScrapeResult(emptyList()) }

            enrichWithOcr(pass2)
        }

    private fun fetchHttp(url: String, ua: String): String {
        val client = buildClient(ua)
        val req = Request.Builder().url(url).build()
        return client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchWebView(url: String, ua: String): String =
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                val wv = WebView(context)
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.settings.userAgentString = ua
                wv.settings.mediaPlaybackRequiresUserGesture = false

                var done = false
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        if (done) return
                        // Wait 2s for JS to settle, then extract HTML
                        handler.postDelayed({
                            if (done) return@postDelayed
                            done = true
                            view?.evaluateJavascript("document.documentElement.outerHTML") { html ->
                                val cleaned = (html ?: "")
                                    .removePrefix("\"").removeSuffix("\"")
                                    .replace("\\u003C", "<").replace("\\u003E", ">")
                                    .replace("\\\"", "\"").replace("\\n", "\n")
                                view.destroy()
                                if (cont.isActive) cont.resume(cleaned)
                            }
                        }, 2000)
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
                }
                wv.loadUrl(url)

                cont.invokeOnCancellation {
                    handler.post { if (!done) { done = true; wv.destroy() } }
                }
            }
        }

    private fun parseDocument(doc: Document, provider: ProviderEntity, baseUrl: String): ScrapeResult {
        val selectors = provider.resultSelector.split(",").map { it.trim() }
        var elements = org.jsoup.select.Elements()
        for (sel in selectors) {
            val found = doc.select(sel)
            if (found.size > elements.size) elements = found
        }

        val items = elements.mapNotNull { el ->
            val linkEl = el.selectFirst(provider.urlSelector) ?: el.selectFirst("a") ?: return@mapNotNull null
            val href = linkEl.absUrl("href").ifBlank { linkEl.attr("href") }
            if (href.isBlank()) return@mapNotNull null

            val titleEl = el.selectFirst(provider.titleSelector) ?: linkEl
            val title = titleEl.text().trim().ifBlank { href }
            if (title.length < 2) return@mapNotNull null

            val desc = el.selectFirst(provider.descSelector)?.text()?.trim()
            val thumb = el.selectFirst(provider.thumbSelector)?.let {
                it.absUrl("src").ifBlank { it.attr("data-src") }.ifBlank { it.attr("data-lazy") }
            }?.takeIf { it.startsWith("http") }

            val text = el.text()
            val quality = QUALITY_PATTERNS.firstOrNull { text.contains(it, ignoreCase = true) }
            val duration = DURATION_REGEX.find(text)?.value
            val fileSize = SIZE_REGEX.find(text)?.value
            val seeders = SEEDER_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()

            ResultItem(
                id = "${provider.name}::${href.hashCode()}",
                providerName = provider.name,
                title = title,
                url = href,
                description = desc,
                thumbnailUrl = thumb,
                quality = quality,
                duration = duration,
                fileSize = fileSize,
                seeders = seeders
            )
        }.distinctBy { it.url }

        val nextUrl = doc.selectFirst(provider.nextPageSelector)?.absUrl("href")?.takeIf { it.isNotBlank() }
        return ScrapeResult(items = items, nextPageUrl = nextUrl)
    }

    private suspend fun enrichWithOcr(result: ScrapeResult): ScrapeResult {
        if (result.items.isEmpty()) return result
        // Run OCR on first 5 thumbnails in parallel for keyword enrichment
        val enriched = result.items.mapIndexed { idx, item ->
            if (idx < 5 && !item.thumbnailUrl.isNullOrBlank()) {
                val keywords = try { visionEngine.extractKeywords(item.thumbnailUrl) } catch (_: Exception) { "" }
                if (keywords.isNotBlank()) item.copy(ocrKeywords = keywords) else item
            } else item
        }
        return result.copy(items = enriched)
    }

    private fun buildUrl(provider: ProviderEntity, query: String, page: Int): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val pageToken = ((page - 1).coerceAtLeast(0) * provider.pageSize).toString()
        return "${provider.baseUrl}${provider.searchPath}"
            .replace("{query}", encoded)
            .replace("{page}", page.toString())
            .replace("{offset}", pageToken)
    }
}

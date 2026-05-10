package com.aggregatorx.shielded.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.foundation.text.BasicTextField
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aggregatorx.shielded.data.model.PageDirection
import com.aggregatorx.shielded.data.model.ProviderEntity
import com.aggregatorx.shielded.data.model.ResultItem
import com.aggregatorx.shielded.ui.theme.*
import com.aggregatorx.shielded.ui.viewmodel.SearchTab
import com.aggregatorx.shielded.ui.viewmodel.SearchViewModel

@Composable
fun SearchScreen(vm: SearchViewModel) {
    val state by vm.state.collectAsState()
    val providers by vm.providers.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val tokens by vm.tokens.collectAsState()
    var queryText by remember { mutableStateOf(state.query) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        // ── Fixed Search Bar ──────────────────────────────────────────────────
        NeonSearchBar(
            query = queryText,
            onQueryChange = { queryText = it },
            onSearch = { vm.search(queryText) },
            isSearching = state.isSearching,
            isPaused = state.isPaused
        )

        // ── Quick-Tabs Row ────────────────────────────────────────────────────
        QuickTabsRow(
            activeTab = state.activeTab,
            providers = providers,
            onTabSelect = { vm.setTab(it) }
        )

        HorizontalDivider(color = BorderGreen, thickness = 1.dp)

        // ── Content ───────────────────────────────────────────────────────────
        when (state.activeTab) {
            SearchTab.TOP -> {
                if (state.resultsByProvider.isEmpty() && !state.isSearching) {
                    EmptyState()
                } else {
                    ResultsFeed(
                        resultsByProvider = state.resultsByProvider,
                        providerPages = state.providerPages,
                        providers = providers,
                        isPaused = state.isPaused,
                        onPaginate = { name, dir -> vm.paginate(name, dir) },
                        onToggleFavorite = { vm.toggleFavorite(it) }
                    )
                }
            }
            SearchTab.MY_AI -> FavoritesTab(favorites = favorites, onToggle = { vm.toggleFavorite(it) })
            SearchTab.TOKENS -> TokensTab(
                tokens = tokens,
                onPurge = { vm.purgeTokens() },
                onDelete = { vm.deleteToken(it) }
            )
        }
    }
}

// ── Neon Search Bar ───────────────────────────────────────────────────────────
@Composable
private fun NeonSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isSearching: Boolean,
    isPaused: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .border(1.dp, if (isPaused) AccentAmber else NeonGreen, RoundedCornerShape(6.dp))
            .background(SurfaceBlack, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("> ", color = NeonGreen, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 14.sp)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TextPrimary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 14.sp
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) Text("search query...", color = TextDim,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 14.sp)
                    innerTextField()
                }
            }
        )
        if (isSearching) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NeonGreen, strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onSearch, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = NeonGreen)
            }
        }
    }
}

// ── Quick-Tabs Row ────────────────────────────────────────────────────────────
@Composable
private fun QuickTabsRow(
    activeTab: SearchTab,
    providers: List<ProviderEntity>,
    onTabSelect: (SearchTab) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        QuickTab("TOP",    activeTab == SearchTab.TOP)    { onTabSelect(SearchTab.TOP) }
        QuickTab("MY AI",  activeTab == SearchTab.MY_AI)  { onTabSelect(SearchTab.MY_AI) }
        QuickTab("TOKENS", activeTab == SearchTab.TOKENS) { onTabSelect(SearchTab.TOKENS) }
        // Dynamic provider tabs — clicking sets TOP tab (results already filtered by provider section)
        providers.forEach { p ->
            QuickTab(p.name.uppercase(), false) { onTabSelect(SearchTab.TOP) }
        }
    }
}

@Composable
private fun QuickTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) NeonGreenFaint else Color.Transparent
    val border = if (selected) NeonGreen else BorderGreen
    val textColor = if (selected) NeonGreen else TextSecondary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = textColor, fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// ── Results Feed ──────────────────────────────────────────────────────────────
@Composable
private fun ResultsFeed(
    resultsByProvider: Map<String, List<ResultItem>>,
    providerPages: Map<String, Int>,
    providers: List<ProviderEntity>,
    isPaused: Boolean,
    onPaginate: (String, PageDirection) -> Unit,
    onToggleFavorite: (ResultItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        resultsByProvider.forEach { (providerName, results) ->
            val currentPage = providerPages[providerName] ?: 1
            // ── Provider Section Header ────────────────────────────────────
            item(key = "header_$providerName") {
                ProviderSectionHeader(
                    name = providerName,
                    page = currentPage,
                    resultCount = results.size,
                    isPaused = isPaused,
                    onBack    = { onPaginate(providerName, PageDirection.BACK) },
                    onForward = { onPaginate(providerName, PageDirection.FORWARD) },
                    onRefresh = { onPaginate(providerName, PageDirection.REFRESH) }
                )
            }
            // ── Result Cards ───────────────────────────────────────────────
            items(results, key = { it.id }) { item ->
                ResultCard(item = item, onToggleFavorite = { onToggleFavorite(item) })
            }
        }
    }
}

// ── Provider Section Header ───────────────────────────────────────────────────
@Composable
private fun ProviderSectionHeader(
    name: String,
    page: Int,
    resultCount: Int,
    isPaused: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBlack)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "[ $name ]",
            color = NeonGreen,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$resultCount results",
            color = TextDim,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.width(8.dp))
        // Pagination controls — top-right of section
        IconButton(onClick = onBack, modifier = Modifier.size(28.dp), enabled = !isPaused && page > 1) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev", tint = if (page > 1 && !isPaused) NeonGreen else TextDim, modifier = Modifier.size(18.dp))
        }
        Text(
            text = "$page",
            color = NeonGreen,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        IconButton(onClick = onForward, modifier = Modifier.size(28.dp), enabled = !isPaused) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = if (!isPaused) NeonGreen else TextDim, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp), enabled = !isPaused) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = if (!isPaused) AccentCyan else TextDim, modifier = Modifier.size(16.dp))
        }
    }
}

// ── Result Card ───────────────────────────────────────────────────────────────
@Composable
private fun ResultCard(item: ResultItem, onToggleFavorite: () -> Unit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .border(1.dp, BorderGreen, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = CardBlack)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Thumbnail + Metadata Row ───────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                // Thumbnail
                if (!item.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(item.thumbnailUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp, 54.dp).clip(RoundedCornerShape(4.dp)).background(SurfaceBlack),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    // Metadata badges row
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.quality?.let { MetaBadge(it, NeonGreen) }
                        item.duration?.let { MetaBadge(it, AccentCyan) }
                        item.fileSize?.let { MetaBadge(it, TextSecondary) }
                        item.seeders?.let { MetaBadge("S:$it", if (it > 50) AccentAmber else TextDim) }
                    }
                    if (!item.description.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.description,
                            color = TextDim,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!item.ocrKeywords.isNullOrBlank()) {
                        Text(
                            text = "OCR: ${item.ocrKeywords}",
                            color = TextDim,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderGreen, thickness = 0.5.dp)

            // ── Action Row ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // ▶ Watch — open in ExoPlayer via VideoPlayerActivity
                ActionBtn(Icons.Default.PlayArrow, "Watch", NeonGreen) {
                    val intent = Intent(context, com.aggregatorx.shielded.ui.VideoPlayerActivity::class.java).apply {
                        putExtra("url", item.videoUrl ?: item.url)
                        putExtra("title", item.title)
                    }
                    context.startActivity(intent)
                }
                // ⬇ Download — trigger system download manager
                ActionBtn(Icons.Default.Download, "DL", AccentCyan) {
                    val dlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                    context.startActivity(dlIntent)
                }
                // ↑ Browser — open in external browser
                ActionBtn(Icons.Default.OpenInBrowser, "Browser", TextSecondary) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                }
                // 👁 In-App WebView
                ActionBtn(Icons.Default.Visibility, "In App", TextSecondary) {
                    val intent = Intent(context, com.aggregatorx.shielded.ui.WebViewActivity::class.java).apply {
                        putExtra("url", item.url)
                        putExtra("title", item.title)
                    }
                    context.startActivity(intent)
                }
                // ♥ Favorite
                ActionBtn(
                    icon = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = if (item.isFavorite) "Saved" else "Save",
                    tint = if (item.isFavorite) AccentRed else TextDim,
                    onClick = onToggleFavorite
                )
            }
        }
    }
}

@Composable
private fun MetaBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(text, color = color, fontSize = 9.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, color = tint, fontSize = 9.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

// ── Favorites Tab ─────────────────────────────────────────────────────────────
@Composable
private fun FavoritesTab(favorites: List<ResultItem>, onToggle: (ResultItem) -> Unit) {
    if (favorites.isEmpty()) {
        EmptyState("No saved results yet.\nHeart items from search results.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(favorites, key = { it.id }) { item ->
                ResultCard(item = item, onToggleFavorite = { onToggle(item) })
            }
        }
    }
}

// ── Tokens Tab ────────────────────────────────────────────────────────────────
@Composable
private fun TokensTab(
    tokens: List<com.aggregatorx.shielded.data.model.AuthTokenEntity>,
    onPurge: () -> Unit,
    onDelete: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CAPTURED TOKENS (${tokens.size})", color = NeonGreen,
                style = MaterialTheme.typography.labelLarge)
            OutlinedButton(
                onClick = onPurge,
                border = BorderStroke(1.dp, AccentRed),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("PURGE FAILED", color = AccentRed, fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
        if (tokens.isEmpty()) {
            EmptyState("No tokens captured yet.\nTokens are auto-discovered during scraping.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(tokens, key = { it.id }) { token ->
                    TokenCard(token = token, onDelete = { onDelete(token.id) })
                }
            }
        }
    }
}

@Composable
private fun TokenCard(token: com.aggregatorx.shielded.data.model.AuthTokenEntity, onDelete: () -> Unit) {
    val statusColor = when (token.status) {
        "ACTIVE"   -> NeonGreen
        "FAILED"   -> AccentRed
        "EXPIRED"  -> AccentAmber
        else       -> TextSecondary
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)
            .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = CardBlack)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaBadge(token.status, statusColor)
                    MetaBadge(token.encoding, AccentCyan)
                    MetaBadge(token.headerName, TextSecondary)
                }
                Spacer(Modifier.height(4.dp))
                Text(token.host, color = NeonGreen, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = token.value.take(60) + if (token.value.length > 60) "…" else "",
                    color = TextDim,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
                Text(
                    text = "✓${token.successCount}  ✗${token.failureCount}",
                    color = TextDim,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentRed, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Empty State ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyState(message: String = "Enter a query and press SEARCH\nto begin discovery.") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("[ NO DATA ]", color = NeonGreenDim,
                style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(message, color = TextDim,
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}



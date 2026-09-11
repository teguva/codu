package tv.coog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.coog.app.data.CoogApi
import tv.coog.app.data.MediaItem
import tv.coog.app.data.StreamCandidate
import tv.coog.app.ui.theme.CoogBgDeep
import tv.coog.app.ui.theme.CoogCached
import tv.coog.app.ui.theme.CoogDanger
import tv.coog.app.ui.theme.CoogTextSecondary
import tv.coog.app.ui.theme.CoogType

@Composable
fun StreamsScreen(
    item: MediaItem,
    playError: String?,
    onBack: () -> Unit,
    onPick: (StreamCandidate) -> Unit,
) {
    val server = LocalCoogServer.current
    var loading by remember(item.id) { mutableStateOf(true) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }
    var items by remember(item.id) { mutableStateOf<List<StreamCandidate>>(emptyList()) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(item.id, item.imdbId, item.season, item.episode, server.url) {
        loading = true
        error = null
        try {
            val kind = item.kind.ifBlank { "movie" }
            items = CoogApi(server.url, server.token).catalogStreams(
                imdbId = item.imdbId,
                kind = kind,
                season = item.season,
                episode = item.episode,
                title = item.headline(),
                year = item.year,
            )
            if (items.isEmpty()) {
                error = "No sources found."
            }
        } catch (e: Exception) {
            error = e.message ?: "Could not load sources"
        } finally {
            loading = false
        }
    }
    LaunchedEffect(loading, items.firstOrNull()?.infoHash) {
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CoogBgDeep),
    ) {
        PosterArt(
            item = item,
            kind = ArtKind.Backdrop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to CoogBgDeep.copy(alpha = 0.72f),
                        0.18f to CoogBgDeep.copy(alpha = 0.88f),
                        1f to CoogBgDeep,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sources", style = CoogType.screenTitle)
            Text(item.headline(), style = CoogType.heroTagline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton(
                    label = "Back",
                    onClick = onBack,
                    modifier = if (loading || items.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
            if (playError != null) {
                Text(friendlyPlayError(playError), color = CoogDanger, style = CoogType.heroPlot)
            }
            when {
                loading -> Text("Looking up torrents, Real-Debrid, and web sources…", style = CoogType.heroPlot)
                error != null && items.isEmpty() -> Text(error ?: "", color = CoogDanger)
                else -> {
                    val best = remember(items) { items.filter { it.cached }.ifEmpty { items.take(3) } }
                    val more = remember(items, best) { items.filterNot { it in best } }
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                    ) {
                        item(key = "best-header") {
                            Text(
                                if (best.any { it.cached }) "Best · ready to play" else "Best matches",
                                style = CoogType.shelfTitle,
                            )
                        }
                        itemsIndexed(best, key = { _, row ->
                            "best-" + row.infoHash.ifBlank { row.url }.ifBlank { row.title } + row.size
                        }) { index, row ->
                            StreamRow(
                                candidate = row,
                                onClick = { onPick(row) },
                                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                badge = when {
                                    row.cached -> "Fast"
                                    index == 0 -> "Best"
                                    else -> null
                                },
                            )
                        }
                        if (more.isNotEmpty()) {
                            item(key = "more-header") {
                                Text("More sources", style = CoogType.shelfTitle, modifier = Modifier.padding(top = 8.dp))
                            }
                            itemsIndexed(more, key = { _, row ->
                                "more-" + row.infoHash.ifBlank { row.url }.ifBlank { row.title } + row.size
                            }) { _, row ->
                                StreamRow(
                                    candidate = row,
                                    onClick = { onPick(row) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamRow(
    candidate: StreamCandidate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.07f),
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                else Modifier,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            QualityChip(candidate.quality.ifBlank { "—" })
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        candidate.title.ifBlank { candidate.name }.ifBlank { candidate.infoHash },
                        style = CoogType.cardTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!badge.isNullOrBlank()) {
                        Text(badge, style = CoogType.chip, color = CoogCached)
                    }
                }
                Text(
                    listOfNotNull(
                        when {
                            candidate.kind.equals("web", true) || candidate.source.equals("web", true) -> "Web-DL"
                            candidate.cached -> "Cached"
                            else -> "Local torrent"
                        },
                        if (candidate.source.equals("rdcatalog", ignoreCase = true)) "RD library" else null,
                        candidate.seeders.takeIf { it > 0 }?.let { "$it seeders" },
                        candidate.sizeLabel.ifBlank { null },
                        candidate.provider.ifBlank { null },
                    ).joinToString("  ·  "),
                    style = CoogType.cardYear,
                    color = if (candidate.cached) CoogCached else CoogTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .background(
                        if (candidate.cached) Color(0xFF1F6B3A) else Color.White.copy(alpha = 0.10f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        candidate.kind.equals("web", true) || candidate.source.equals("web", true) -> "Web"
                        candidate.source.equals("rdcatalog", ignoreCase = true) -> "RD lib"
                        candidate.cached -> "RD+"
                        else -> "Local"
                    },
                    style = CoogType.chip,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun QualityChip(label: String) {
    Text(
        label,
        style = CoogType.chip,
        modifier = Modifier
            .width(64.dp)
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = Color.White,
    )
}

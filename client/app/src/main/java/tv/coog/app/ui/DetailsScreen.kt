package tv.coog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import tv.coog.app.data.CastMember
import tv.coog.app.data.CoogApi
import tv.coog.app.data.MediaItem
import tv.coog.app.data.PersonSummary
import tv.coog.app.ui.theme.CoogBgDeep
import tv.coog.app.ui.theme.CoogTextMuted
import tv.coog.app.ui.theme.CoogTextSecondary
import tv.coog.app.ui.theme.CoogType

@Composable
fun MovieDetailsScreen(
    item: MediaItem,
    playError: String?,
    onBack: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onSources: (MediaItem) -> Unit,
    onOpenPerson: (PersonSummary) -> Unit,
    onOpenSimilar: (MediaItem) -> Unit,
) {
    val server = LocalCoogServer.current
    var details by remember(item.id) { mutableStateOf(item) }
    var similar by remember(item.id) { mutableStateOf<List<MediaItem>>(emptyList()) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(item.id) { runCatching { playFocus.requestFocus() } }
    LaunchedEffect(item.id, item.imdbId, item.tmdbId, item.title, server.url, server.token) {
        val api = CoogApi(server.url, server.token)
        val remote = when {
            item.imdbId.isNotBlank() -> runCatching {
                api.catalogTitle(item.imdbId, item.kind.ifBlank { "movie" })
            }.getOrNull()
            item.tmdbId != 0 -> runCatching {
                api.catalogTmdb(item.kind.ifBlank { "movie" }, item.tmdbId)
            }.getOrNull()
            else -> catalogMatch(api, item)
        }
        if (remote != null) {
            details = mergeDetails(item, remote)
        }
        val imdb = details.imdbId.ifBlank { item.imdbId }
        if (imdb.isNotBlank()) {
            similar = runCatching {
                api.catalogSimilar(imdb, details.kind.ifBlank { "movie" })
            }.getOrDefault(emptyList())
        }
    }
    TitleOverview(
        item = details,
        onPlay = onPlay,
        onSources = onSources,
        onOpenPerson = onOpenPerson,
        onBack = onBack,
        playFocus = playFocus,
        similar = similar,
        onOpenSimilar = onOpenSimilar,
        playError = playError,
    )
}

@Composable
fun ShowDetailsScreen(
    show: ShowRow,
    playError: String?,
    onBack: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onSources: (MediaItem) -> Unit,
    onOpenPerson: (PersonSummary) -> Unit,
    onOpenSimilar: (MediaItem) -> Unit,
) {
    val server = LocalCoogServer.current
    val seed = remember(show.name) {
        show.cover.copy(kind = "series", title = show.name.ifBlank { show.cover.title })
    }
    var details by remember(show.name) { mutableStateOf(seed) }
    var similar by remember(show.name) { mutableStateOf<List<MediaItem>>(emptyList()) }
    val playFocus = remember { FocusRequester() }
    val playable = show.episodes.firstOrNull { it.isLocal() } ?: show.episodes.firstOrNull()
    LaunchedEffect(show.name) { runCatching { playFocus.requestFocus() } }
    LaunchedEffect(show.name, show.cover.imdbId, show.cover.tmdbId, seed.title, server.url, server.token) {
        val api = CoogApi(server.url, server.token)
        val remote = when {
            seed.imdbId.isNotBlank() -> runCatching {
                api.catalogTitle(seed.imdbId, "series")
            }.getOrNull()
            seed.tmdbId != 0 -> runCatching {
                api.catalogTmdb("series", seed.tmdbId)
            }.getOrNull()
            else -> catalogMatch(api, seed.copy(kind = "series"))
        }
        if (remote != null) {
            details = mergeDetails(seed, remote).copy(kind = "series", title = show.name.ifBlank { remote.title })
        }
        val imdb = details.imdbId.ifBlank { seed.imdbId }
        if (imdb.isNotBlank()) {
            similar = runCatching { api.catalogSimilar(imdb, "series") }.getOrDefault(emptyList())
        }
    }
    TitleOverview(
        item = details,
        onPlay = { onPlay(playable ?: details) },
        onSources = onSources,
        onOpenPerson = onOpenPerson,
        onBack = onBack,
        playFocus = playFocus,
        playError = playError,
        similar = similar,
        similarLabel = "Similar series",
        onOpenSimilar = onOpenSimilar,
        bottomShelf = if (show.episodes.isNotEmpty()) {
            {
                CatalogRow(
                    label = "Episodes",
                    items = show.episodes,
                    onOpen = onPlay,
                    insetStart = 72.dp,
                    compact = true,
                )
            }
        } else {
            null
        },
        extraShelf = if (show.episodes.isNotEmpty() && similar.isNotEmpty()) {
            {
                CatalogRow(
                    label = "Similar series",
                    items = similar,
                    onOpen = onOpenSimilar,
                    insetStart = 72.dp,
                    compact = true,
                )
            }
        } else {
            null
        },
    )
}

@Composable
fun FolderBrowseScreen(
    folder: FolderRow,
    playError: String?,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(folder.name) { runCatching { firstFocus.requestFocus() } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoogBgDeep)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(folder.name, style = CoogType.screenTitle)
        Text(folder.subtitle, style = CoogType.heroPlot, color = CoogTextSecondary)
        if (playError != null) {
            Text(friendlyPlayError(playError), color = Color(0xFFFF8B8B))
        }
        GhostButton(label = "Back", onClick = onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(folder.items, key = { _, item -> item.id }) { index, item ->
                PosterCard(
                    item = item,
                    title = item.headline(),
                    subtitle = item.year.takeIf { it > 0 }?.toString() ?: item.supporting(),
                    onClick = { onOpen(item) },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
    }
}

@Composable
fun CastRow(people: List<CastMember>, onOpen: (PersonSummary) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Cast", style = CoogType.shelfTitle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(people, key = { person ->
                if (person.tmdbId != 0) person.tmdbId else person.name
            }) { person ->
                CastCard(person = person, onClick = {
                    onOpen(PersonSummary(tmdbId = person.tmdbId, name = person.name, profileUrl = person.profileUrl))
                })
            }
        }
    }
}

@Composable
private fun CastCard(person: CastMember, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
        ),
        modifier = Modifier.width(108.dp),
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f)),
            ) {
                if (person.profileUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(person.profileUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = person.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        person.name.take(1).uppercase(),
                        style = CoogType.cardTitle,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Text(person.name, style = CoogType.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (person.character.isNotBlank()) {
                Text(person.character, style = CoogType.cardYear, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun WhitePill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = Color.White,
            contentColor = Color(0xFF121214),
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF121214),
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(label)
        }
    }
}

@Composable
fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF121214),
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.04f),
    ) {
        Text(label)
    }
}

internal fun mergeDetails(local: MediaItem, remote: MediaItem): MediaItem = remote.copy(
    id = local.id.ifBlank { remote.id },
    path = local.path.ifBlank { remote.path },
    inLibrary = local.inLibrary || remote.inLibrary,
    libraryId = local.libraryId.ifBlank { remote.libraryId },
    kind = local.kind.ifBlank { remote.kind },
    season = if (local.season > 0) local.season else remote.season,
    episode = if (local.episode > 0) local.episode else remote.episode,
    showTitle = local.showTitle.ifBlank { remote.showTitle },
    codecVideo = local.codecVideo.ifBlank { remote.codecVideo },
    codecAudio = local.codecAudio.ifBlank { remote.codecAudio },
    width = if (local.width > 0) local.width else remote.width,
    height = if (local.height > 0) local.height else remote.height,
    durationMs = if (local.durationMs > 0) local.durationMs else remote.durationMs,
    runtimeMinutes = if (remote.runtimeMinutes > 0) remote.runtimeMinutes else local.runtimeMinutes,
    certification = remote.certification.ifBlank { local.certification },
    country = remote.country.ifBlank { local.country },
    director = if (remote.director.name.isNotBlank()) remote.director else local.director,
    matchStatus = when {
        remote.imdbId.isNotBlank() || remote.plot.isNotBlank() || remote.genres.isNotEmpty() ->
            remote.matchStatus.ifBlank { "matched" }
        else -> local.matchStatus.ifBlank { remote.matchStatus }
    },
    logoUrl = remote.logoUrl.ifBlank { local.logoUrl },
)

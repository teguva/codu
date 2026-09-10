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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import tv.coog.app.data.MediaItem
import tv.coog.app.ui.theme.CoogBgDeep
import tv.coog.app.ui.theme.CoogTextMuted
import tv.coog.app.ui.theme.CoogTextSecondary
import tv.coog.app.ui.theme.CoogType

@Composable
fun MovieDetailsScreen(
    item: MediaItem,
    playError: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(item.id) { runCatching { playFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CoogBgDeep)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                    onBack()
                    true
                } else {
                    false
                }
            },
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
                    Brush.horizontalGradient(
                        0f to CoogBgDeep.copy(alpha = 0.88f),
                        0.45f to CoogBgDeep.copy(alpha = 0.45f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.55f)
                .padding(start = 56.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.headline(), style = CoogType.heroTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.heroSubtitle().isNotBlank()) {
                Text(item.heroSubtitle(), style = CoogType.heroTagline, maxLines = 2)
            }
            if (item.heroDescription().isNotBlank()) {
                Text(item.heroDescription(), style = CoogType.heroPlot, maxLines = 4)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.heroChips().forEach { chip ->
                    Text(
                        chip,
                        style = CoogType.chip,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Text(item.techLine(), style = CoogType.cardYear, color = CoogTextMuted)
            if (playError != null) {
                Text(playError, color = Color(0xFFFF8B8B))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WhitePill(label = "Play", onClick = onPlay, modifier = Modifier.focusRequester(playFocus))
                GhostButton(label = "Back", onClick = onBack)
            }
        }
    }
}

@Composable
fun ShowDetailsScreen(
    show: ShowRow,
    playError: String?,
    onBack: () -> Unit,
    onPlay: (MediaItem) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(show.name) { runCatching { firstFocus.requestFocus() } }
    val seasons = remember(show.episodes) { show.episodes.groupBy { it.season }.toSortedMap() }
    Box(modifier = Modifier.fillMaxSize().background(CoogBgDeep)) {
        PosterArt(item = show.cover, kind = ArtKind.Backdrop, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(CoogBgDeep.copy(alpha = 0.55f), CoogBgDeep)),
            ),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                        onBack()
                        true
                    } else {
                        false
                    }
                },
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 36.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(show.name, style = CoogType.heroTitle)
            }
            item {
                Text(show.subtitle, style = CoogType.heroPlot, color = CoogTextSecondary)
            }
            if (playError != null) {
                item { Text(playError, color = Color(0xFFFF8B8B)) }
            }
            seasons.forEach { (season, episodes) ->
                val label = if (season > 0) "Season $season" else "Episodes"
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(label, style = CoogType.shelfTitle)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(episodes, key = { _, item -> item.id }) { index, item ->
                                PosterCard(
                                    item = item,
                                    title = item.seasonEpisode().ifBlank { item.episodeHeadline() },
                                    subtitle = item.episodeName().ifBlank { item.resolutionLabel() ?: "" },
                                    onClick = { onPlay(item) },
                                    modifier = if (season == seasons.keys.first() && index == 0) {
                                        Modifier.focusRequester(firstFocus)
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item { GhostButton(label = "Back", onClick = onBack) }
        }
    }
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
            .padding(horizontal = 48.dp, vertical = 32.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                    onBack()
                    true
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(folder.name, style = CoogType.heroTitle)
        Text(folder.subtitle, style = CoogType.heroPlot, color = CoogTextSecondary)
        if (playError != null) {
            Text(playError, color = Color(0xFFFF8B8B))
        }
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
        GhostButton(label = "Back", onClick = onBack)
    }
}

@Composable
fun WhitePill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        Text(label)
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

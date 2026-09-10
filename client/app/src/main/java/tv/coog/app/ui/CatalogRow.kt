package tv.coog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.coog.app.data.MediaItem
import tv.coog.app.ui.theme.CoogType

private val PosterShape = RoundedCornerShape(18.dp)

internal data class PosterMetrics(val width: Dp, val height: Dp, val gap: Dp)

@Composable
private fun rememberPosterMetrics(): PosterMetrics {
    val density = LocalDensity.current.density
    val widthDp = LocalConfiguration.current.screenWidthDp
    val widthPx = widthDp * density
    val cardPx = (widthPx * 0.11f).coerceIn(128f, 196f)
    val gapPx = (widthPx * 0.012f).coerceIn(14f, 24f)
    val width = cardPx / density
    val gap = gapPx / density
    return remember(widthDp, density) {
        PosterMetrics(width = width.dp, height = (width * 1.5f).dp, gap = gap.dp)
    }
}

@Composable
fun CatalogRow(
    label: String,
    items: List<MediaItem>,
    onOpen: (MediaItem) -> Unit,
    onFocused: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
) {
    if (items.isEmpty()) return
    val metrics = rememberPosterMetrics()
    Shelf(label = label, metrics = metrics, modifier = modifier) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            PosterCard(
                item = item,
                title = item.headline(),
                subtitle = item.year.takeIf { it > 0 }?.toString().orEmpty(),
                onClick = { onOpen(item) },
                onFocused = onFocused?.let { cb -> { cb(item) } },
                modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}

@Composable
fun ShowCatalogRow(
    label: String,
    shows: List<ShowRow>,
    onOpen: (ShowRow) -> Unit,
    onFocused: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
) {
    if (shows.isEmpty()) return
    val metrics = rememberPosterMetrics()
    Shelf(label = label, metrics = metrics, modifier = modifier) {
        itemsIndexed(shows, key = { _, show -> show.name }) { index, show ->
            PosterCard(
                item = show.cover,
                title = show.name,
                subtitle = show.subtitle,
                onClick = { onOpen(show) },
                onFocused = onFocused?.let { cb -> { cb(show.cover) } },
                modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}

@Composable
fun FolderCatalogRow(
    label: String,
    folders: List<FolderRow>,
    onOpen: (FolderRow) -> Unit,
    onFocused: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
) {
    if (folders.isEmpty()) return
    val metrics = rememberPosterMetrics()
    Shelf(label = label, metrics = metrics, modifier = modifier) {
        itemsIndexed(folders, key = { _, folder -> folder.name }) { index, folder ->
            PosterCard(
                item = folder.cover,
                title = folder.name,
                subtitle = folder.subtitle,
                onClick = { onOpen(folder) },
                onFocused = onFocused?.let { cb -> { cb(folder.cover) } },
                modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun Shelf(
    label: String,
    metrics: PosterMetrics,
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = CoogType.shelfTitle,
            modifier = Modifier.padding(start = RailWidth + 22.dp),
        )
        LazyRow(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
            contentPadding = PaddingValues(
                start = RailWidth + 22.dp,
                end = 28.dp,
                top = 14.dp,
                bottom = 6.dp,
            ),
            content = content,
        )
    }
}

@Composable
fun PosterCard(
    item: MediaItem,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onFocused: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val size = rememberPosterMetrics()
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.width(size.width),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape = PosterShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                pressedContainerColor = Color.Transparent,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = modifier
                .fillMaxWidth()
                .height(size.height)
                .graphicsLayer {
                    val scale = if (focused) 1.06f else 1f
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 1f)
                }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused?.invoke()
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(PosterShape)
                    .then(
                        if (focused) {
                            Modifier.border(3.dp, Color.White, PosterShape)
                        } else {
                            Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), PosterShape)
                        },
                    )
                    .background(Color.White.copy(alpha = 0.06f)),
            ) {
                PosterArt(
                    item = item,
                    kind = ArtKind.Poster,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            title,
            style = CoogType.cardTitle.copy(color = Color.White.copy(alpha = if (focused) 1f else 0.92f)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = CoogType.cardYear,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

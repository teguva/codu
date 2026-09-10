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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.coog.app.data.JobItem
import tv.coog.app.data.MediaItem
import tv.coog.app.ui.theme.CoogDanger
import tv.coog.app.ui.theme.CoogType

private val PosterShape = RoundedCornerShape(12.dp)

internal data class PosterMetrics(val width: Dp, val height: Dp, val gap: Dp)

@Composable
private fun rememberPosterMetrics(compact: Boolean = false, featured: Boolean = false): PosterMetrics {
    val widthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val cardDp = when {
        featured -> widthDp * 0.145f
        compact -> widthDp * 0.078f
        else -> widthDp * 0.100f
    }
    val gapDp = when {
        featured -> widthDp * 0.010f
        compact -> widthDp * 0.009f
        else -> widthDp * 0.010f
    }
    return remember(widthDp, compact, featured) {
        PosterMetrics(width = cardDp.dp, height = (cardDp * 1.5f).dp, gap = gapDp.dp)
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
    jobs: List<JobItem> = emptyList(),
    insetStart: Dp = RailWidth,
    compact: Boolean = false,
    featured: Boolean = false,
    exitUp: Boolean = false,
    showBadge: Boolean = !featured,
) {
    if (items.isEmpty()) return
    val hideCaptions = compact || featured
    val metrics = rememberPosterMetrics(compact = hideCaptions, featured = featured)
    Shelf(label = label, metrics = metrics, modifier = modifier, insetStart = insetStart, compact = hideCaptions) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            PosterCard(
                item = item,
                title = item.headline(),
                subtitle = item.year.takeIf { it > 0 }?.toString().orEmpty(),
                onClick = { onOpen(item) },
                onFocused = onFocused?.let { cb -> { cb(item) } },
                modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                badge = if (showBadge) item.posterBadgeLabel(jobs) else null,
                exitUp = exitUp,
                compact = hideCaptions,
                featured = featured,
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
    jobs: List<JobItem> = emptyList(),
    compact: Boolean = false,
    insetStart: Dp = RailWidth,
    featured: Boolean = false,
    exitUp: Boolean = false,
) {
    if (shows.isEmpty()) return
    val hideCaptions = compact || featured
    val metrics = rememberPosterMetrics(compact = hideCaptions, featured = featured)
    Shelf(label = label, metrics = metrics, modifier = modifier, compact = hideCaptions, insetStart = insetStart) {
        itemsIndexed(shows, key = { _, show -> show.name }) { index, show ->
            PosterCard(
                item = show.cover,
                title = show.name,
                subtitle = show.subtitle,
                onClick = { onOpen(show) },
                onFocused = onFocused?.let { cb -> { cb(show.cover) } },
                modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                badge = if (featured) null else show.cover.posterBadgeLabel(jobs),
                exitUp = exitUp,
                compact = hideCaptions,
                featured = featured,
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
    compact: Boolean = false,
    insetStart: Dp = RailWidth,
    featured: Boolean = false,
    exitUp: Boolean = false,
) {
    if (folders.isEmpty()) return
    val hideCaptions = compact || featured
    val metrics = rememberPosterMetrics(compact = hideCaptions, featured = featured)
    Shelf(label = label, metrics = metrics, modifier = modifier, compact = hideCaptions, insetStart = insetStart) {
        itemsIndexed(folders, key = { _, folder -> folder.name }) { index, folder ->
            PosterCard(
                item = folder.cover,
                title = folder.name,
                subtitle = folder.subtitle,
                onClick = { onOpen(folder) },
                onFocused = onFocused?.let { cb -> { cb(folder.cover) } },
                modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                exitUp = exitUp,
                compact = hideCaptions,
                featured = featured,
            )
        }
    }
}

@Composable
private fun Shelf(
    label: String,
    metrics: PosterMetrics,
    modifier: Modifier = Modifier,
    insetStart: Dp = RailWidth,
    compact: Boolean = false,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 2.dp)) {
        Text(
            label,
            style = CoogType.shelfTitle,
            modifier = Modifier.padding(start = insetStart),
        )
        LazyRow(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(metrics.gap),
            contentPadding = PaddingValues(
                start = insetStart,
                end = if (compact) 12.dp else 28.dp,
                top = if (compact) 14.dp else 12.dp,
                bottom = if (compact) 14.dp else 16.dp,
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
    badge: String? = item.posterBadgeLabel(),
    exitUp: Boolean = false,
    compact: Boolean = false,
    featured: Boolean = false,
) {
    val size = rememberPosterMetrics(compact = compact, featured = featured)
    var focused by remember { mutableStateOf(false) }
    val railFocus = LocalRailFocus.current
    Column(
        modifier = Modifier.width(size.width),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape = PosterShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                pressedContainerColor = Color.Transparent,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            modifier = modifier
                .fillMaxWidth()
                .height(size.height)
                .then(
                    if (exitUp && railFocus != null) {
                        Modifier.focusProperties { up = railFocus }
                    } else {
                        Modifier
                    },
                )
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
                            Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), PosterShape)
                        },
                    )
                    .background(Color.White.copy(alpha = 0.06f)),
            ) {
                PosterArt(
                    item = item,
                    kind = ArtKind.Poster,
                    badge = badge,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (!compact) {
            Column(
                modifier = Modifier.padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    title,
                    style = CoogType.cardTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = CoogType.cardYear,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun JobCatalogRow(
    jobs: List<JobItem>,
    onOpen: (JobItem) -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
) {
    if (jobs.isEmpty()) return
    val metrics = rememberPosterMetrics()
    val cardWidth = metrics.width * 1.9f
    val cardHeight = metrics.height * 0.42f
    Shelf(label = "Downloading", metrics = metrics, modifier = modifier) {
        itemsIndexed(jobs, key = { _, job -> job.id }) { index, job ->
            JobCard(
                job = job,
                width = cardWidth,
                height = cardHeight,
                onClick = { onOpen(job) },
                modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                exitUp = index == 0,
            )
        }
    }
}

@Composable
internal fun JobCard(
    job: JobItem,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    exitUp: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val progress = job.progress.toFloat().coerceIn(0f, 1f)
    val railFocus = LocalRailFocus.current
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = PosterShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier
            .then(
                if (exitUp && railFocus != null) Modifier.focusProperties { up = railFocus } else Modifier,
            )
            .onFocusChanged { focused = it.isFocused }
            .width(width)
            .height(height),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(PosterShape)
                .then(
                    if (focused) Modifier.border(3.dp, Color.White, PosterShape)
                    else Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), PosterShape),
                )
                .background(Color.White.copy(alpha = 0.08f))
                .padding(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        job.headline(),
                        style = CoogType.cardTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        job.subtitle(),
                        style = CoogType.cardYear,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .background(
                                if (job.status == "error") CoogDanger else Color.White,
                                RoundedCornerShape(99.dp),
                            ),
                    )
                }
            }
        }
    }
}

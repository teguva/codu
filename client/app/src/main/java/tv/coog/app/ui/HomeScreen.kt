package tv.coog.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.coog.app.data.JobItem
import tv.coog.app.data.MediaItem
import tv.coog.app.ui.theme.CoogBgDeep
import tv.coog.app.ui.theme.CoogBgSoft
import tv.coog.app.ui.theme.CoogType

@Composable
fun HomeScreen(
    tab: BrowseTab,
    loading: Boolean,
    error: String?,
    items: List<MediaItem>,
    jobs: List<JobItem>,
    continueWatching: List<MediaItem>,
    forYou: List<MediaItem> = emptyList(),
    tasteColdStart: Boolean = false,
    trendingMovies: List<MediaItem>,
    trendingSeries: List<MediaItem>,
    onOpenMovie: (MediaItem) -> Unit,
    onOpenFolder: (FolderRow) -> Unit,
    onPlayContinue: (MediaItem) -> Unit = {},
    onClearContinue: (MediaItem) -> Unit = {},
) {
    val folders = remember(items) { items.folderRows() }
    val firstFocus = LocalBrowseContentFocus.current ?: remember { FocusRequester() }

    val hasContent = when (tab) {
        BrowseTab.Folders -> folders.isNotEmpty()
        else -> continueWatching.isNotEmpty() || forYou.isNotEmpty() ||
            trendingMovies.isNotEmpty() || trendingSeries.isNotEmpty()
    }

    val inset = catalogInset()
    val pad = Modifier.padding(start = inset, top = topBarHeight() + 6.dp, end = inset)
    when {
        error != null && !hasContent -> {
            Column(
                modifier = Modifier.fillMaxSize().background(CoogBgDeep).then(pad),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(error, color = MaterialTheme.colorScheme.error)
                Text(
                    "Open Settings and set the server to this PC's LAN IP on port 8090.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        loading -> {
            Text(
                "One moment.",
                style = CoogType.heroTagline,
                modifier = Modifier.fillMaxSize().background(CoogBgDeep).then(pad),
            )
        }
        !hasContent -> {
            Column(
                modifier = Modifier.fillMaxSize().background(CoogBgDeep).then(pad),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Nothing to show yet", style = CoogType.heroTitle)
                Text(
                    if (tasteColdStart) {
                        "Watch a few titles so Match can learn your taste. Trending still works meanwhile."
                    } else {
                        "Trending titles appear here once the server can reach TMDB."
                    },
                    style = CoogType.heroPlot,
                )
            }
        }
        tab == BrowseTab.Folders -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CoogBgDeep)
                    .padding(top = topBarHeight())
                    .focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item(key = "folders") {
                    FolderCatalogRow(
                        label = "Library",
                        folders = folders,
                        onOpen = onOpenFolder,
                        firstFocus = firstFocus,
                        insetStart = inset,
                        featured = true,
                        exitUp = true,
                    )
                }
            }
        }
        else -> {
            HomeRows(
                continueWatching = continueWatching,
                forYou = forYou,
                movies = trendingMovies,
                series = trendingSeries,
                jobs = jobs,
                library = items,
                firstFocus = firstFocus,
                inset = inset,
                onOpen = onOpenMovie,
                onPlayContinue = onPlayContinue,
                onClearContinue = onClearContinue,
            )
        }
    }
}

@Composable
private fun HomeRows(
    continueWatching: List<MediaItem>,
    forYou: List<MediaItem>,
    movies: List<MediaItem>,
    series: List<MediaItem>,
    jobs: List<JobItem>,
    library: List<MediaItem>,
    firstFocus: FocusRequester,
    inset: Dp,
    onOpen: (MediaItem) -> Unit,
    onPlayContinue: (MediaItem) -> Unit,
    onClearContinue: (MediaItem) -> Unit,
) {
    val shelves = remember(continueWatching, forYou, movies, series) {
        buildList {
            if (continueWatching.isNotEmpty()) {
                add(HomeShelf("continue", "Continue watching", continueWatching))
            }
            if (forYou.isNotEmpty()) {
                add(HomeShelf("foryou", "For you", forYou))
            }
            if (movies.isNotEmpty()) {
                add(HomeShelf("movies", "Recommended movies", movies))
            }
            if (series.isNotEmpty()) {
                add(HomeShelf("series", "Recommended series", series))
            }
        }
    }
    val pinFocus = remember(shelves.map { it.id }) {
        List(shelves.size) { FocusRequester() }
    }
    var focusedRow by remember { mutableIntStateOf(0) }
    var menuItem by remember { mutableStateOf<MediaItem?>(null) }
    val motion = tween<Dp>(220, easing = FastOutSlowInEasing)
    val topInset by animateDpAsState(
        if (focusedRow == 0) topBarOverlayHeight() else topBarHeight(),
        motion,
        label = "home-top",
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CoogBgDeep)
            .padding(top = topInset)
            .clipToBounds()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown) {
                    coogDebug(
                        "A",
                        "HomeScreen.kt:HomeRows",
                        "shelf up",
                        mapOf(
                            "focusedRow" to focusedRow,
                            "rows" to shelves.size,
                            "continue" to continueWatching.size,
                        ),
                    )
                }
                false
            },
    ) {
        val viewport = maxHeight
        val gap = 8.dp
        val prevPeek = (viewport * 0.14f).coerceIn(48.dp, 72.dp)
        val nextH = (viewport * 0.36f).coerceIn(160.dp, 240.dp)
        val activeH = (viewport - prevPeek - gap - nextH - gap).coerceAtLeast(240.dp)
        val heights = shelves.mapIndexed { i, _ ->
            if (i == focusedRow) activeH else nextH
        }
        val yBefore = heights.take(focusedRow).fold(0.dp) { acc, h -> acc + h + gap }
        val targetOffset = if (focusedRow == 0) 0.dp else -(yBefore - prevPeek)
        val offsetY by animateDpAsState(targetOffset, motion, label = "home-offset")
        val windowH = activeH + gap + nextH
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (focusedRow == 0) windowH else viewport)
                .clipToBounds(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .offset(y = offsetY),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
            shelves.forEachIndexed { i, shelf ->
                androidx.compose.runtime.key(shelf.id) {
                    val h by animateDpAsState(heights[i], motion, label = "home-h-$i")
                    FeaturedCarousel(
                        items = shelf.items,
                        label = shelf.label,
                        onOpen = if (shelf.id == "continue") onPlayContinue else onOpen,
                        jobs = jobs,
                        library = library,
                        expanded = i == focusedRow,
                        onRowFocused = { focusedRow = i },
                        firstFocus = if (i == 0) firstFocus else pinFocus[i],
                        exitUp = i == 0,
                        insetStart = inset,
                        upFocus = when {
                            i <= 0 -> null
                            i == 1 -> firstFocus
                            else -> pinFocus[i - 1]
                        },
                        downFocus = pinFocus.getOrNull(i + 1),
                        onCardMenu = if (shelf.id == "continue") {
                            { item -> menuItem = item }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .requiredHeight(h)
                            .clipToBounds(),
                    )
                }
            }
            }
        }
    }
    menuItem?.let { item ->
        ContinueCardMenu(
            item = item,
            onDismiss = { menuItem = null },
            onResume = {
                menuItem = null
                onPlayContinue(item)
            },
            onMoreInfo = {
                menuItem = null
                onOpen(item)
            },
            onClearProgress = {
                menuItem = null
                onClearContinue(item)
            },
        )
    }
}

@Composable
private fun ContinueCardMenu(
    item: MediaItem,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onMoreInfo: () -> Unit,
    onClearProgress: () -> Unit,
) {
    val catchFocus = remember { FocusRequester() }
    val resumeFocus = remember { FocusRequester() }
    var armed by remember { mutableStateOf(false) }
    var sawSelectDown by remember { mutableStateOf(false) }
    val detail = when {
        item.season > 0 || item.episode > 0 -> item.episodeHeadline()
        else -> item.heroMetaLine()
    }
    LaunchedEffect(item.id) {
        runCatching { catchFocus.requestFocus() }
        delay(300)
        if (!sawSelectDown && !armed) {
            armed = true
        }
    }
    LaunchedEffect(armed) {
        if (!armed) return@LaunchedEffect
        delay(16)
        runCatching { resumeFocus.requestFocus() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .focusRequester(catchFocus)
                .focusProperties { canFocus = !armed }
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (armed) return@onPreviewKeyEvent false
                    if (!event.key.isSelectKey()) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) {
                        sawSelectDown = true
                    } else if (event.type == KeyEventType.KeyUp) {
                        armed = true
                    }
                    true
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .background(CoogBgSoft, RoundedCornerShape(18.dp))
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(item.headline(), style = CoogType.heroTagline)
                if (detail.isNotBlank() && !detail.equals(item.headline(), ignoreCase = true)) {
                    Text(detail, style = CoogType.cardYear)
                }
                WhitePill(
                    label = "Resume",
                    onClick = { if (armed) onResume() },
                    modifier = Modifier
                        .focusRequester(resumeFocus)
                        .focusProperties { canFocus = armed }
                        .fillMaxWidth(),
                )
                GhostButton(
                    label = "More info",
                    onClick = { if (armed) onMoreInfo() },
                    modifier = Modifier
                        .focusProperties { canFocus = armed }
                        .fillMaxWidth(),
                )
                GhostButton(
                    label = "Clear progress",
                    onClick = { if (armed) onClearProgress() },
                    modifier = Modifier
                        .focusProperties { canFocus = armed }
                        .fillMaxWidth(),
                )
            }
        }
    }
}

private fun Key.isSelectKey(): Boolean =
    this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter

private data class HomeShelf(
    val id: String,
    val label: String,
    val items: List<MediaItem>,
)

package tv.coog.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.ContentFrame
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import tv.coog.app.data.CoogApi
import tv.coog.app.data.MediaItem
import tv.coog.app.ui.theme.CoogType
import androidx.media3.common.MediaItem as ExoMediaItem

private val CardShape = RoundedCornerShape(16.dp)
private val FocusPad = 8.dp

internal suspend fun catalogMatch(api: CoogApi, item: MediaItem): MediaItem? {
    val query = item.headline().ifBlank { item.title }
    if (query.isBlank()) return null
    val result = runCatching { api.catalogSearch(query) }.getOrNull() ?: return null
    val pool = if (item.kind == "series" || item.kind == "episode") result.series else result.movies
    val want = normalizeBrowseTitle(query)
    val hits = pool.filter { normalizeBrowseTitle(it.headline()) == want }
    if (hits.isEmpty()) return null
    val year = item.year
    return hits.firstOrNull { year == 0 || it.year == 0 || it.year == year } ?: hits.singleOrNull()
}

private fun hasCatalogArt(item: MediaItem): Boolean {
    val poster = item.posterUrl
    val backdrop = item.backdropUrl
    fun remoteCdn(url: String) =
        url.startsWith("http") && !url.contains("/api/v1/media/")
    return remoteCdn(poster) || remoteCdn(backdrop)
}

private suspend fun resolveCatalogArt(api: CoogApi, item: MediaItem): MediaItem? = when {
    item.imdbId.isNotBlank() -> runCatching {
        api.catalogTitle(item.imdbId, item.kind.ifBlank { "movie" })
    }.getOrNull()
    item.tmdbId != 0 -> runCatching {
        api.catalogTmdb(item.kind.ifBlank { "movie" }, item.tmdbId)
    }.getOrNull()
    else -> catalogMatch(api, item)
}

@Composable
fun FeaturedCarousel(
    items: List<MediaItem>,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    jobs: List<tv.coog.app.data.JobItem> = emptyList(),
    library: List<MediaItem> = emptyList(),
    expanded: Boolean = true,
    /** When set, non-focused cards keep this height even in an expanded (tall) row. */
    peekCardHeight: Dp = Dp.Unspecified,
    onRowFocused: () -> Unit = {},
    firstFocus: FocusRequester? = null,
    exitUp: Boolean = false,
    insetStart: Dp = catalogInset(),
    onCardMenu: ((MediaItem) -> Unit)? = null,
    upFocus: FocusRequester? = null,
    downFocus: FocusRequester? = null,
) {
    if (items.isEmpty()) return
    val server = LocalCoogServer.current
    var selected by remember(items.firstOrNull()?.id) { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, items.lastIndex)
    val cardFocus = firstFocus ?: remember { FocusRequester() }
    val enterRail = LocalEnterRail.current
    val listState = rememberLazyListState()
    var extras by remember { mutableStateOf<Map<String, MediaItem>>(emptyMap()) }
    val featured = extras[items[index].id] ?: items[index]

    // Built once per items change — not on every animation-frame recomposition (avoids per-frame
    // string allocation / GC churn while home shelves animate).
    val idsKey = remember(items) { items.joinToString { it.id } }
    LaunchedEffect(idsKey, server.url, server.token) {
        val api = CoogApi(server.url, server.token)
        val missing = items.filter { raw ->
            val cur = extras[raw.id] ?: raw
            !hasCatalogArt(cur)
        }
        missing.forEachIndexed { i, raw ->
            if (i > 0) delay(40L)
            val remote = resolveCatalogArt(api, raw)
            if (remote != null) {
                extras = extras + (raw.id to mergeDetails(raw, remote))
            }
        }
    }

    LaunchedEffect(featured.id, featured.imdbId, featured.tmdbId, featured.title, featured.kind, server.url, server.token) {
        if (hasCatalogArt(featured)) return@LaunchedEffect
        delay(120)
        val api = CoogApi(server.url, server.token)
        val remote = resolveCatalogArt(api, featured)
        if (remote != null) {
            extras = extras + (featured.id to mergeDetails(featured, remote))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.hasFocus) onRowFocused() },
    ) {
        if (label.isNotBlank()) {
            Text(
                label,
                style = CoogType.shelfTitle,
                modifier = Modifier.padding(start = insetStart, bottom = 4.dp),
            )
        }
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val gap = 12.dp
            val pin = insetStart
            val rowHeight = maxHeight
            val featuredHeight = (rowHeight - FocusPad * 2).coerceAtLeast(1.dp)
            // Focused row: peeks are exactly half the hero. Idle rows use the shared peek size.
            val peekHeight = when {
                expanded -> featuredHeight * 0.5f
                peekCardHeight != Dp.Unspecified -> peekCardHeight
                else -> featuredHeight
            }
            val featuredWidth = featuredHeight * 2f
            val peekWidth = peekHeight * 2f / 3f
            val focusedWidth = if (expanded) featuredWidth else peekWidth
            val endPad = (maxWidth - pin - focusedWidth).coerceAtLeast(pin)
            val metaGap = 8.dp
            val metaHeight = (featuredHeight - peekHeight - metaGap).coerceAtLeast(0.dp)
            val metaWidth = (peekWidth * 3f + gap * 2f)
                .coerceAtMost((maxWidth - pin - featuredWidth - gap - pin).coerceAtLeast(0.dp))
            Box(modifier = Modifier.fillMaxSize()) {
                PivotBringIntoView(pin = pin) {
                    LazyRow(
                        state = listState,
                        userScrollEnabled = false,
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        contentPadding = PaddingValues(
                            start = pin,
                            end = endPad,
                            top = FocusPad,
                            bottom = FocusPad,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                    ) {
                        itemsIndexed(items, key = { _, item -> item.id }) { i, raw ->
                            val item = extras[raw.id] ?: raw
                            val featuredCard = expanded && i == index
                            val mark = remember(item, jobs, library) { item.cardMark(jobs, library) }
                            RowCard(
                                item = item,
                                featured = featuredCard,
                                width = if (featuredCard) featuredWidth else peekWidth,
                                height = if (featuredCard) featuredHeight else peekHeight,
                                playTrailer = featuredCard,
                                mark = mark,
                                onOpen = { onOpen(item) },
                                onLongClick = onCardMenu?.let { menu -> { menu(item) } },
                                modifier = Modifier
                                    .then(if (i == index) Modifier.focusRequester(cardFocus) else Modifier)
                                    .then(
                                        if (upFocus != null || downFocus != null) {
                                            Modifier.focusProperties {
                                                upFocus?.let { up = it }
                                                downFocus?.let { down = it }
                                            }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            onRowFocused()
                                            selected = i
                                        }
                                    }
                                    .onPreviewKeyEvent { event ->
                                        val toMenu = (exitUp && event.key == Key.DirectionUp) ||
                                            (exitUp && i == 0 && event.key == Key.DirectionLeft)
                                        // #region agent log
                                        if (event.key == Key.DirectionUp || event.key == Key.DirectionLeft) {
                                            coogDebug(
                                                if (exitUp) "A" else "D",
                                                "FeaturedCarousel.kt:preview",
                                                "card dpad",
                                                mapOf(
                                                    "key" to event.key.toString(),
                                                    "type" to event.type.toString(),
                                                    "exitUp" to exitUp,
                                                    "i" to i,
                                                    "toMenu" to toMenu,
                                                    "label" to label,
                                                    "expanded" to expanded,
                                                ),
                                            )
                                        }
                                        // #endregion
                                        if (toMenu) {
                                            if (event.type == KeyEventType.KeyDown) enterRail()
                                            return@onPreviewKeyEvent event.type == KeyEventType.KeyDown ||
                                                event.type == KeyEventType.KeyUp
                                        }
                                        val target = when (event.key) {
                                            Key.DirectionDown -> downFocus
                                            Key.DirectionUp -> upFocus
                                            else -> null
                                        }
                                        if (target == null) return@onPreviewKeyEvent false
                                        if (event.type == KeyEventType.KeyDown) {
                                            runCatching { target.requestFocus() }
                                        }
                                        event.type == KeyEventType.KeyDown || event.type == KeyEventType.KeyUp
                                    },
                            )
                        }
                    }
                }
                if (expanded && metaHeight > 0.dp && metaWidth > 0.dp) {
                    val mark = remember(featured, jobs, library) { featured.cardMark(jobs, library) }
                    FocusedMetaPanel(
                        item = featured,
                        mark = mark,
                        modifier = Modifier
                            .padding(start = pin + featuredWidth + gap, top = FocusPad)
                            .width(metaWidth)
                            .height(metaHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowCard(
    item: MediaItem,
    featured: Boolean,
    width: Dp,
    height: Dp,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    playTrailer: Boolean = false,
    mark: CardMark? = null,
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(shape = CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (featured) 1.02f else 1.08f),
        modifier = modifier
            .width(width)
            .height(height)
            .onFocusChanged { focused = it.isFocused },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CardShape)
                .then(if (focused) Modifier.border(3.dp, Color.White, CardShape) else Modifier),
        ) {
            if (featured) {
                PosterArt(
                    item = item,
                    kind = ArtKind.Backdrop,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                )
                // Only while this card holds focus — otherwise audio keeps looping under the rail /
                // dialogs / other shelves after the row stays "expanded".
                if (playTrailer && focused) {
                    FocusedTrailer(
                        item = item,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                PosterArt(
                    item = item,
                    kind = ArtKind.Poster,
                    mark = mark,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            WatchProgressBar(
                item = item,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
private fun FocusedMetaPanel(
    item: MediaItem,
    mark: CardMark?,
    modifier: Modifier = Modifier,
) {
    val genres = remember(item) { item.heroGenres() }
    val meta = remember(item) { item.heroMetaLine().ifBlank { item.cardMetaLine() } }
    val resumeAt = remember(item) { item.seasonEpisode() }
    val plot = remember(item) {
        item.plot.trim().ifBlank { item.tagline.trim() }
    }
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            item.headline(),
            style = CoogType.heroTitle.copy(
                fontSize = 22.sp,
                lineHeight = 26.sp,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (resumeAt.isNotBlank()) {
            Text(
                resumeAt,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            mark?.let { StatusMark(mark = it, size = MarkSize.Comfort) }
            item.matchPercent()?.let { MatchMark(percent = it, size = MarkSize.Comfort) }
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (genres.isNotEmpty()) {
            Text(
                genres.joinToString("  •  "),
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (plot.isNotBlank()) {
            Text(
                plot,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun FocusedTrailer(
    item: MediaItem,
    modifier: Modifier = Modifier,
    onPlaying: (Boolean) -> Unit = {},
) {
    val server = LocalCoogServer.current
    val mediaId = item.trailerMediaId()
    val url = server.trailerUrl(mediaId)
    var start by remember(mediaId) { mutableStateOf(false) }
    LaunchedEffect(mediaId, server.url, server.token) {
        start = false
        onPlaying(false)
        val api = CoogApi(server.url, server.token)
        val check = async {
            runCatching { api.trailerExists(mediaId) }.getOrDefault(false)
        }
        delay(3_000)
        start = check.await()
    }
    if (start && url.isNotBlank()) {
        TrailerPlayer(url = url, token = server.token, onReady = onPlaying, modifier = modifier)
    }
}

@Composable
private fun TrailerPlayer(
    url: String,
    token: String,
    modifier: Modifier = Modifier,
    onReady: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var ready by remember(url) { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    LaunchedEffect(ready) { onReady(ready) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(url, token) {
        val http = DefaultHttpDataSource.Factory()
        if (token.isNotBlank()) {
            http.setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
        }
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
            .apply {
                volume = 1f
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(ExoMediaItem.fromUri(url))
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) ready = true
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        ready = false
                    }
                })
                prepare()
                playWhenReady = true
            }
        player = exo
        onDispose {
            onReady(false)
            exo.playWhenReady = false
            exo.stop()
            exo.release()
            player = null
            ready = false
        }
    }
    LaunchedEffect(resumed, player) {
        val exo = player ?: return@LaunchedEffect
        exo.playWhenReady = resumed
        if (!resumed) exo.pause()
    }
    val exo = player
    AnimatedVisibility(visible = ready && resumed && exo != null, enter = fadeIn(), exit = fadeOut()) {
        if (exo != null) {
            Box(modifier.clipToBounds()) {
                ContentFrame(
                    player = exo,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

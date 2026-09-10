package tv.coog.app.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.PlayerSurface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import tv.coog.app.data.CoogApi
import tv.coog.app.data.MediaItem
import tv.coog.app.ui.theme.CoogCached
import tv.coog.app.ui.theme.CoogType
import androidx.media3.common.MediaItem as ExoMediaItem

private val CardShape = RoundedCornerShape(16.dp)

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

@Composable
fun FeaturedCarousel(
    items: List<MediaItem>,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    expanded: Boolean = true,
    onRowFocused: () -> Unit = {},
    firstFocus: FocusRequester? = null,
    exitUp: Boolean = false,
    insetStart: Dp = catalogInset(),
) {
    if (items.isEmpty()) return
    val server = LocalCoogServer.current
    val railFocus = LocalRailFocus.current
    var selected by remember(items.firstOrNull()?.id) { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, items.lastIndex)
    val playFocus = firstFocus ?: remember { FocusRequester() }
    val peekFocus = remember { List(2) { FocusRequester() } }
    val navBarFocused = LocalNavBarFocused.current
    val navBarFocusedState = rememberUpdatedState(navBarFocused)
    var extras by remember { mutableStateOf<Map<String, MediaItem>>(emptyMap()) }
    val featured = extras[items[index].id] ?: items[index]
    val window = remember(items, index) { items.subList(index, items.size) }
    val rowItems = if (expanded) window else items
    var restorePlayOnIndex by remember { mutableStateOf(false) }
    var restorePlayOnExpand by remember { mutableStateOf(false) }

    LaunchedEffect(index) {
        val restore = restorePlayOnIndex
        restorePlayOnIndex = true
        if (!restore || !expanded) return@LaunchedEffect
        delay(40)
        if (navBarFocusedState.value) return@LaunchedEffect
        runCatching { playFocus.requestFocus() }
    }
    LaunchedEffect(expanded) {
        val restore = restorePlayOnExpand
        restorePlayOnExpand = true
        if (!restore || !expanded) return@LaunchedEffect
        delay(40)
        if (navBarFocusedState.value) return@LaunchedEffect
        runCatching { playFocus.requestFocus() }
    }
    LaunchedEffect(featured.id, featured.imdbId, featured.tmdbId, featured.title, featured.kind, server.url, server.token) {
        delay(220)
        val api = CoogApi(server.url, server.token)
        val remote = when {
            featured.imdbId.isNotBlank() -> runCatching {
                api.catalogTitle(featured.imdbId, featured.kind.ifBlank { "movie" })
            }.getOrNull()
            featured.tmdbId != 0 -> runCatching {
                api.catalogTmdb(featured.kind.ifBlank { "movie" }, featured.tmdbId)
            }.getOrNull()
            else -> catalogMatch(api, featured)
        }
        if (remote != null) {
            extras = extras + (featured.id to mergeDetails(featured, remote))
        }
    }

    fun moveLeft(): Boolean {
        if (index <= 0) return false
        selected = index - 1
        return true
    }

    fun moveRight(): Boolean {
        if (index >= items.lastIndex) return false
        selected = index + 1
        return true
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
            val innerWidth = maxWidth - insetStart - insetStart
            val heightFromWidth = (innerWidth - gap * 2) * 9f / 28f
            val rowHeight = if (expanded) minOf(maxHeight, heightFromWidth) else maxHeight
            val featuredWidth = rowHeight * 16f / 9f
            val peekWidth = rowHeight * 2f / 3f
            LazyRow(
                userScrollEnabled = !expanded,
                horizontalArrangement = Arrangement.spacedBy(gap),
                contentPadding = PaddingValues(start = insetStart, end = insetStart),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .clipToBounds(),
            ) {
                itemsIndexed(rowItems, key = { _, item -> item.id }) { offset, raw ->
                    val item = extras[raw.id] ?: raw
                    val isBillboard = expanded && offset == 0
                    if (isBillboard) {
                        BillboardCard(
                            item = item,
                            onOpen = { onOpen(item) },
                            playFocus = playFocus,
                            upFocus = if (exitUp) railFocus else null,
                            leftToRail = index == 0,
                            railFocus = railFocus,
                            onMoveLeft = ::moveLeft,
                            onMoveRight = ::moveRight,
                            width = featuredWidth,
                            playTrailer = true,
                        )
                    } else {
                        val collapsedIndex = if (expanded) index + offset else offset
                        val usePlayFocus = !expanded && offset == index
                        PeekCard(
                            item = item,
                            width = peekWidth,
                            compact = !expanded,
                            modifier =                                             Modifier
                                                .then(
                                                    when {
                                                        usePlayFocus -> Modifier.focusRequester(playFocus)
                                                        expanded && offset - 1 in peekFocus.indices -> {
                                                            Modifier.focusRequester(peekFocus[offset - 1])
                                                        }
                                                        else -> Modifier
                                                    },
                                                )
                                                .focusProperties { canFocus = !expanded }
                                                .onFocusChanged {
                                    if (it.isFocused) {
                                        onRowFocused()
                                        selected = collapsedIndex
                                    }
                                },
                            onClick = {
                                selected = collapsedIndex
                                onRowFocused()
                                if (!expanded) onOpen(item)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BillboardCard(
    item: MediaItem,
    onOpen: () -> Unit,
    playFocus: FocusRequester,
    upFocus: FocusRequester?,
    leftToRail: Boolean,
    railFocus: FocusRequester?,
    onMoveLeft: () -> Boolean,
    onMoveRight: () -> Boolean,
    width: Dp,
    playTrailer: Boolean,
) {
    val genres = item.heroGenres()
    val meta = item.heroMetaLine()
    val plot = item.heroDescription()
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(CardShape),
    ) {
        PosterArt(
            item = item,
            kind = ArtKind.Backdrop,
            badge = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        )
        if (playTrailer) {
            MutedTrailer(item = item, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.00f to Color.Black.copy(alpha = 0.88f),
                        0.38f to Color.Black.copy(alpha = 0.58f),
                        0.68f to Color.Transparent,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1.00f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.72f)
                .padding(start = 22.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    item.kindLabel().uppercase(),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp,
                )
                Text(
                    item.headline(),
                    style = CoogType.heroTitle.copy(
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        shadow = Shadow(Color.Black.copy(alpha = 0.9f), Offset(0f, 2f), 10f),
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.rating > 0) {
                        Text(
                            "${(item.rating * 10).toInt()}% Match",
                            color = CoogCached,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (meta.isNotBlank()) {
                        Text(meta, style = CoogType.heroPlot, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (genres.isNotEmpty()) {
                    Text(
                        genres.joinToString("  •  "),
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                    )
                }
                if (plot.isNotBlank()) {
                    Text(
                        plot,
                        style = CoogType.heroPlot.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            WhitePill(
                label = "Play",
                icon = Icons.Filled.PlayArrow,
                onClick = onOpen,
                modifier = Modifier
                    .focusRequester(playFocus)
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (leftToRail) return@onPreviewKeyEvent false
                                val handled = onMoveLeft()
                                if (handled) event.type == KeyEventType.KeyDown || event.type == KeyEventType.KeyUp else false
                            }
                            Key.DirectionRight -> {
                                val handled = onMoveRight()
                                if (handled) event.type == KeyEventType.KeyDown || event.type == KeyEventType.KeyUp else false
                            }
                            else -> false
                        }
                    }
                    .focusProperties {
                        if (leftToRail && railFocus != null) left = railFocus
                        if (upFocus != null) up = upFocus
                    },
            )
        }
    }
}

@Composable
private fun PeekCard(
    item: MediaItem,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val genres = item.heroGenres()
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .onFocusChanged { focused = it.isFocused },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CardShape)
                .then(
                    if (focused) Modifier.border(3.dp, Color.White, CardShape)
                    else Modifier,
                ),
        ) {
            PosterArt(
                item = item,
                kind = ArtKind.Poster,
                badge = null,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1.00f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(if (compact) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!compact) {
                    Text(
                        item.kindLabel().uppercase(),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.1.sp,
                    )
                }
                Text(
                    item.headline(),
                    color = Color.White,
                    fontSize = if (compact) 12.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact && item.rating > 0) {
                    Text(
                        "${(item.rating * 10).toInt()}% Match",
                        color = CoogCached,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (!compact && genres.isNotEmpty()) {
                    Text(
                        genres.joinToString("  •  "),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MutedTrailer(
    item: MediaItem,
    modifier: Modifier = Modifier,
) {
    val server = LocalCoogServer.current
    val mediaId = item.trailerMediaId()
    val url = server.trailerUrl(mediaId)
    var start by remember(mediaId) { mutableStateOf(false) }
    LaunchedEffect(mediaId, server.url, server.token) {
        start = false
        val api = CoogApi(server.url, server.token)
        val check = async {
            runCatching { api.trailerExists(mediaId) }.getOrDefault(false)
        }
        delay(3_000)
        start = check.await()
    }
    if (start && url.isNotBlank()) {
        TrailerPlayer(url = url, token = server.token, modifier = modifier)
    }
}

@Composable
private fun TrailerPlayer(
    url: String,
    token: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var ready by remember(url) { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    DisposableEffect(url, token) {
        val http = DefaultHttpDataSource.Factory()
        if (token.isNotBlank()) {
            http.setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
        }
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
            .apply {
                volume = 0f
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
            exo.release()
            player = null
            ready = false
        }
    }
    val exo = player
    AnimatedVisibility(visible = ready && exo != null, enter = fadeIn(), exit = fadeOut()) {
        if (exo != null) {
            PlayerSurface(player = exo, modifier = modifier)
        }
    }
}

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.delay
import tv.coog.app.data.CoogApi
import tv.coog.app.data.MediaItem
import tv.coog.app.ui.theme.CoogCached
import tv.coog.app.ui.theme.CoogType
import androidx.media3.common.MediaItem as ExoMediaItem

private val CardShape = RoundedCornerShape(16.dp)

@Composable
fun FeaturedCarousel(
    items: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    onMoreInfo: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
    insetStart: Dp = catalogInset(),
) {
    if (items.isEmpty()) return
    val server = LocalCoogServer.current
    val railFocus = LocalRailFocus.current
    val listState = rememberLazyListState()
    var selected by remember(items.firstOrNull()?.id) { mutableIntStateOf(0) }
    val itemFocus = remember(items.size, firstFocus) {
        List(items.size) { i ->
            if (i == 0 && firstFocus != null) firstFocus else FocusRequester()
        }
    }
    val moreFocus = remember { FocusRequester() }
    var extras by remember { mutableStateOf<Map<String, MediaItem>>(emptyMap()) }
    val index = selected.coerceIn(0, items.lastIndex)
    val featured = extras[items[index].id] ?: items[index]

    LaunchedEffect(index) {
        listState.animateScrollToItem(index)
        delay(40)
        runCatching { itemFocus[index].requestFocus() }
    }
    LaunchedEffect(featured.id, featured.imdbId, featured.tmdbId, server.url, server.token) {
        if (!featured.hasOfficialMeta()) return@LaunchedEffect
        delay(220)
        val api = CoogApi(server.url, server.token)
        val remote = when {
            featured.imdbId.isNotBlank() -> runCatching {
                api.catalogTitle(featured.imdbId, featured.kind.ifBlank { "movie" })
            }.getOrNull()
            featured.tmdbId != 0 -> runCatching {
                api.catalogTmdb(featured.kind.ifBlank { "movie" }, featured.tmdbId)
            }.getOrNull()
            else -> null
        }
        if (remote != null) {
            extras = extras + (featured.id to mergeDetails(featured, remote))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val gap = 12.dp
            val innerWidth = maxWidth - insetStart - insetStart
            val heightFromWidth = (innerWidth - gap * 2) * 9f / 28f
            val rowHeight = minOf(maxHeight, heightFromWidth)
            val featuredWidth = rowHeight * 16f / 9f
            val peekWidth = rowHeight * 2f / 3f
            val used = featuredWidth + peekWidth * 2 + gap * 2
            val rowWidth = insetStart + used
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
            LazyRow(
                state = listState,
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(gap),
                contentPadding = PaddingValues(start = insetStart),
                modifier = Modifier
                    .width(rowWidth)
                    .height(rowHeight)
                    .clipToBounds(),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { i, raw ->
                    val item = extras[raw.id] ?: raw
                    val active = i == index
                    if (active) {
                        BillboardCard(
                            item = item,
                            onPlay = { onPlay(item) },
                            onMoreInfo = { onMoreInfo(item) },
                            playFocus = itemFocus[i],
                            moreFocus = moreFocus,
                            leftFocus = when {
                                i > 0 -> itemFocus[i - 1]
                                railFocus != null -> railFocus
                                else -> null
                            },
                            moreRight = itemFocus.getOrNull(i + 1),
                            upFocus = railFocus,
                            width = featuredWidth,
                            page = index,
                            pageCount = items.size.coerceAtMost(6),
                        )
                    } else {
                        PeekCard(
                            item = item,
                            width = peekWidth,
                            modifier = Modifier
                                .focusRequester(itemFocus[i])
                                .onFocusChanged { if (it.isFocused) selected = i }
                                .then(
                                    if (railFocus != null && i == index + 1) {
                                        Modifier.focusProperties { up = railFocus }
                                    } else {
                                        Modifier
                                    },
                                ),
                            onClick = { selected = i },
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun BillboardCard(
    item: MediaItem,
    onPlay: () -> Unit,
    onMoreInfo: () -> Unit,
    playFocus: FocusRequester,
    moreFocus: FocusRequester,
    leftFocus: FocusRequester?,
    moreRight: FocusRequester?,
    upFocus: FocusRequester?,
    width: Dp,
    page: Int,
    pageCount: Int,
) {
    val server = LocalCoogServer.current
    val genres = item.heroGenres()
    val meta = item.heroMetaLine()
    val plot = item.heroDescription()
    val trailerUrl = if (item.isLocal()) server.trailerUrl(item.playableId()) else ""
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
        if (trailerUrl.isNotBlank()) {
            MutedTrailer(
                url = trailerUrl,
                token = server.token,
                modifier = Modifier.fillMaxSize(),
            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WhitePill(
                    label = "Play",
                    icon = Icons.Filled.PlayArrow,
                    onClick = onPlay,
                    modifier = Modifier
                        .focusRequester(playFocus)
                        .then(
                            if (leftFocus != null || upFocus != null) {
                                Modifier.focusProperties {
                                    if (leftFocus != null) left = leftFocus
                                    right = moreFocus
                                    if (upFocus != null) up = upFocus
                                }
                            } else {
                                Modifier.focusProperties { right = moreFocus }
                            },
                        ),
                )
                GhostButton(
                    label = "More Info",
                    onClick = onMoreInfo,
                    modifier = Modifier
                        .focusRequester(moreFocus)
                        .then(
                            Modifier.focusProperties {
                                left = playFocus
                                if (moreRight != null) right = moreRight
                                if (upFocus != null) up = upFocus
                            },
                        ),
                )
            }
        }
        if (pageCount > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(pageCount) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == page) 7.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (i == page) Color.White else Color.White.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PeekCard(
    item: MediaItem,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    item.kindLabel().uppercase(),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp,
                )
                Text(
                    item.headline(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.rating > 0) {
                    Text(
                        "${(item.rating * 10).toInt()}% Match",
                        color = CoogCached,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (genres.isNotEmpty()) {
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
    url: String,
    token: String,
    modifier: Modifier = Modifier,
) {
    var start by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        start = false
        delay(1100)
        start = true
    }
    if (start) {
        TrailerPlayer(url = url, token = token, modifier = modifier)
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

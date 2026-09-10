package tv.coog.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.PlayerSurface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.coog.app.data.MediaItem
import tv.coog.app.ui.theme.CoogBgDeep
import tv.coog.app.ui.theme.CoogType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val trailerClient = OkHttpClient.Builder()
    .followRedirects(true)
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(4, TimeUnit.SECONDS)
    .build()

private val failedTrailers = ConcurrentHashMap.newKeySet<String>()

@Composable
fun HeroBanner(
    item: MediaItem?,
    rowLabel: String = "Movies",
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().fillMaxSize().background(CoogBgDeep)) {
        if (item != null) {
            HeroSplash(item = item)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.00f to CoogBgDeep.copy(alpha = 0.92f),
                            0.18f to CoogBgDeep.copy(alpha = 0.72f),
                            0.38f to CoogBgDeep.copy(alpha = 0.28f),
                            0.58f to CoogBgDeep.copy(alpha = 0.08f),
                            1.00f to Color.Transparent,
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to CoogBgDeep.copy(alpha = 0.28f),
                            0.14f to Color.Transparent,
                            0.58f to Color.Transparent,
                            0.82f to CoogBgDeep.copy(alpha = 0.55f),
                            1.00f to CoogBgDeep,
                        ),
                    ),
            )
        }
        HeroClock(modifier = Modifier.fillMaxWidth().padding(start = RailWidth + 18.dp, end = 28.dp, top = 18.dp))
        if (item != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(0.50f)
                    .padding(start = RailWidth + 18.dp, end = 16.dp, top = 56.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    item.headline(),
                    style = CoogType.heroTitle.copy(
                        shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset.Zero, 14f),
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val tagline = item.heroSubtitle()
                if (tagline.isNotBlank()) {
                    Text(
                        tagline,
                        style = CoogType.heroTagline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val plot = item.heroDescription()
                if (plot.isNotBlank()) {
                    Text(
                        plot,
                        style = CoogType.heroPlot,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.heroChips(rowLabel).take(5).forEachIndexed { index, chip ->
                        val badge = index == 0
                        Text(
                            chip,
                            style = CoogType.chip,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (badge) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.28f),
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroClock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    val day = remember(now) { SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now) }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val clockStyle = CoogType.clock.copy(
        shadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset.Zero, 12f),
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(day, style = clockStyle)
        Text(time, style = clockStyle.copy(color = Color.White.copy(alpha = 0.86f)))
    }
}

@Composable
private fun HeroSplash(item: MediaItem) {
    val server = LocalCoogServer.current
    var playTrailer by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id, server.url) {
        playTrailer = false
        if (server.url.isBlank() || failedTrailers.contains(item.id)) return@LaunchedEffect
        delay(5_000)
        val exists = trailerExists(server.trailerUrl(item.id), server.token)
        if (!exists) {
            failedTrailers.add(item.id)
            return@LaunchedEffect
        }
        playTrailer = true
    }
    PosterArt(
        item = item,
        kind = ArtKind.Backdrop,
        badge = null,
        contentScale = ContentScale.Crop,
        alignment = Alignment.CenterEnd,
        modifier = Modifier.fillMaxSize(),
    )
    AnimatedVisibility(
        visible = playTrailer,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        MutedTrailer(
            url = server.trailerUrl(item.id),
            token = server.token,
            onError = { failedTrailers.add(item.id) },
        )
    }
}

@Composable
private fun MutedTrailer(url: String, token: String, onError: () -> Unit) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    DisposableEffect(url, token) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                onError()
            }
        }
        player.addListener(listener)
        val http = DefaultHttpDataSource.Factory()
        if (token.isNotBlank()) {
            http.setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
        }
        val source = DefaultMediaSourceFactory(http).createMediaSource(ExoMediaItem.fromUri(url))
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            player.stop()
            player.clearMediaItems()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    PlayerSurface(
        player = player,
        modifier = Modifier.fillMaxSize(),
    )
}

private suspend fun trailerExists(url: String, token: String): Boolean = withContext(Dispatchers.IO) {
    val req = Request.Builder().url(url).head()
    if (token.isNotBlank()) req.header("Authorization", "Bearer $token")
    runCatching {
        trailerClient.newCall(req.build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}
